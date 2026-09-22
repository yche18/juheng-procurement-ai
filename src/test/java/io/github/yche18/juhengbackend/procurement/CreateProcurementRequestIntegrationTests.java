package io.github.yche18.juhengbackend.procurement;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 PostgreSQL、Spring Security 和 HTTP 边界验证创建采购草稿用例。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CreateProcurementRequestIntegrationTests
{

    private static final String DEMO_PASSWORD = "juheng-local";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_create_request_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 清除上一个测试产生的业务数据，同时保留 Flyway 结构和全局序列。
     */
    @BeforeEach
    void clearBusinessData()
    {
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM procurement_item");
        jdbcTemplate.update("DELETE FROM procurement_request");
    }

    /**
     * 验证 REQUESTER 可以创建带稳定编号、金额和成功审计的采购草稿。
     */
    @Test
    void createsDraftAndAuditFromTrustedRequester() throws Exception
    {
        mockMvc.perform(post("/api/procurement-requests")
                        .with(httpBasic("demo-requester", DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.businessNumber")
                        .value(Matchers.matchesPattern("PR-\\d{8}-\\d+")))
                .andExpect(jsonPath("$.creatorId").value("demo-requester"))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.estimatedTotal").value(40.09))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].lineNumber").value(1))
                .andExpect(jsonPath("$.items[0].estimatedLineTotal").value(20.09))
                .andExpect(jsonPath("$.items[1].lineNumber").value(2))
                .andExpect(jsonPath("$.items[1].estimatedLineTotal").value(20.00));

        assertThat(rowCount("procurement_request")).isEqualTo(1);
        assertThat(rowCount("procurement_item")).isEqualTo(2);
        assertThat(rowCount("audit_event")).isEqualTo(1);
        assertThat(singleText("SELECT creator_id FROM procurement_request"))
                .isEqualTo("demo-requester");
        assertThat(singleText("SELECT action FROM audit_event"))
                .isEqualTo("PROCUREMENT_REQUEST_CREATED");
        assertThat(singleText("SELECT result FROM audit_event"))
                .isEqualTo("SUCCESS");
        assertThat(singleText("SELECT request_identifier FROM audit_event"))
                .isNotBlank();
    }

    /**
     * 验证客户端声明的创建者、币种、状态和总额不会覆盖服务端控制值。
     */
    @Test
    void ignoresClientSuppliedControlledFields() throws Exception
    {
        String body = validRequestBody().replaceFirst(
                "\\{",
                "{\"creatorId\":\"demo-admin\","
                        + "\"currency\":\"USD\","
                        + "\"status\":\"APPROVED\","
                        + "\"estimatedTotal\":0,");

        mockMvc.perform(post("/api/procurement-requests")
                        .with(httpBasic("demo-requester", DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creatorId").value("demo-requester"))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.estimatedTotal").value(40.09));

        assertThat(singleText("SELECT creator_id FROM procurement_request"))
                .isEqualTo("demo-requester");
        assertThat(singleText("SELECT currency FROM procurement_request"))
                .isEqualTo("CNY");
        assertThat(singleText("SELECT status FROM procurement_request"))
                .isEqualTo("DRAFT");
    }

    /**
     * 验证缺失必填项、空明细和非法品类、数量、金额都会返回字段错误且不落库。
     */
    @Test
    void rejectsInvalidFieldsWithoutWritingRequestOrAudit() throws Exception
    {
        List<String> invalidBodies = List.of(
                validRequestBody().replace("\"研发电脑采购\"", "\"\""),
                validRequestBody().replaceFirst("\"items\":\\s*\\[[\\s\\S]*]", "\"items\":[]"),
                validRequestBody().replace("\"LAPTOP\"", "\"FOOD\""),
                validRequestBody().replace("\"quantity\": 1.005", "\"quantity\": 0"),
                validRequestBody().replace("\"estimatedUnitPrice\": 19.99", "\"estimatedUnitPrice\": -0.01"));

        for (String body : invalidBodies)
        {
            mockMvc.perform(post("/api/procurement-requests")
                            .with(httpBasic("demo-requester", DEMO_PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        }

        assertThat(rowCount("procurement_request")).isZero();
        assertThat(rowCount("procurement_item")).isZero();
        assertThat(rowCount("audit_event")).isZero();
    }

    /**
     * 验证缺少 REQUESTER 角色时应用层拒绝创建且没有数据库副作用。
     */
    @Test
    void deniesNonRequesterWithoutWriting() throws Exception
    {
        mockMvc.perform(post("/api/procurement-requests")
                        .with(httpBasic("demo-approver", DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(rowCount("procurement_request")).isZero();
        assertThat(rowCount("procurement_item")).isZero();
        assertThat(rowCount("audit_event")).isZero();
    }

    /**
     * 返回包含舍入边界和两个采购项的合法请求体。
     *
     * @return JSON 请求体
     */
    private String validRequestBody()
    {
        return """
                {
                  "title": "研发电脑采购",
                  "purpose": "补充开发设备",
                  "department": "研发部",
                  "expectedDeliveryDate": "2026-10-01",
                  "items": [
                    {
                      "name": "开发笔记本",
                      "categoryCode": "LAPTOP",
                      "specification": "32GB 内存",
                      "quantity": 1.005,
                      "unit": "台",
                      "estimatedUnitPrice": 19.99
                    },
                    {
                      "name": "外接显示器",
                      "categoryCode": "MONITOR",
                      "specification": "27 英寸",
                      "quantity": 2,
                      "unit": "台",
                      "estimatedUnitPrice": 10.00
                    }
                  ]
                }
                """;
    }

    /**
     * 查询指定业务表的当前行数。
     *
     * @param tableName 测试内固定的表名
     * @return 表行数
     */
    private Integer rowCount(String tableName)
    {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    /**
     * 查询只返回一个字符串值的测试 SQL。
     *
     * @param sql 测试内固定 SQL
     * @return 单一字符串结果
     */
    private String singleText(String sql)
    {
        return jdbcTemplate.queryForObject(sql, String.class);
    }
}
