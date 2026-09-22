package io.github.yche18.juhengbackend.approval;

import io.github.yche18.juhengbackend.approval.application.ApprovalTaskQueryRepository;
import io.github.yche18.juhengbackend.approval.application.ApprovalTaskSummary;
import io.github.yche18.juhengbackend.approval.application.ListAssignedApprovalTasksQuery;
import io.github.yche18.juhengbackend.approval.domain.ApprovalTaskStatus;
import io.github.yche18.juhengbackend.common.application.PageResult;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 PostgreSQL、Spring Security 和 HTTP 边界验证查看本人审批任务用例。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ViewAssignedApprovalTasksIntegrationTests
{

    private static final String DEMO_PASSWORD = "juheng-local";
    private static final String APPROVER = "demo-approver";
    private static final String OTHER_APPROVER = "demo-requester-approver";
    private static final String REQUESTER = "demo-requester";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_view_approval_task_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApprovalTaskQueryRepository queryRepository;

    /**
     * 清除上一个测试产生的业务数据，同时保留 Flyway 结构。
     */
    @BeforeEach
    void clearBusinessData()
    {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM approval_task");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM procurement_item");
        jdbcTemplate.update("DELETE FROM procurement_request");
    }

    /**
     * 验证列表只返回当前审批人的任务，并按创建时间与 ID 稳定分页及支持三个状态筛选。
     */
    @Test
    void listsOnlyAssignedTasksWithStablePaginationAndStatusFilters() throws Exception
    {
        Instant older = Instant.parse("2026-09-20T10:00:00Z");
        Instant tied = Instant.parse("2026-09-21T10:00:00Z");
        UUID oldestTaskId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID tiedLowerTaskId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        UUID tiedHigherTaskId = UUID.fromString("10000000-0000-0000-0000-000000000003");
        UUID oldestRequestId = insertRequest("41", "待审批申请", "SUBMITTED", older, 1L, "10.00");
        UUID approvedRequestId = insertRequest("42", "已批准申请", "APPROVED", tied, 2L, "20.00");
        UUID rejectedRequestId = insertRequest("43", "已驳回申请", "REJECTED", tied, 3L, "30.00");
        insertTask(oldestTaskId, oldestRequestId, APPROVER, "PENDING", older, 0L);
        insertTask(tiedLowerTaskId, approvedRequestId, APPROVER, "APPROVED", tied, 1L);
        insertTask(tiedHigherTaskId, rejectedRequestId, APPROVER, "REJECTED", tied, 1L);

        UUID otherRequestId = insertRequest(
                "44",
                "其他审批人申请",
                "SUBMITTED",
                Instant.parse("2026-09-22T10:00:00Z"),
                1L,
                "999.00");
        insertTask(UUID.randomUUID(), otherRequestId, OTHER_APPROVER, "PENDING",
                Instant.parse("2026-09-22T10:00:00Z"), 0L);

        mockMvc.perform(get("/api/approval-tasks")
                        .with(httpBasic(APPROVER, DEMO_PASSWORD))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(tiedHigherTaskId.toString()))
                .andExpect(jsonPath("$.content[0].procurementRequest.title").value("已驳回申请"))
                .andExpect(jsonPath("$.content[0].procurementRequest.creatorId").value(REQUESTER))
                .andExpect(jsonPath("$.content[1].id").value(tiedLowerTaskId.toString()));

        mockMvc.perform(get("/api/approval-tasks")
                        .with(httpBasic(APPROVER, DEMO_PASSWORD))
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(oldestTaskId.toString()));

        mockMvc.perform(get("/api/approval-tasks")
                        .with(httpBasic(APPROVER, DEMO_PASSWORD))
                        .param("page", "5")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        assertStatusFilter("PENDING", oldestTaskId);
        assertStatusFilter("APPROVED", tiedLowerTaskId);
        assertStatusFilter("REJECTED", tiedHigherTaskId);
    }

    /**
     * 直接验证查询端口把审批人范围传入数据库条件，而不是在返回后过滤。
     */
    @Test
    void queryRepositoryScopesPageAndDetailsByAssignee()
    {
        Instant createdAt = Instant.parse("2026-09-22T10:00:00Z");
        UUID ownRequestId = insertRequest("51", "本人任务申请", "SUBMITTED", createdAt, 1L, "10.00");
        UUID otherRequestId = insertRequest("52", "他人任务申请", "SUBMITTED", createdAt, 1L, "20.00");
        UUID ownTaskId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID otherTaskId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        insertTask(ownTaskId, ownRequestId, APPROVER, "PENDING", createdAt, 0L);
        insertTask(otherTaskId, otherRequestId, OTHER_APPROVER, "PENDING", createdAt, 0L);

        UserId approverId = new UserId(APPROVER);
        PageResult<ApprovalTaskSummary> page = queryRepository.findAssignedPage(
                approverId,
                new ListAssignedApprovalTasksQuery(0, 20, null));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).extracting(ApprovalTaskSummary::id).containsExactly(ownTaskId);
        assertThat(queryRepository.findAssignedById(ownTaskId, approverId)).isPresent();
        assertThat(queryRepository.findAssignedById(otherTaskId, approverId)).isEmpty();
    }

    /**
     * 验证受理审批人可以读取任务、完整申请、采购项和两类版本信息。
     */
    @Test
    void returnsAssignedTaskAndCompleteRequestDetails() throws Exception
    {
        Instant createdAt = Instant.parse("2026-09-22T08:00:00Z");
        UUID requestId = insertRequest("61", "研发电脑采购", "SUBMITTED", createdAt, 7L, "40.09");
        insertItem(requestId, 2, "显示器", "MONITOR", "2.0000", "10.00");
        insertItem(requestId, 1, "笔记本", "LAPTOP", "1.0050", "19.99");
        UUID taskId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        insertTask(taskId, requestId, APPROVER, "PENDING", createdAt, 3L);

        mockMvc.perform(get("/api/approval-tasks/{taskId}", taskId)
                        .with(httpBasic(APPROVER, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.assigneeId").value(APPROVER))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.procurementRequest.id").value(requestId.toString()))
                .andExpect(jsonPath("$.procurementRequest.businessNumber").value("PR-20260922-61"))
                .andExpect(jsonPath("$.procurementRequest.creatorId").value(REQUESTER))
                .andExpect(jsonPath("$.procurementRequest.title").value("研发电脑采购"))
                .andExpect(jsonPath("$.procurementRequest.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.procurementRequest.version").value(7))
                .andExpect(jsonPath("$.procurementRequest.items.length()").value(2))
                .andExpect(jsonPath("$.procurementRequest.items[0].lineNumber").value(1))
                .andExpect(jsonPath("$.procurementRequest.items[0].estimatedLineTotal").value(20.09))
                .andExpect(jsonPath("$.procurementRequest.items[1].lineNumber").value(2))
                .andExpect(jsonPath("$.procurementRequest.items[1].estimatedLineTotal").value(20.00));
    }

    /**
     * 验证他人任务和真实不存在任务使用相同错误代码，且不泄露任务或申请内容。
     */
    @Test
    void hidesWhetherAnUnassignedTaskExists() throws Exception
    {
        Instant createdAt = Instant.parse("2026-09-22T09:00:00Z");
        UUID requestId = insertRequest("71", "不应泄露的申请", "SUBMITTED", createdAt, 1L, "88.00");
        UUID taskId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        insertTask(taskId, requestId, OTHER_APPROVER, "PENDING", createdAt, 0L);

        String unassignedResponse = mockMvc.perform(get("/api/approval-tasks/{taskId}", taskId)
                        .with(httpBasic(APPROVER, DEMO_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource was not found"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(get("/api/approval-tasks/{taskId}", UUID.randomUUID())
                        .with(httpBasic(APPROVER, DEMO_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource was not found"));

        assertThat(unassignedResponse)
                .doesNotContain("不应泄露的申请")
                .doesNotContain("PR-20260922-71")
                .doesNotContain(OTHER_APPROVER);
    }

    /**
     * 验证缺少 APPROVER 角色时列表和详情查询均被应用层拒绝。
     */
    @Test
    void deniesUsersWithoutApproverRole() throws Exception
    {
        mockMvc.perform(get("/api/approval-tasks")
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/approval-tasks/{taskId}", UUID.randomUUID())
                        .with(httpBasic(REQUESTER, DEMO_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 验证非法页码、页大小、任务状态和 UUID 在进入查询前被拒绝。
     */
    @Test
    void rejectsInvalidPaginationStatusAndTaskId() throws Exception
    {
        List<String[]> invalidParameters = List.of(
                new String[]{"page", "-1"},
                new String[]{"size", "0"},
                new String[]{"size", "101"},
                new String[]{"status", "UNKNOWN"});

        for (String[] parameter : invalidParameters)
        {
            mockMvc.perform(get("/api/approval-tasks")
                            .with(httpBasic(APPROVER, DEMO_PASSWORD))
                            .param(parameter[0], parameter[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        mockMvc.perform(get("/api/approval-tasks/not-a-uuid")
                        .with(httpBasic(APPROVER, DEMO_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * 断言指定状态筛选只返回对应任务。
     *
     * @param statusFilter 状态筛选值
     * @param expectedTaskId 预期唯一任务标识
     */
    private void assertStatusFilter(String statusFilter, UUID expectedTaskId) throws Exception
    {
        mockMvc.perform(get("/api/approval-tasks")
                        .with(httpBasic(APPROVER, DEMO_PASSWORD))
                        .param("status", statusFilter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expectedTaskId.toString()))
                .andExpect(jsonPath("$.content[0].status").value(statusFilter));
    }

    /**
     * 插入一条测试申请主记录。
     *
     * @param suffix 业务编号后缀
     * @param title 申请标题
     * @param status 申请状态
     * @param createdAt 创建时间
     * @param version 申请版本
     * @param estimatedTotal 预计总额文本
     * @return 新申请标识
     */
    private UUID insertRequest(
            String suffix,
            String title,
            String status,
            Instant createdAt,
            long version,
            String estimatedTotal)
    {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO procurement_request (
                    id, business_number, creator_id, title, purpose, department,
                    expected_delivery_date, currency, estimated_total, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                "PR-20260922-" + suffix,
                REQUESTER,
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
        return id;
    }

    /**
     * 为测试申请插入审批任务。
     *
     * @param taskId 任务标识
     * @param requestId 申请标识
     * @param assigneeId 受理审批人
     * @param status 任务状态
     * @param createdAt 创建时间
     * @param version 任务版本
     */
    private void insertTask(
            UUID taskId,
            UUID requestId,
            String assigneeId,
            String status,
            Instant createdAt,
            long version)
    {
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
