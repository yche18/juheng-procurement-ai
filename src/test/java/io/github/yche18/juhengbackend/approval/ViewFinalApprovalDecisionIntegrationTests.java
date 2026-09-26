package io.github.yche18.juhengbackend.approval;

import io.github.yche18.juhengbackend.approval.application.FinalApprovalDecisionDetails;
import io.github.yche18.juhengbackend.approval.application.FinalApprovalDecisionQueryRepository;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 PostgreSQL、Spring Security 和 HTTP 边界验证最终审批决定查询。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ViewFinalApprovalDecisionIntegrationTests
{

    private static final String DEMO_PASSWORD = "juheng-local";
    private static final String REQUESTER = "demo-requester";
    private static final String APPROVER = "demo-approver";
    private static final String OTHER_BUSINESS_USER = "demo-requester-approver";
    private static final String ADMINISTRATOR = "demo-admin";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_view_final_decision_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FinalApprovalDecisionQueryRepository queryRepository;

    /**
     * 清除上一个测试产生的业务数据，同时保留 Flyway 结构。
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
     * 验证申请创建者能稳定读取批准结果的完整白名单字段，且空意见不会被伪造成文本。
     */
    @Test
    void requesterViewsStableApprovedDecisionWithoutSensitiveFields() throws Exception
    {
        UUID requestId = UUID.fromString("11000000-0000-0000-0000-000000000001");
        UUID taskId = UUID.fromString("21000000-0000-0000-0000-000000000001");
        UUID decisionId = UUID.fromString("31000000-0000-0000-0000-000000000001");
        Instant decidedAt = Instant.parse("2026-09-25T08:30:00Z");
        insertRequest(requestId, REQUESTER, "APPROVED");
        insertTask(taskId, requestId, APPROVER, "APPROVED");
        insertDecision(decisionId, taskId, "APPROVED", APPROVER, decidedAt, null);

        String firstResponse = mockMvc.perform(get(
                            "/api/procurement-requests/{requestId}/approval-decision", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.procurementRequestId").value(requestId.toString()))
                .andExpect(jsonPath("$.approvalTaskId").value(taskId.toString()))
                .andExpect(jsonPath("$.decisionId").value(decisionId.toString()))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.actorId").value(APPROVER))
                .andExpect(jsonPath("$.decidedAt").value(decidedAt.toString()))
                .andExpect(jsonPath("$.comment").value(nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(get(
                            "/api/procurement-requests/{requestId}/approval-decision", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(secondResponse).isEqualTo(firstResponse);
        assertThat(firstResponse)
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("credential")
                .doesNotContainIgnoringCase("stackTrace")
                .doesNotContainIgnoringCase("exception");
    }

    /**
     * 验证任务受理审批人能读取数据库中唯一的驳回决定及其原因。
     */
    @Test
    void assignedApproverViewsRejectedDecision() throws Exception
    {
        UUID requestId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();
        Instant decidedAt = Instant.parse("2026-09-25T09:30:00Z");
        insertRequest(requestId, REQUESTER, "REJECTED");
        insertTask(taskId, requestId, APPROVER, "REJECTED");
        insertDecision(decisionId, taskId, "REJECTED", APPROVER, decidedAt, "预算依据不足");

        mockMvc.perform(get("/api/procurement-requests/{requestId}/approval-decision", requestId)
                        .with(httpBasic(APPROVER, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procurementRequestId").value(requestId.toString()))
                .andExpect(jsonPath("$.approvalTaskId").value(taskId.toString()))
                .andExpect(jsonPath("$.decisionId").value(decisionId.toString()))
                .andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(jsonPath("$.actorId").value(APPROVER))
                .andExpect(jsonPath("$.decidedAt").value(decidedAt.toString()))
                .andExpect(jsonPath("$.comment").value("预算依据不足"));
    }

    /**
     * 直接验证查询端口只允许申请创建者和任务受理人读取同一条最终决定。
     */
    @Test
    void queryRepositoryScopesDecisionByCreatorOrAssignee()
    {
        UUID requestId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "APPROVED");
        insertTask(taskId, requestId, APPROVER, "APPROVED");
        insertDecision(
                decisionId,
                taskId,
                "APPROVED",
                APPROVER,
                Instant.parse("2026-09-25T10:30:00Z"),
                "同意采购");

        Optional<FinalApprovalDecisionDetails> requesterDecision = queryRepository.findVisibleByRequestId(
                requestId, new UserId(REQUESTER));
        Optional<FinalApprovalDecisionDetails> approverDecision = queryRepository.findVisibleByRequestId(
                requestId, new UserId(APPROVER));
        Optional<FinalApprovalDecisionDetails> outsiderDecision = queryRepository.findVisibleByRequestId(
                requestId, new UserId(OTHER_BUSINESS_USER));

        assertThat(requesterDecision).isPresent();
        assertThat(requesterDecision.orElseThrow().decisionId()).isEqualTo(decisionId);
        assertThat(requesterDecision.orElseThrow().decision()).isEqualTo(ApprovalDecisionType.APPROVED);
        assertThat(approverDecision).isEqualTo(requesterDecision);
        assertThat(outsiderDecision).isEmpty();
    }

    /**
     * 验证尚无决定、申请不存在和越权访问使用完全一致的安全 404 语义。
     */
    @Test
    void usesSameNotFoundSemanticsWithoutFabricatingDecision() throws Exception
    {
        UUID pendingRequestId = UUID.randomUUID();
        UUID pendingTaskId = UUID.randomUUID();
        insertRequest(pendingRequestId, REQUESTER, "SUBMITTED");
        insertTask(pendingTaskId, pendingRequestId, APPROVER, "PENDING");

        UUID hiddenRequestId = UUID.randomUUID();
        UUID hiddenTaskId = UUID.randomUUID();
        UUID hiddenDecisionId = UUID.randomUUID();
        insertRequest(hiddenRequestId, REQUESTER, "REJECTED");
        insertTask(hiddenTaskId, hiddenRequestId, APPROVER, "REJECTED");
        insertDecision(
                hiddenDecisionId,
                hiddenTaskId,
                "REJECTED",
                APPROVER,
                Instant.parse("2026-09-25T11:30:00Z"),
                "不可泄露的审批原因");

        String pendingResponse = getNotFoundResponse(pendingRequestId, REQUESTER);
        getNotFoundResponse(UUID.randomUUID(), REQUESTER);
        String unauthorizedResponse = getNotFoundResponse(hiddenRequestId, OTHER_BUSINESS_USER);

        assertThat(pendingResponse)
                .doesNotContain("APPROVED")
                .doesNotContain("REJECTED");
        assertThat(unauthorizedResponse)
                .doesNotContain(hiddenTaskId.toString())
                .doesNotContain(hiddenDecisionId.toString())
                .doesNotContain(APPROVER)
                .doesNotContain("不可泄露的审批原因")
                .doesNotContain("APPROVED")
                .doesNotContain("REJECTED");
    }

    /**
     * 验证未认证身份和非业务角色分别得到 401 与 403。
     */
    @Test
    void requiresAuthenticationAndRequesterOrApproverRole() throws Exception
    {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(get("/api/procurement-requests/{requestId}/approval-decision", requestId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/procurement-requests/{requestId}/approval-decision", requestId)
                        .with(httpBasic(ADMINISTRATOR, DEMO_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 验证最终决定资源只开放 GET，所有常见写方法都返回 405 且不改变数据库事实。
     */
    @Test
    void exposesNoFinalDecisionMutationApi() throws Exception
    {
        UUID requestId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "APPROVED");
        insertTask(taskId, requestId, APPROVER, "APPROVED");
        insertDecision(
                decisionId,
                taskId,
                "APPROVED",
                APPROVER,
                Instant.parse("2026-09-25T12:30:00Z"),
                null);

        mockMvc.perform(post("/api/procurement-requests/{requestId}/approval-decision", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(put("/api/procurement-requests/{requestId}/approval-decision", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(patch("/api/procurement-requests/{requestId}/approval-decision", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(delete("/api/procurement-requests/{requestId}/approval-decision", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        Integer decisionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_decision WHERE id = ?", Integer.class, decisionId);
        assertThat(decisionCount).isEqualTo(1);
    }

    /**
     * 调用最终决定查询并断言统一的安全未找到响应。
     *
     * @param requestId 采购申请标识
     * @param username 当前认证用户名
     * @return 完整错误响应文本
     */
    private String getNotFoundResponse(UUID requestId, String username) throws Exception
    {
        return mockMvc.perform(get("/api/procurement-requests/{requestId}/approval-decision", requestId)
                        .with(httpBasic(username, DEMO_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource was not found"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /**
     * 插入一条测试申请主记录。
     *
     * @param requestId 申请标识
     * @param creatorId 创建者标识
     * @param status 申请状态
     */
    private void insertRequest(UUID requestId, String creatorId, String status)
    {
        Instant createdAt = Instant.parse("2026-09-25T07:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO procurement_request (
                    id, business_number, creator_id, title, purpose, department,
                    expected_delivery_date, currency, estimated_total, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId,
                "PR-20260925-" + requestId.toString().substring(0, 8),
                creatorId,
                "最终决定查询测试申请",
                "验证最终审批决定读取",
                "测试部门",
                Date.valueOf(LocalDate.of(2026, 10, 10)),
                "CNY",
                new BigDecimal("100.00"),
                status,
                1L,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    /**
     * 为测试申请插入审批任务。
     *
     * @param taskId 任务标识
     * @param requestId 所属申请标识
     * @param assigneeId 任务受理人
     * @param status 任务状态
     */
    private void insertTask(UUID taskId, UUID requestId, String assigneeId, String status)
    {
        Instant createdAt = Instant.parse("2026-09-25T08:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO approval_task (
                    id, procurement_request_id, assignee_id, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                taskId,
                requestId,
                assigneeId,
                status,
                1L,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    /**
     * 插入一条不可变的最终审批决定。
     *
     * @param decisionId 决定标识
     * @param taskId 所属审批任务标识
     * @param decision 决定类型
     * @param actorId 可信操作者标识
     * @param decidedAt 服务端决定时间
     * @param comment 决定意见
     */
    private void insertDecision(
            UUID decisionId,
            UUID taskId,
            String decision,
            String actorId,
            Instant decidedAt,
            String comment)
    {
        jdbcTemplate.update("""
                INSERT INTO approval_decision (
                    id, approval_task_id, decision, actor_id, decided_at, comment
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                decisionId,
                taskId,
                decision,
                actorId,
                Timestamp.from(decidedAt),
                comment);
    }
}
