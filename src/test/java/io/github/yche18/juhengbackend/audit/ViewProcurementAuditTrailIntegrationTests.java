package io.github.yche18.juhengbackend.audit;

import io.github.yche18.juhengbackend.audit.application.AuditTrailQueryRepository;
import io.github.yche18.juhengbackend.audit.domain.AuditEvent;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 PostgreSQL、Spring Security 和 HTTP 边界验证申请审计轨迹查询。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ViewProcurementAuditTrailIntegrationTests
{

    private static final String DEMO_PASSWORD = "juheng-local";
    private static final String REQUESTER = "demo-requester";
    private static final String APPROVER = "demo-approver";
    private static final String OTHER_BUSINESS_USER = "demo-requester-approver";
    private static final String ADMINISTRATOR = "demo-admin";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_view_audit_trail_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditTrailQueryRepository queryRepository;

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
     * 验证申请创建者能读取完整白名单字段，并按时间与事件标识稳定升序查看关键事件。
     */
    @Test
    void requesterViewsStableCompleteAuditTrailWithoutSensitiveFields() throws Exception
    {
        UUID requestId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID taskId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        insertRequest(requestId, REQUESTER, "APPROVED");
        insertTask(taskId, requestId, APPROVER, "APPROVED");

        Instant createdAt = Instant.parse("2026-09-20T08:00:00Z");
        Instant tiedAt = Instant.parse("2026-09-20T09:00:00Z");
        Instant decidedAt = Instant.parse("2026-09-20T10:00:00Z");
        UUID createdEventId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID updatedEventId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        UUID submittedEventId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID assignedEventId = UUID.fromString("30000000-0000-0000-0000-000000000004");
        UUID approvedEventId = UUID.fromString("30000000-0000-0000-0000-000000000005");
        insertAuditEvent(createdEventId, requestId, REQUESTER,
                "PROCUREMENT_REQUEST_CREATED", "PROCUREMENT_REQUEST", requestId,
                createdAt, "create-request-001");
        insertAuditEvent(submittedEventId, requestId, REQUESTER,
                "PROCUREMENT_REQUEST_SUBMITTED", "PROCUREMENT_REQUEST", requestId,
                tiedAt, "submit-request-001");
        insertAuditEvent(updatedEventId, requestId, REQUESTER,
                "PROCUREMENT_REQUEST_UPDATED", "PROCUREMENT_REQUEST", requestId,
                tiedAt, "update-request-001");
        insertAuditEvent(assignedEventId, requestId, REQUESTER,
                "APPROVAL_TASK_ASSIGNED", "APPROVAL_TASK", taskId,
                tiedAt, "submit-request-001");
        insertAuditEvent(approvedEventId, requestId, APPROVER,
                "APPROVAL_TASK_APPROVED", "APPROVAL_TASK", taskId,
                decidedAt, "approve-task-001");

        String response = mockMvc.perform(get(
                            "/api/procurement-requests/{requestId}/audit-events", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.procurementRequestId").value(requestId.toString()))
                .andExpect(jsonPath("$.events.length()").value(5))
                .andExpect(jsonPath("$.events[0].id").value(createdEventId.toString()))
                .andExpect(jsonPath("$.events[0].actorId").value(REQUESTER))
                .andExpect(jsonPath("$.events[0].action").value("PROCUREMENT_REQUEST_CREATED"))
                .andExpect(jsonPath("$.events[0].targetType").value("PROCUREMENT_REQUEST"))
                .andExpect(jsonPath("$.events[0].targetId").value(requestId.toString()))
                .andExpect(jsonPath("$.events[0].timestamp").value(createdAt.toString()))
                .andExpect(jsonPath("$.events[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.events[0].requestIdentifier").value("create-request-001"))
                .andExpect(jsonPath("$.events[1].id").value(updatedEventId.toString()))
                .andExpect(jsonPath("$.events[2].id").value(submittedEventId.toString()))
                .andExpect(jsonPath("$.events[3].id").value(assignedEventId.toString()))
                .andExpect(jsonPath("$.events[4].id").value(approvedEventId.toString()))
                .andExpect(jsonPath("$.events[4].actorId").value(APPROVER))
                .andExpect(jsonPath("$.events[4].action").value("APPROVAL_TASK_APPROVED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("credential")
                .doesNotContainIgnoringCase("secret")
                .doesNotContainIgnoringCase("stackTrace")
                .doesNotContainIgnoringCase("exception");
    }

    /**
     * 验证任务受理审批人能够读取处理申请所需的同一条关键审计轨迹。
     */
    @Test
    void assignedApproverViewsRequestAuditTrail() throws Exception
    {
        UUID requestId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "SUBMITTED");
        insertTask(taskId, requestId, APPROVER, "PENDING");
        insertAuditEvent(eventId, requestId, REQUESTER,
                "APPROVAL_TASK_ASSIGNED", "APPROVAL_TASK", taskId,
                Instant.parse("2026-09-21T09:00:00Z"), "submit-request-002");

        mockMvc.perform(get("/api/procurement-requests/{requestId}/audit-events", requestId)
                        .with(httpBasic(APPROVER, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procurementRequestId").value(requestId.toString()))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$.events[0].action").value("APPROVAL_TASK_ASSIGNED"));
    }

    /**
     * 直接验证查询端口同时支持创建者和任务受理人，并拒绝其他业务用户范围。
     */
    @Test
    void queryRepositoryScopesTrailByCreatorOrAssignee()
    {
        UUID requestId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "SUBMITTED");
        insertTask(taskId, requestId, APPROVER, "PENDING");
        insertAuditEvent(eventId, requestId, REQUESTER,
                "PROCUREMENT_REQUEST_SUBMITTED", "PROCUREMENT_REQUEST", requestId,
                Instant.parse("2026-09-21T10:00:00Z"), "submit-request-003");

        Optional<List<AuditEvent>> requesterTrail = queryRepository.findVisibleByRequestId(
                requestId, new UserId(REQUESTER));
        Optional<List<AuditEvent>> approverTrail = queryRepository.findVisibleByRequestId(
                requestId, new UserId(APPROVER));
        Optional<List<AuditEvent>> outsiderTrail = queryRepository.findVisibleByRequestId(
                requestId, new UserId(OTHER_BUSINESS_USER));

        assertThat(requesterTrail).isPresent();
        assertThat(requesterTrail.orElseThrow()).extracting(AuditEvent::id).containsExactly(eventId);
        assertThat(approverTrail).isPresent();
        assertThat(approverTrail.orElseThrow()).extracting(AuditEvent::id).containsExactly(eventId);
        assertThat(outsiderTrail).isEmpty();
    }

    /**
     * 验证越权申请和真实不存在申请使用相同 404，且响应不包含任何审计内容。
     */
    @Test
    void hidesWhetherAnUnauthorizedAuditTrailExists() throws Exception
    {
        UUID requestId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "DRAFT");
        insertAuditEvent(eventId, requestId, REQUESTER,
                "PROCUREMENT_REQUEST_CREATED", "PROCUREMENT_REQUEST", requestId,
                Instant.parse("2026-09-21T11:00:00Z"), "non-visible-request-identifier");

        String unauthorizedResponse = mockMvc.perform(get(
                            "/api/procurement-requests/{requestId}/audit-events", requestId)
                        .with(httpBasic(OTHER_BUSINESS_USER, DEMO_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource was not found"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(get("/api/procurement-requests/{requestId}/audit-events", UUID.randomUUID())
                        .with(httpBasic(OTHER_BUSINESS_USER, DEMO_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(unauthorizedResponse)
                .doesNotContain(eventId.toString())
                .doesNotContain("PROCUREMENT_REQUEST_CREATED")
                .doesNotContain("non-visible-request-identifier");
    }

    /**
     * 验证未认证身份和非业务角色分别得到 401 与 403。
     */
    @Test
    void requiresAuthenticationAndRequesterOrApproverRole() throws Exception
    {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(get("/api/procurement-requests/{requestId}/audit-events", requestId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/procurement-requests/{requestId}/audit-events", requestId)
                        .with(httpBasic(ADMINISTRATOR, DEMO_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 验证审计轨迹只开放 GET，普通用户的写方法返回 405 且不会改变既有事件。
     */
    @Test
    void exposesNoAuditMutationApi() throws Exception
    {
        UUID requestId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        insertRequest(requestId, REQUESTER, "DRAFT");
        insertAuditEvent(eventId, requestId, REQUESTER,
                "PROCUREMENT_REQUEST_CREATED", "PROCUREMENT_REQUEST", requestId,
                Instant.parse("2026-09-21T12:00:00Z"), "create-request-004");

        mockMvc.perform(post("/api/procurement-requests/{requestId}/audit-events", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(put("/api/procurement-requests/{requestId}/audit-events", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(delete("/api/procurement-requests/{requestId}/audit-events", requestId)
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE id = ?", Integer.class, eventId);
        assertThat(eventCount).isEqualTo(1);
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
        Instant createdAt = Instant.parse("2026-09-20T08:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO procurement_request (
                    id, business_number, creator_id, title, purpose, department,
                    expected_delivery_date, currency, estimated_total, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId,
                "PR-20260920-" + requestId.toString().substring(0, 8),
                creatorId,
                "合成审计测试申请",
                "验证审计轨迹查询",
                "测试部门",
                Date.valueOf(LocalDate.of(2026, 10, 1)),
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
        Instant createdAt = Instant.parse("2026-09-20T09:00:00Z");
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
     * 插入一条包含完整 R1 字段的合成审计事件。
     *
     * @param eventId 事件标识
     * @param requestId 所属申请标识
     * @param actorId 操作者标识
     * @param action 动作
     * @param targetType 目标类型
     * @param targetId 目标标识
     * @param occurredAt 发生时间
     * @param requestIdentifier 请求或幂等标识
     */
    private void insertAuditEvent(
            UUID eventId,
            UUID requestId,
            String actorId,
            String action,
            String targetType,
            UUID targetId,
            Instant occurredAt,
            String requestIdentifier)
    {
        jdbcTemplate.update("""
                INSERT INTO audit_event (
                    id, procurement_request_id, actor_id, action, target_type,
                    target_id, occurred_at, result, request_identifier
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventId,
                requestId,
                actorId,
                action,
                targetType,
                targetId,
                Timestamp.from(occurredAt),
                "SUCCESS",
                requestIdentifier);
    }
}
