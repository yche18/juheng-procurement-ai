package io.github.yche18.juhengbackend.procurement;

import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import io.github.yche18.juhengbackend.procurement.application.SubmitProcurementRequestCommand;
import io.github.yche18.juhengbackend.procurement.application.SubmitProcurementRequestResult;
import io.github.yche18.juhengbackend.procurement.application.SubmitProcurementRequestService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 PostgreSQL、Spring Security 和 HTTP 边界验证采购申请提交用例。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SubmitProcurementRequestIntegrationTests
{

    private static final String REQUESTER = "demo-requester";
    private static final String OTHER_REQUESTER = "demo-requester-approver";
    private static final String DEMO_PASSWORD = "juheng-local";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_submit_request_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SubmitProcurementRequestService submitService;

    /**
     * 清除上一个测试的提交数据，并按外键依赖顺序保留数据库结构。
     */
    @BeforeEach
    void clearBusinessData()
    {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM approval_task");
        jdbcTemplate.update("DELETE FROM procurement_item");
        jdbcTemplate.update("DELETE FROM procurement_request");
    }

    /**
     * 验证合法提交原子更新申请、创建唯一待办、两条审计和完成幂等记录。
     */
    @Test
    void submitsCompleteOwnedDraftAtomically() throws Exception
    {
        UUID requestId = insertCompleteRequest(REQUESTER, "DRAFT", 0L);

        mockMvc.perform(post("/api/procurement-requests/{requestId}/submit", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .header("Idempotency-Key", "submit-success-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.requestStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.requestVersion").value(1))
                .andExpect(jsonPath("$.approvalTaskId").isNotEmpty())
                .andExpect(jsonPath("$.approvalTaskStatus").value("PENDING"));

        assertThat(singleText(
                "SELECT status FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo("SUBMITTED");
        assertThat(singleLong(
                "SELECT version FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo(1L);
        assertThat(rowCount("approval_task")).isEqualTo(1);
        assertThat(singleText(
                "SELECT assignee_id FROM approval_task WHERE procurement_request_id = ?", requestId))
                .isEqualTo("demo-approver");
        assertThat(singleText(
                "SELECT status FROM approval_task WHERE procurement_request_id = ?", requestId))
                .isEqualTo("PENDING");
        assertThat(rowCount("audit_event")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT action FROM audit_event WHERE procurement_request_id = ? ORDER BY action",
                String.class,
                requestId)).containsExactly(
                        "APPROVAL_TASK_ASSIGNED",
                        "PROCUREMENT_REQUEST_SUBMITTED");
        assertThat(singleText(
                "SELECT status FROM idempotency_record WHERE target_id = ?", requestId))
                .isEqualTo("COMPLETED");
    }

    /**
     * 验证相同调用者、键和版本的顺序重试返回同一任务且没有重复副作用。
     */
    @Test
    void replaysCompletedSubmissionWithoutDuplicateSideEffects() throws Exception
    {
        UUID requestId = insertCompleteRequest(REQUESTER, "DRAFT", 0L);

        submitOverHttp(requestId, "same-key", 0L)
                .andExpect(status().isOk());
        UUID firstTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM approval_task WHERE procurement_request_id = ?",
                UUID.class,
                requestId);

        submitOverHttp(requestId, "same-key", 0L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestVersion").value(1))
                .andExpect(jsonPath("$.approvalTaskId").value(firstTaskId.toString()));

        assertThat(rowCount("approval_task")).isEqualTo(1);
        assertThat(rowCount("audit_event")).isEqualTo(2);
        assertThat(rowCount("idempotency_record")).isEqualTo(1);
    }

    /**
     * 验证同一幂等键绑定不同版本时返回专用冲突且不执行新副作用。
     */
    @Test
    void rejectsSameIdempotencyKeyWithDifferentPayload() throws Exception
    {
        UUID requestId = insertCompleteRequest(REQUESTER, "DRAFT", 0L);
        submitOverHttp(requestId, "payload-conflict", 0L)
                .andExpect(status().isOk());

        submitOverHttp(requestId, "payload-conflict", 1L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        assertThat(rowCount("approval_task")).isEqualTo(1);
        assertThat(rowCount("audit_event")).isEqualTo(2);
        assertThat(rowCount("idempotency_record")).isEqualTo(1);
    }

    /**
     * 验证非创建者无法区分他人申请与不存在申请，且失败幂等占位被回滚。
     */
    @Test
    void rejectsNonOwnerWithoutSideEffects() throws Exception
    {
        UUID requestId = insertCompleteRequest(OTHER_REQUESTER, "DRAFT", 0L);

        submitOverHttp(requestId, "non-owner", 0L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(singleText(
                "SELECT status FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo("DRAFT");
        assertThat(rowCount("approval_task")).isZero();
        assertThat(rowCount("audit_event")).isZero();
        assertThat(rowCount("idempotency_record")).isZero();
    }

    /**
     * 验证非草稿状态和过期版本分别返回状态冲突与并发冲突。
     */
    @Test
    void rejectsNonDraftAndStaleVersionWithoutSideEffects() throws Exception
    {
        UUID submittedRequest = insertCompleteRequest(REQUESTER, "SUBMITTED", 1L);

        submitOverHttp(submittedRequest, "non-draft", 1L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));

        clearBusinessData();
        UUID staleRequest = insertCompleteRequest(REQUESTER, "DRAFT", 2L);
        submitOverHttp(staleRequest, "stale-version", 1L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        assertThat(singleText(
                "SELECT status FROM procurement_request WHERE id = ?", staleRequest))
                .isEqualTo("DRAFT");
        assertThat(rowCount("approval_task")).isZero();
        assertThat(rowCount("audit_event")).isZero();
        assertThat(rowCount("idempotency_record")).isZero();
    }

    /**
     * 验证持久化草稿缺少采购项时提交失败，而不是生成不完整审批任务。
     */
    @Test
    void rejectsIncompletePersistedDraftWithoutSideEffects() throws Exception
    {
        UUID requestId = insertCompleteRequest(REQUESTER, "DRAFT", 0L);
        jdbcTemplate.update(
                "DELETE FROM procurement_item WHERE procurement_request_id = ?",
                requestId);

        submitOverHttp(requestId, "incomplete-draft", 0L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(singleText(
                "SELECT status FROM procurement_request WHERE id = ?", requestId))
                .isEqualTo("DRAFT");
        assertThat(rowCount("approval_task")).isZero();
        assertThat(rowCount("audit_event")).isZero();
        assertThat(rowCount("idempotency_record")).isZero();
    }

    /**
     * 验证缺少 REQUESTER 角色或幂等键时在业务写入前被拒绝。
     */
    @Test
    void rejectsMissingRoleAndMissingIdempotencyKey() throws Exception
    {
        UUID requestId = insertCompleteRequest(REQUESTER, "DRAFT", 0L);

        mockMvc.perform(post("/api/procurement-requests/{requestId}/submit", requestId)
                        .with(httpBasic("demo-approver", DEMO_PASSWORD))
                        .header("Idempotency-Key", "wrong-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/procurement-requests/{requestId}/submit", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(rowCount("approval_task")).isZero();
        assertThat(rowCount("audit_event")).isZero();
        assertThat(rowCount("idempotency_record")).isZero();
    }

    /**
     * 验证真正并发的相同幂等请求返回同一结果并只产生一次业务副作用。
     */
    @Test
    void replaysOneResultForConcurrentIdenticalRequests() throws Exception
    {
        UUID requestId = insertCompleteRequest(REQUESTER, "DRAFT", 0L);
        CurrentUser requester = requester();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try
        {
            Future<SubmitProcurementRequestResult> first = executor.submit(
                    () -> runConcurrentSubmit(requestId, "concurrent-same", requester, start));
            Future<SubmitProcurementRequestResult> second = executor.submit(
                    () -> runConcurrentSubmit(requestId, "concurrent-same", requester, start));
            start.countDown();

            SubmitProcurementRequestResult firstResult = first.get(10, TimeUnit.SECONDS);
            SubmitProcurementRequestResult secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult).isEqualTo(secondResult);
        }
        finally
        {
            executor.shutdownNow();
        }

        assertThat(rowCount("approval_task")).isEqualTo(1);
        assertThat(rowCount("audit_event")).isEqualTo(2);
        assertThat(rowCount("idempotency_record")).isEqualTo(1);
    }

    /**
     * 验证不同幂等键并发提交同一版本时仍只有一个状态转换成功。
     */
    @Test
    void allowsAtMostOneConcurrentSubmissionWithDifferentKeys() throws Exception
    {
        UUID requestId = insertCompleteRequest(REQUESTER, "DRAFT", 0L);
        CurrentUser requester = requester();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try
        {
            Future<String> first = executor.submit(
                    () -> classifyConcurrentSubmit(requestId, "different-1", requester, start));
            Future<String> second = executor.submit(
                    () -> classifyConcurrentSubmit(requestId, "different-2", requester, start));
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
        }
        finally
        {
            executor.shutdownNow();
        }

        assertThat(rowCount("approval_task")).isEqualTo(1);
        assertThat(rowCount("audit_event")).isEqualTo(2);
        assertThat(rowCount("idempotency_record")).isEqualTo(1);
    }

    /**
     * 使用 HTTP Basic 发起标准提交请求。
     *
     * @param requestId 申请标识
     * @param idempotencyKey 幂等键
     * @param version 草稿版本
     * @return 可继续声明响应断言的结果操作器
     * @throws Exception MockMvc 调用失败
     */
    private org.springframework.test.web.servlet.ResultActions submitOverHttp(
            UUID requestId,
            String idempotencyKey,
            long version) throws Exception
    {
        return mockMvc.perform(post("/api/procurement-requests/{requestId}/submit", requestId)
                .with(httpBasic(REQUESTER, DEMO_PASSWORD))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + "}"));
    }

    /**
     * 等待统一起跑信号后执行一次相同幂等键提交。
     *
     * @param requestId 申请标识
     * @param key 幂等键
     * @param requester 可信申请人
     * @param start 并发起跑门闩
     * @return 提交结果
     * @throws InterruptedException 等待被中断
     */
    private SubmitProcurementRequestResult runConcurrentSubmit(
            UUID requestId,
            String key,
            CurrentUser requester,
            CountDownLatch start) throws InterruptedException
    {
        start.await();
        return submitService.submit(
                requestId,
                new SubmitProcurementRequestCommand(0L),
                key,
                requester);
    }

    /**
     * 执行不同幂等键的并发提交并将预期并发异常归一为测试文本。
     *
     * @param requestId 申请标识
     * @param key 本次幂等键
     * @param requester 可信申请人
     * @param start 并发起跑门闩
     * @return SUCCESS 或 CONFLICT
     * @throws InterruptedException 等待被中断
     */
    private String classifyConcurrentSubmit(
            UUID requestId,
            String key,
            CurrentUser requester,
            CountDownLatch start) throws InterruptedException
    {
        try
        {
            runConcurrentSubmit(requestId, key, requester, start);
            return "SUCCESS";
        }
        catch (ConcurrentUpdateException exception)
        {
            return "CONFLICT";
        }
    }

    /**
     * 创建测试使用的可信申请人上下文。
     *
     * @return REQUESTER 当前用户
     */
    private CurrentUser requester()
    {
        return new CurrentUser(new UserId(REQUESTER), Set.of(Role.REQUESTER));
    }

    /**
     * 插入一份包含单个采购项的测试申请。
     *
     * @param creatorId 创建者
     * @param status 当前状态
     * @param version 当前版本
     * @return 申请标识
     */
    private UUID insertCompleteRequest(String creatorId, String status, long version)
    {
        UUID requestId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-22T01:02:03Z");
        jdbcTemplate.update("""
                INSERT INTO procurement_request (
                    id, business_number, creator_id, title, purpose, department,
                    expected_delivery_date, currency, estimated_total, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId,
                "PR-20260922-" + Integer.toUnsignedString(requestId.hashCode()),
                creatorId,
                "提交测试申请",
                "验证人工审批提交链路",
                "研发部",
                Date.valueOf(LocalDate.of(2026, 10, 1)),
                "CNY",
                new BigDecimal("10.00"),
                status,
                version,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        jdbcTemplate.update("""
                INSERT INTO procurement_item (
                    id, procurement_request_id, line_no, name, category_code,
                    specification, quantity, unit, estimated_unit_price
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                requestId,
                1,
                "开发笔记本",
                "LAPTOP",
                "32GB 内存",
                new BigDecimal("1"),
                "台",
                new BigDecimal("10.00"));
        return requestId;
    }

    /**
     * 查询指定表的总行数。
     *
     * @param tableName 测试内受控表名
     * @return 行数
     */
    private int rowCount(String tableName)
    {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class);
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
}
