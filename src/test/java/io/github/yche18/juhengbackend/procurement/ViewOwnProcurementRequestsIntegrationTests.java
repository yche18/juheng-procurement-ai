package io.github.yche18.juhengbackend.procurement;

import io.github.yche18.juhengbackend.common.application.PageResult;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import io.github.yche18.juhengbackend.procurement.application.ListOwnProcurementRequestsQuery;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestQueryRepository;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestSummary;
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

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 PostgreSQL、Spring Security 和 HTTP 边界验证查看本人采购申请用例。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ViewOwnProcurementRequestsIntegrationTests
{

    private static final String DEMO_PASSWORD = "juheng-local";
    private static final String REQUESTER = "demo-requester";
    private static final String OTHER_REQUESTER = "demo-requester-approver";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_view_request_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProcurementRequestQueryRepository queryRepository;

    /**
     * 清除上一个测试产生的业务数据，同时保留 Flyway 结构。
     */
    @BeforeEach
    void clearBusinessData()
    {
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM procurement_item");
        jdbcTemplate.update("DELETE FROM procurement_request");
    }

    /**
     * 验证列表只返回当前用户数据，并按创建时间与 ID 稳定分页和筛选。
     */
    @Test
    void listsOnlyOwnedRequestsWithStablePaginationAndStatusFilter() throws Exception
    {
        Instant older = Instant.parse("2026-09-20T10:00:00Z");
        Instant tied = Instant.parse("2026-09-21T10:00:00Z");
        UUID oldestId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID tiedLowerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID tiedHigherId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        insertRequest(oldestId, "PR-20260920-1", REQUESTER, "较早草稿", "DRAFT", older, 0L, "10.00");
        insertRequest(tiedLowerId, "PR-20260921-2", REQUESTER, "同时间已提交", "SUBMITTED", tied, 1L, "20.00");
        insertRequest(tiedHigherId, "PR-20260921-3", REQUESTER, "同时间较大 ID", "DRAFT", tied, 2L, "30.00");
        insertRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "PR-20260922-4",
                OTHER_REQUESTER,
                "其他用户申请",
                "DRAFT",
                Instant.parse("2026-09-22T10:00:00Z"),
                0L,
                "999.00");

        mockMvc.perform(get("/api/procurement-requests")
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(tiedHigherId.toString()))
                .andExpect(jsonPath("$.content[1].id").value(tiedLowerId.toString()))
                .andExpect(jsonPath("$.content[0].creatorId").doesNotExist());

        mockMvc.perform(get("/api/procurement-requests")
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(oldestId.toString()));

        mockMvc.perform(get("/api/procurement-requests")
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(tiedHigherId.toString()))
                .andExpect(jsonPath("$.content[1].id").value(oldestId.toString()));

        mockMvc.perform(get("/api/procurement-requests")
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .param("page", "5")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    /**
     * 直接验证查询端口把所有者范围传入数据库条件，而不是在返回后过滤。
     */
    @Test
    void queryRepositoryScopesPageAndDetailsByCreator()
    {
        UUID ownRequestId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID otherRequestId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        Instant createdAt = Instant.parse("2026-09-22T10:00:00Z");
        insertRequest(ownRequestId, "PR-20260922-30", REQUESTER, "本人申请", "DRAFT", createdAt, 0L, "10.00");
        insertRequest(otherRequestId, "PR-20260922-31", OTHER_REQUESTER, "他人申请", "DRAFT", createdAt, 0L, "20.00");

        UserId requesterId = new UserId(REQUESTER);
        PageResult<ProcurementRequestSummary> page = queryRepository.findOwnedPage(
                requesterId,
                new ListOwnProcurementRequestsQuery(0, 20, null));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).extracting(ProcurementRequestSummary::id)
                .containsExactly(ownRequestId);
        assertThat(queryRepository.findOwnedById(ownRequestId, requesterId)).isPresent();
        assertThat(queryRepository.findOwnedById(otherRequestId, requesterId)).isEmpty();
    }

    /**
     * 验证申请所有者可以读取包含采购项、总额、状态和版本的完整详情。
     */
    @Test
    void returnsOwnedRequestDetailsAndItemsInLineOrder() throws Exception
    {
        UUID requestId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        insertRequest(
                requestId,
                "PR-20260922-10",
                REQUESTER,
                "研发电脑采购",
                "DRAFT",
                Instant.parse("2026-09-22T08:00:00Z"),
                7L,
                "40.09");
        insertItem(requestId, 2, "显示器", "MONITOR", "2.0000", "10.00");
        insertItem(requestId, 1, "笔记本", "LAPTOP", "1.0050", "19.99");

        mockMvc.perform(get("/api/procurement-requests/{requestId}", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.businessNumber").value("PR-20260922-10"))
                .andExpect(jsonPath("$.creatorId").value(REQUESTER))
                .andExpect(jsonPath("$.title").value("研发电脑采购"))
                .andExpect(jsonPath("$.estimatedTotal").value(40.09))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(7))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].lineNumber").value(1))
                .andExpect(jsonPath("$.items[0].estimatedLineTotal").value(20.09))
                .andExpect(jsonPath("$.items[1].lineNumber").value(2))
                .andExpect(jsonPath("$.items[1].estimatedLineTotal").value(20.00));
    }

    /**
     * 验证他人申请和真实不存在的申请使用相同错误代码，且不泄露申请内容。
     */
    @Test
    void hidesWhetherAnUnownedRequestExists() throws Exception
    {
        UUID otherRequestId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        insertRequest(
                otherRequestId,
                "PR-20260922-20",
                OTHER_REQUESTER,
                "不应泄露的申请",
                "DRAFT",
                Instant.parse("2026-09-22T09:00:00Z"),
                0L,
                "88.00");

        String unownedResponse = mockMvc.perform(get("/api/procurement-requests/{requestId}", otherRequestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource was not found"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(get("/api/procurement-requests/{requestId}", UUID.randomUUID())
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource was not found"));

        assertThat(unownedResponse)
                .doesNotContain("不应泄露的申请")
                .doesNotContain("PR-20260922-20")
                .doesNotContain(OTHER_REQUESTER);
    }

    /**
     * 验证缺少 REQUESTER 角色时列表和详情查询均被应用层拒绝。
     */
    @Test
    void deniesUsersWithoutRequesterRole() throws Exception
    {
        mockMvc.perform(get("/api/procurement-requests")
                        .with(httpBasic("demo-approver", DEMO_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/procurement-requests/{requestId}", UUID.randomUUID())
                        .with(httpBasic("demo-approver", DEMO_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 验证非法页码、页大小和状态在进入查询前被拒绝。
     */
    @Test
    void rejectsInvalidPaginationAndStatus() throws Exception
    {
        List<String[]> invalidParameters = List.of(
                new String[]{"page", "-1"},
                new String[]{"size", "0"},
                new String[]{"size", "101"},
                new String[]{"status", "UNKNOWN"});

        for (String[] parameter : invalidParameters)
        {
            mockMvc.perform(get("/api/procurement-requests")
                            .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                            .param(parameter[0], parameter[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        mockMvc.perform(get("/api/procurement-requests/not-a-uuid")
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * 插入一条测试申请主记录。
     *
     * @param id 申请标识
     * @param businessNumber 业务编号
     * @param creatorId 创建者
     * @param title 标题
     * @param status 状态
     * @param createdAt 创建时间
     * @param version 版本
     * @param estimatedTotal 预计总额文本
     */
    private void insertRequest(
            UUID id,
            String businessNumber,
            String creatorId,
            String title,
            String status,
            Instant createdAt,
            long version,
            String estimatedTotal)
    {
        jdbcTemplate.update("""
                INSERT INTO procurement_request (
                    id, business_number, creator_id, title, purpose, department,
                    expected_delivery_date, currency, estimated_total, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                businessNumber,
                creatorId,
                title,
                "测试采购目的",
                "研发部",
                Date.valueOf(LocalDate.of(2026, 10, 1)),
                "CNY",
                new BigDecimal(estimatedTotal),
                status,
                version,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    /**
     * 为测试申请插入一条采购项。
     *
     * @param requestId 所属申请
     * @param lineNumber 行号
     * @param name 名称
     * @param categoryCode 品类
     * @param quantity 数量文本
     * @param estimatedUnitPrice 单价文本
     */
    private void insertItem(
            UUID requestId,
            int lineNumber,
            String name,
            String categoryCode,
            String quantity,
            String estimatedUnitPrice)
    {
        jdbcTemplate.update("""
                INSERT INTO procurement_item (
                    id, procurement_request_id, line_no, name, category_code,
                    specification, quantity, unit, estimated_unit_price
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                requestId,
                lineNumber,
                name,
                categoryCode,
                "测试规格",
                new BigDecimal(quantity),
                "台",
                new BigDecimal(estimatedUnitPrice));
    }
}
