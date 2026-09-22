package io.github.yche18.juhengbackend.procurement;

import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import io.github.yche18.juhengbackend.procurement.application.UpdateProcurementRequestCommand;
import io.github.yche18.juhengbackend.procurement.application.UpdateProcurementRequestService;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 PostgreSQL、Spring Security 和 HTTP 边界验证修改采购草稿用例。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UpdateProcurementRequestIntegrationTests
{

    private static final String REQUESTER = "demo-requester";
    private static final String OTHER_REQUESTER = "demo-requester-approver";
    private static final String DEMO_PASSWORD = "juheng-local";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_update_request_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UpdateProcurementRequestService updateService;

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
     * 验证创建者可整体替换草稿字段和采购项，并原子保存重算总额、新版本和审计。
     */
    @Test
    void updatesOwnedDraftAndReplacesItemsAtomically() throws Exception
    {
        UUID requestId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "DRAFT", 3L, "原始标题", "10.00");
        insertItem(requestId, 1, "原始采购项", "LAPTOP", "1", "10.00");

        mockMvc.perform(put("/api/procurement-requests/{requestId}", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateBody(3L, "更新后的标题")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.businessNumber").value("PR-20260922-700"))
                .andExpect(jsonPath("$.creatorId").value(REQUESTER))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.title").value("更新后的标题"))
                .andExpect(jsonPath("$.estimatedTotal").value(40.09))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].lineNumber").value(1))
                .andExpect(jsonPath("$.items[1].lineNumber").value(2));

        assertThat(singleText("SELECT title FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo("更新后的标题");
        assertThat(singleLong("SELECT version FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo(4L);
        assertThat(singleDecimal("SELECT estimated_total FROM procurement_request WHERE id = ?", requestId))
                .isEqualByComparingTo("40.09");
        assertThat(rowCount("procurement_item")).isEqualTo(2);
        assertThat(rowCount("audit_event")).isEqualTo(1);
        assertThat(singleText("SELECT action FROM audit_event WHERE procurement_request_id = ?", requestId))
                .isEqualTo("PROCUREMENT_REQUEST_UPDATED");
        assertThat(singleText("SELECT request_identifier FROM audit_event WHERE procurement_request_id = ?", requestId))
                .isNotBlank();
    }

    /**
     * 验证他人草稿和不存在的申请使用相同 404，且均不改变业务数据或审计。
     */
    @Test
    void hidesWhetherAnUnownedDraftExists() throws Exception
    {
        UUID otherRequestId = UUID.randomUUID();
        insertRequest(otherRequestId, OTHER_REQUESTER, "DRAFT", 0L, "他人草稿", "10.00");
        insertItem(otherRequestId, 1, "他人采购项", "LAPTOP", "1", "10.00");

        mockMvc.perform(put("/api/procurement-requests/{requestId}", otherRequestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateBody(0L, "越权修改")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(put("/api/procurement-requests/{requestId}", UUID.randomUUID())
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateBody(0L, "不存在申请")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(singleText("SELECT title FROM procurement_request WHERE id = ?", otherRequestId))
                .isEqualTo("他人草稿");
        assertThat(rowCount("audit_event")).isZero();
    }

    /**
     * 验证 SUBMITTED 和两个终态均不能通过普通修改接口改变核心字段。
     */
    @Test
    void rejectsUpdatesForNonDraftStates() throws Exception
    {
        List<String> statuses = List.of("SUBMITTED", "APPROVED", "REJECTED");
        for (int index = 0; index < statuses.size(); index++)
        {
            clearBusinessData();
            String currentStatus = statuses.get(index);
            UUID requestId = UUID.randomUUID();
            insertRequest(requestId, REQUESTER, currentStatus, 2L, "不可修改标题", "10.00");
            insertItem(requestId, 1, "原始采购项", "LAPTOP", "1", "10.00");

            mockMvc.perform(put("/api/procurement-requests/{requestId}", requestId)
                            .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validUpdateBody(2L, "非法修改")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));

            assertThat(singleText("SELECT title FROM procurement_request WHERE id = ?", requestId))
                    .isEqualTo("不可修改标题");
            assertThat(singleLong("SELECT version FROM procurement_request WHERE id = ?", requestId))
                    .isEqualTo(2L);
            assertThat(rowCount("audit_event")).isZero();
        }
    }

    /**
     * 验证同一旧版本顺序提交两次时，第二次得到明确并发冲突且不会覆盖首次结果。
     */
    @Test
    void rejectsSequentialUpdateUsingStaleVersion() throws Exception
    {
        UUID requestId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "DRAFT", 0L, "原始标题", "10.00");
        insertItem(requestId, 1, "原始采购项", "LAPTOP", "1", "10.00");

        mockMvc.perform(put("/api/procurement-requests/{requestId}", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateBody(0L, "首次成功标题")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/procurement-requests/{requestId}", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateBody(0L, "不应覆盖标题")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        assertThat(singleText("SELECT title FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo("首次成功标题");
        assertThat(singleLong("SELECT version FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo(1L);
        assertThat(rowCount("audit_event")).isEqualTo(1);
    }

    /**
     * 验证两个真正并发的同版本写入最多成功一次，数据库只留下一个一致快照。
     */
    @Test
    void allowsAtMostOneConcurrentUpdateFromSameVersion() throws Exception
    {
        UUID requestId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "DRAFT", 0L, "原始标题", "10.00");
        insertItem(requestId, 1, "原始采购项", "LAPTOP", "1", "10.00");
        CurrentUser requester = new CurrentUser(new UserId(REQUESTER), Set.of(Role.REQUESTER));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try
        {
            Future<String> first = executor.submit(
                    () -> runConcurrentUpdate(requestId, "并发标题一", requester, start));
            Future<String> second = executor.submit(
                    () -> runConcurrentUpdate(requestId, "并发标题二", requester, start));
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
        }
        finally
        {
            executor.shutdownNow();
        }

        assertThat(singleLong("SELECT version FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo(1L);
        assertThat(singleText("SELECT title FROM procurement_request WHERE id = ?", requestId))
                .isIn("并发标题一", "并发标题二");
        assertThat(rowCount("procurement_item")).isEqualTo(1);
        assertThat(rowCount("audit_event")).isEqualTo(1);
    }

    /**
     * 验证字段和采购项校验失败时不写入部分明细、版本或成功审计。
     */
    @Test
    void rejectsInvalidUpdateWithoutPartialWrites() throws Exception
    {
        UUID requestId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "DRAFT", 0L, "原始标题", "10.00");
        insertItem(requestId, 1, "原始采购项", "LAPTOP", "1", "10.00");
        List<String> invalidBodies = List.of(
                validUpdateBody(0L, ""),
                validUpdateBody(0L, "合法标题").replace("\"version\": 0,", ""),
                validUpdateBody(0L, "合法标题").replaceFirst(
                        "\"items\":\\s*\\[[\\s\\S]*]", "\"items\":[]"),
                validUpdateBody(0L, "合法标题").replace("\"LAPTOP\"", "\"FOOD\""),
                validUpdateBody(0L, "合法标题").replace("\"quantity\": 1.005", "\"quantity\": 0"),
                validUpdateBody(0L, "合法标题").replace(
                        "\"estimatedUnitPrice\": 19.99", "\"estimatedUnitPrice\": -0.01"));

        for (String body : invalidBodies)
        {
            mockMvc.perform(put("/api/procurement-requests/{requestId}", requestId)
                            .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        }

        assertThat(singleText("SELECT title FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo("原始标题");
        assertThat(singleLong("SELECT version FROM procurement_request WHERE id = ?", requestId))
                .isZero();
        assertThat(rowCount("procurement_item")).isEqualTo(1);
        assertThat(rowCount("audit_event")).isZero();
    }

    /**
     * 验证缺少 REQUESTER 角色时在加载申请前拒绝修改且没有副作用。
     */
    @Test
    void deniesNonRequesterWithoutWriting() throws Exception
    {
        UUID requestId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "DRAFT", 0L, "原始标题", "10.00");
        insertItem(requestId, 1, "原始采购项", "LAPTOP", "1", "10.00");

        mockMvc.perform(put("/api/procurement-requests/{requestId}", requestId)
                        .with(httpBasic("demo-approver", DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateBody(0L, "非法修改")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(singleText("SELECT title FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo("原始标题");
        assertThat(rowCount("audit_event")).isZero();
    }

    /**
     * 等待统一起跑信号并执行一次应用服务更新，将并发异常归一为测试结果。
     *
     * @param requestId 申请标识
     * @param title 本次并发请求标题
     * @param requester 可信申请人
     * @param start 并发起跑门闩
     * @return 成功或冲突结果文本
     * @throws InterruptedException 等待被中断
     */
    private String runConcurrentUpdate(
            UUID requestId,
            String title,
            CurrentUser requester,
            CountDownLatch start) throws InterruptedException
    {
        start.await();
        try
        {
            updateService.update(requestId, updateCommand(0L, title), requester);
            return "SUCCESS";
        }
        catch (ConcurrentUpdateException exception)
        {
            return "CONFLICT";
        }
    }

    /**
     * 创建应用服务并发测试使用的合法更新命令。
     *
     * @param version 旧版本
     * @param title 新标题
     * @return 合法更新命令
     */
    private UpdateProcurementRequestCommand updateCommand(long version, String title)
    {
        return new UpdateProcurementRequestCommand(
                version,
                title,
                "更新后的采购目的",
                "采购部",
                LocalDate.of(2026, 11, 1),
                List.of(new UpdateProcurementRequestCommand.ItemCommand(
                        "并发采购项",
                        "LAPTOP",
                        "32GB 内存",
                        new BigDecimal("1"),
                        "台",
                        new BigDecimal("99.00"))));
    }

    /**
     * 返回包含版本和两个采购项的合法更新请求体。
     *
     * @param version 当前版本
     * @param title 新标题
     * @return JSON 请求体
     */
    private String validUpdateBody(long version, String title)
    {
        return """
                {
                  "version": %d,
                  "title": "%s",
                  "purpose": "更新后的采购目的",
                  "department": "采购部",
                  "expectedDeliveryDate": "2026-11-01",
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
                """.formatted(version, title);
    }

    /**
     * 插入测试申请主记录。
     *
     * @param id 申请标识
     * @param creatorId 创建者
     * @param status 状态
     * @param version 版本
     * @param title 标题
     * @param estimatedTotal 预计总额
     */
    private void insertRequest(
            UUID id,
            String creatorId,
            String status,
            long version,
            String title,
            String estimatedTotal)
    {
        Instant createdAt = Instant.parse("2026-09-22T01:02:03Z");
        jdbcTemplate.update("""
                INSERT INTO procurement_request (
                    id, business_number, creator_id, title, purpose, department,
                    expected_delivery_date, currency, estimated_total, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                "PR-20260922-700",
                creatorId,
                title,
                "原始采购目的",
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
     * @param quantity 数量
     * @param estimatedUnitPrice 预计单价
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

    /**
     * 查询固定测试表当前行数。
     *
     * @param tableName 测试内固定表名
     * @return 表行数
     */
    private Integer rowCount(String tableName)
    {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    /**
     * 查询带单个 UUID 参数的字符串结果。
     *
     * @param sql 测试 SQL
     * @param id 查询标识
     * @return 字符串结果
     */
    private String singleText(String sql, UUID id)
    {
        return jdbcTemplate.queryForObject(sql, String.class, id);
    }

    /**
     * 查询带单个 UUID 参数的长整数结果。
     *
     * @param sql 测试 SQL
     * @param id 查询标识
     * @return 长整数结果
     */
    private Long singleLong(String sql, UUID id)
    {
        return jdbcTemplate.queryForObject(sql, Long.class, id);
    }

    /**
     * 查询带单个 UUID 参数的十进制结果。
     *
     * @param sql 测试 SQL
     * @param id 查询标识
     * @return 十进制结果
     */
    private BigDecimal singleDecimal(String sql, UUID id)
    {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, id);
    }
}
