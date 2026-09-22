package io.github.yche18.juhengbackend.approval;

import io.github.yche18.juhengbackend.approval.application.DecideApprovalApplicationService;
import io.github.yche18.juhengbackend.approval.application.DecideApprovalCommand;
import io.github.yche18.juhengbackend.approval.application.DecideApprovalResult;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;
import io.github.yche18.juhengbackend.common.error.BusinessConflictException;
import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;
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
 * 使用真实 PostgreSQL、Spring Security 和 HTTP 边界验证 US-015 人工审批链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DecideApprovalIntegrationTests
{

    private static final String APPROVER = "demo-approver";
    private static final String MULTI_ROLE_USER = "demo-requester-approver";
    private static final String DEMO_PASSWORD = "juheng-local";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_decide_approval_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DecideApprovalApplicationService decisionService;

    /**
     * 按外键依赖顺序清除上一测试产生的业务记录。
     */
    @BeforeEach
    void clearBusinessData()
    {
        jdbcTemplate.update("DELETE FROM approval_decision");
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM approval_task");
        jdbcTemplate.update("DELETE FROM procurement_item");
        jdbcTemplate.update("DELETE FROM procurement_request");
    }

    /**
     * 验证合法批准会原子更新任务和申请，并保存可信决定、审计和幂等结果。
     */
    @Test
    void approvesAssignedPendingTaskAtomically() throws Exception
    {
        TestScope scope = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");

        mockMvc.perform(post("/api/approval-tasks/{taskId}/approve", scope.taskId())
                        .with(httpBasic(APPROVER, DEMO_PASSWORD))
                        .header("Idempotency-Key", "approve-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalTaskVersion\":0,\"comment\":\"  同意采购  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalTaskId").value(scope.taskId().toString()))
                .andExpect(jsonPath("$.approvalTaskStatus").value("APPROVED"))
                .andExpect(jsonPath("$.approvalTaskVersion").value(1))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.actorId").value(APPROVER))
                .andExpect(jsonPath("$.comment").value("同意采购"));

        assertTerminalState(scope, "APPROVED");
        assertThat(singleText(
                "SELECT decision FROM approval_decision WHERE approval_task_id = ?",
                scope.taskId())).isEqualTo("APPROVED");
        assertThat(singleText(
                "SELECT actor_id FROM approval_decision WHERE approval_task_id = ?",
                scope.taskId())).isEqualTo(APPROVER);
        assertThat(rowCount("approval_decision")).isEqualTo(1);
        assertThat(singleText(
                "SELECT action FROM audit_event WHERE target_id = ?",
                scope.taskId())).isEqualTo("APPROVAL_TASK_APPROVED");
        assertThat(singleText(
                "SELECT status FROM idempotency_record WHERE target_id = ?",
                scope.taskId())).isEqualTo("COMPLETED");
    }

    /**
     * 验证合法驳回保存原因，而空白原因在字段校验阶段不产生任何副作用。
     */
    @Test
    void rejectsAssignedTaskAndRequiresReason() throws Exception
    {
        TestScope invalid = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");

        rejectOverHttp(invalid.taskId(), "missing-reason", 0L, "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("comment"));
        assertPendingState(invalid);
        assertThat(rowCount("approval_decision")).isZero();
        assertThat(rowCount("audit_event")).isZero();
        assertThat(rowCount("idempotency_record")).isZero();

        clearBusinessData();
        TestScope valid = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");
        rejectOverHttp(valid.taskId(), "reject-success", 0L, "预算依据不足")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalTaskStatus").value("REJECTED"))
                .andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(jsonPath("$.comment").value("预算依据不足"));

        assertTerminalState(valid, "REJECTED");
        assertThat(singleText(
                "SELECT comment FROM approval_decision WHERE approval_task_id = ?",
                valid.taskId())).isEqualTo("预算依据不足");
    }

    /**
     * 验证非受理人看不到目标任务，申请人即使拥有审批角色也不能自审。
     */
    @Test
    void deniesNonAssigneeAndSelfApprovalWithoutSideEffects() throws Exception
    {
        TestScope otherTask = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");
        approveOverHttp(otherTask.taskId(), MULTI_ROLE_USER, "not-assignee", 0L, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertPendingState(otherTask);
        assertThat(rowCount("idempotency_record")).isZero();

        clearBusinessData();
        TestScope selfTask = insertPendingScope(MULTI_ROLE_USER, MULTI_ROLE_USER, "SUBMITTED");
        approveOverHttp(selfTask.taskId(), MULTI_ROLE_USER, "self-approval", 0L, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertPendingState(selfTask);
        assertThat(rowCount("approval_decision")).isZero();
        assertThat(rowCount("audit_event")).isZero();
        assertThat(rowCount("idempotency_record")).isZero();
    }

    /**
     * 验证缺少审批角色或幂等键时在任何业务写入前被拒绝。
     */
    @Test
    void rejectsMissingApproverRoleAndIdempotencyKey() throws Exception
    {
        TestScope scope = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");

        approveOverHttp(scope.taskId(), "demo-requester", "wrong-role", 0L, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/approval-tasks/{taskId}/approve", scope.taskId())
                        .with(httpBasic(APPROVER, DEMO_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalTaskVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertPendingState(scope);
        assertThat(rowCount("approval_decision")).isZero();
        assertThat(rowCount("audit_event")).isZero();
        assertThat(rowCount("idempotency_record")).isZero();
    }

    /**
     * 验证相同幂等载荷返回第一次决定，不重复决定、审计或状态变化。
     */
    @Test
    void replaysCompletedDecisionWithoutDuplicateSideEffects() throws Exception
    {
        TestScope scope = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");

        String firstResponse = approveOverHttp(
                scope.taskId(), APPROVER, "same-key", 0L, "同意")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String replayResponse = approveOverHttp(
                scope.taskId(), APPROVER, "same-key", 0L, "同意")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(replayResponse).isEqualTo(firstResponse);
        assertThat(rowCount("approval_decision")).isEqualTo(1);
        assertThat(rowCount("audit_event")).isEqualTo(1);
        assertThat(rowCount("idempotency_record")).isEqualTo(1);
    }

    /**
     * 验证相同键改变决定或载荷时返回专用幂等冲突并保留原决定。
     */
    @Test
    void rejectsReusedIdempotencyKeyWithDifferentDecision() throws Exception
    {
        TestScope scope = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");
        approveOverHttp(scope.taskId(), APPROVER, "conflicting-key", 0L, "同意")
                .andExpect(status().isOk());

        rejectOverHttp(scope.taskId(), "conflicting-key", 0L, "改为驳回")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        assertTerminalState(scope, "APPROVED");
        assertThat(rowCount("approval_decision")).isEqualTo(1);
        assertThat(rowCount("audit_event")).isEqualTo(1);
        assertThat(rowCount("idempotency_record")).isEqualTo(1);
    }

    /**
     * 验证终态任务使用新幂等键也不能被再次修改。
     */
    @Test
    void rejectsNewDecisionAfterTaskReachedTerminalState() throws Exception
    {
        TestScope scope = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");
        approveOverHttp(scope.taskId(), APPROVER, "first-decision", 0L, null)
                .andExpect(status().isOk());

        rejectOverHttp(scope.taskId(), "new-key", 1L, "试图覆盖")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));

        assertTerminalState(scope, "APPROVED");
        assertThat(rowCount("approval_decision")).isEqualTo(1);
        assertThat(rowCount("idempotency_record")).isEqualTo(1);
    }

    /**
     * 验证任务或申请状态、任务版本不满足要求时均不会留下局部写入。
     */
    @Test
    void rejectsStaleVersionAndNonSubmittedRequest() throws Exception
    {
        TestScope stale = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");
        approveOverHttp(stale.taskId(), APPROVER, "stale", 1L, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        assertPendingState(stale);

        clearBusinessData();
        TestScope invalidRequest = insertPendingScope("demo-requester", APPROVER, "APPROVED");
        approveOverHttp(invalidRequest.taskId(), APPROVER, "wrong-request-state", 0L, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));
        assertThat(singleText(
                "SELECT status FROM approval_task WHERE id = ?",
                invalidRequest.taskId())).isEqualTo("PENDING");
        assertThat(rowCount("approval_decision")).isZero();
        assertThat(rowCount("idempotency_record")).isZero();
    }

    /**
     * 验证并发批准和驳回最多一个成功，数据库最终只有一个一致决定。
     */
    @Test
    void allowsAtMostOneConcurrentOppositeDecision() throws Exception
    {
        TestScope scope = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try
        {
            Future<String> approve = executor.submit(() -> classifyConcurrentDecision(
                    scope.taskId(), ApprovalDecisionType.APPROVED, "opposite-approve", start));
            Future<String> reject = executor.submit(() -> classifyConcurrentDecision(
                    scope.taskId(), ApprovalDecisionType.REJECTED, "opposite-reject", start));
            start.countDown();

            assertThat(List.of(
                    approve.get(10, TimeUnit.SECONDS),
                    reject.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
        }
        finally
        {
            executor.shutdownNow();
        }

        assertThat(rowCount("approval_decision")).isEqualTo(1);
        assertThat(rowCount("audit_event")).isEqualTo(1);
        assertThat(rowCount("idempotency_record")).isEqualTo(1);
        assertThat(singleText(
                "SELECT status FROM approval_task WHERE id = ?",
                scope.taskId())).isEqualTo(singleText(
                        "SELECT decision FROM approval_decision WHERE approval_task_id = ?",
                        scope.taskId()));
    }

    /**
     * 验证并发相同幂等请求返回同一结果且只产生一次副作用。
     */
    @Test
    void replaysOneResultForConcurrentIdenticalDecision() throws Exception
    {
        TestScope scope = insertPendingScope("demo-requester", APPROVER, "SUBMITTED");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try
        {
            Future<DecideApprovalResult> first = executor.submit(
                    () -> runConcurrentApproval(scope.taskId(), "concurrent-same", start));
            Future<DecideApprovalResult> second = executor.submit(
                    () -> runConcurrentApproval(scope.taskId(), "concurrent-same", start));
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS))
                    .isEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        finally
        {
            executor.shutdownNow();
        }

        assertThat(rowCount("approval_decision")).isEqualTo(1);
        assertThat(rowCount("audit_event")).isEqualTo(1);
        assertThat(rowCount("idempotency_record")).isEqualTo(1);
    }

    /**
     * 通过 HTTP Basic 发起批准请求。
     */
    private org.springframework.test.web.servlet.ResultActions approveOverHttp(
            UUID taskId,
            String username,
            String key,
            long version,
            String comment) throws Exception
    {
        String commentJson = comment == null ? "null" : "\"" + comment + "\"";
        return mockMvc.perform(post("/api/approval-tasks/{taskId}/approve", taskId)
                .with(httpBasic(username, DEMO_PASSWORD))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalTaskVersion\":" + version
                        + ",\"comment\":" + commentJson + "}"));
    }

    /**
     * 通过 HTTP Basic 发起驳回请求。
     */
    private org.springframework.test.web.servlet.ResultActions rejectOverHttp(
            UUID taskId,
            String key,
            long version,
            String reason) throws Exception
    {
        return mockMvc.perform(post("/api/approval-tasks/{taskId}/reject", taskId)
                .with(httpBasic(APPROVER, DEMO_PASSWORD))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalTaskVersion\":" + version
                        + ",\"comment\":\"" + reason + "\"}"));
    }

    /**
     * 等待统一起跑信号后执行相同的批准命令。
     */
    private DecideApprovalResult runConcurrentApproval(
            UUID taskId,
            String key,
            CountDownLatch start) throws InterruptedException
    {
        start.await();
        return decisionService.decide(
                taskId,
                new DecideApprovalCommand(ApprovalDecisionType.APPROVED, 0L, "同意"),
                key,
                approver());
    }

    /**
     * 执行并发相反决定并把预期业务或并发冲突归一为测试结果。
     */
    private String classifyConcurrentDecision(
            UUID taskId,
            ApprovalDecisionType decision,
            String key,
            CountDownLatch start) throws InterruptedException
    {
        start.await();
        try
        {
            decisionService.decide(
                    taskId,
                    new DecideApprovalCommand(
                            decision,
                            0L,
                            decision == ApprovalDecisionType.REJECTED ? "依据不足" : null),
                    key,
                    approver());
            return "SUCCESS";
        }
        catch (BusinessConflictException | ConcurrentUpdateException exception)
        {
            return "CONFLICT";
        }
    }

    /**
     * 创建审批服务并发测试使用的可信审批人上下文。
     */
    private CurrentUser approver()
    {
        return new CurrentUser(new UserId(APPROVER), Set.of(Role.APPROVER));
    }

    /**
     * 插入一份完整申请及其待处理审批任务。
     */
    private TestScope insertPendingScope(
            String creatorId,
            String assigneeId,
            String requestStatus)
    {
        UUID requestId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-22T01:02:03Z");
        jdbcTemplate.update("""
                INSERT INTO procurement_request (
                    id, business_number, creator_id, title, purpose, department,
                    expected_delivery_date, currency, estimated_total, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId,
                "PR-20260923-" + Integer.toUnsignedString(requestId.hashCode()),
                creatorId,
                "人工审批测试申请",
                "验证唯一且可审计的人工决定",
                "采购部",
                Date.valueOf(LocalDate.of(2026, 10, 1)),
                "CNY",
                new BigDecimal("10.00"),
                requestStatus,
                1L,
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
                "开发显示器",
                "MONITOR",
                "27 英寸",
                new BigDecimal("1"),
                "台",
                new BigDecimal("10.00"));
        jdbcTemplate.update("""
                INSERT INTO approval_task (
                    id, procurement_request_id, assignee_id, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, 'PENDING', 0, ?, ?)
                """,
                taskId,
                requestId,
                assigneeId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        return new TestScope(requestId, taskId);
    }

    /**
     * 断言任务和申请进入相同终态且版本均递增一次。
     */
    private void assertTerminalState(TestScope scope, String status)
    {
        assertThat(singleText(
                "SELECT status FROM approval_task WHERE id = ?", scope.taskId()))
                .isEqualTo(status);
        assertThat(singleLong(
                "SELECT version FROM approval_task WHERE id = ?", scope.taskId()))
                .isEqualTo(1L);
        assertThat(singleText(
                "SELECT status FROM procurement_request WHERE id = ?", scope.requestId()))
                .isEqualTo(status);
        assertThat(singleLong(
                "SELECT version FROM procurement_request WHERE id = ?", scope.requestId()))
                .isEqualTo(2L);
    }

    /**
     * 断言失败审批没有改变任务和申请的待处理状态。
     */
    private void assertPendingState(TestScope scope)
    {
        assertThat(singleText(
                "SELECT status FROM approval_task WHERE id = ?", scope.taskId()))
                .isEqualTo("PENDING");
        assertThat(singleLong(
                "SELECT version FROM approval_task WHERE id = ?", scope.taskId()))
                .isZero();
        assertThat(singleText(
                "SELECT status FROM procurement_request WHERE id = ?", scope.requestId()))
                .isEqualTo("SUBMITTED");
    }

    /**
     * 查询指定表的总行数。
     */
    private int rowCount(String tableName)
    {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    /**
     * 查询带单个 UUID 参数的文本结果。
     */
    private String singleText(String sql, UUID id)
    {
        return jdbcTemplate.queryForObject(sql, String.class, id);
    }

    /**
     * 查询带单个 UUID 参数的长整数结果。
     */
    private Long singleLong(String sql, UUID id)
    {
        return jdbcTemplate.queryForObject(sql, Long.class, id);
    }

    /**
     * 保存一组关联测试申请和任务标识。
     *
     * @param requestId 申请标识
     * @param taskId 任务标识
     */
    private record TestScope(UUID requestId, UUID taskId)
    {
    }
}
