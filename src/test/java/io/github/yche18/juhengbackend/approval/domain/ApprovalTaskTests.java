package io.github.yche18.juhengbackend.approval.domain;

import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.yche18.juhengbackend.common.error.BusinessConflictException;
import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;

/**
 * 验证审批任务创建时的服务端默认值和自审不变量。
 */
class ApprovalTaskTests
{

    /**
     * 验证合法路由只能创建 PENDING、版本为 0 的任务。
     */
    @Test
    void createsPendingTaskWithServerControlledDefaults()
    {
        UUID taskId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-22T01:02:03Z");

        ApprovalTask task = ApprovalTask.createPending(
                taskId,
                requestId,
                new UserId("requester"),
                new UserId("approver"),
                createdAt);

        assertThat(task.id()).isEqualTo(taskId);
        assertThat(task.procurementRequestId()).isEqualTo(requestId);
        assertThat(task.assigneeId()).isEqualTo(new UserId("approver"));
        assertThat(task.status()).isEqualTo(ApprovalTaskStatus.PENDING);
        assertThat(task.version()).isZero();
        assertThat(task.createdAt()).isEqualTo(createdAt);
        assertThat(task.updatedAt()).isEqualTo(createdAt);
    }

    /**
     * 验证即使 Application 路由校验遗漏，Domain 仍拒绝申请人审批自己。
     */
    @Test
    void rejectsSelfAssignment()
    {
        UserId requester = new UserId("same-user");

        assertThatThrownBy(() -> ApprovalTask.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                requester,
                requester,
                Instant.parse("2026-09-22T01:02:03Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own request");
    }

    /**
     * 验证受理审批人批准待处理任务后生成可信、匹配且版本递增的唯一决定。
     */
    @Test
    void approvesPendingTaskWithTrustedActorAndTime()
    {
        ApprovalTask pending = pendingTask();
        Instant decidedAt = Instant.parse("2026-09-23T01:02:03Z");

        ApprovalTask approved = pending.approve(
                0L,
                new UserId("approver"),
                decidedAt,
                "  同意采购  ");

        ApprovalDecision decision = approved.decision().orElseThrow();
        assertThat(approved.status()).isEqualTo(ApprovalTaskStatus.APPROVED);
        assertThat(approved.version()).isEqualTo(1L);
        assertThat(approved.updatedAt()).isEqualTo(decidedAt);
        assertThat(decision.approvalTaskId()).isEqualTo(approved.id());
        assertThat(decision.decision()).isEqualTo(ApprovalDecisionType.APPROVED);
        assertThat(decision.actorId()).isEqualTo(new UserId("approver"));
        assertThat(decision.decidedAt()).isEqualTo(decidedAt);
        assertThat(decision.comment()).isEqualTo("同意采购");
    }

    /**
     * 验证驳回必须包含非空原因，并生成与任务终态一致的决定。
     */
    @Test
    void rejectsPendingTaskOnlyWithReason()
    {
        ApprovalTask pending = pendingTask();
        Instant decidedAt = Instant.parse("2026-09-23T01:02:03Z");

        assertThatThrownBy(() -> pending.reject(
                0L,
                new UserId("approver"),
                decidedAt,
                "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");

        ApprovalTask rejected = pending.reject(
                0L,
                new UserId("approver"),
                decidedAt,
                "预算依据不足");
        assertThat(rejected.status()).isEqualTo(ApprovalTaskStatus.REJECTED);
        assertThat(rejected.decision().orElseThrow().comment()).isEqualTo("预算依据不足");
    }

    /**
     * 验证非受理人、过期版本和终态任务均不能形成第二个决定。
     */
    @Test
    void rejectsUnauthorizedStaleAndTerminalDecisions()
    {
        ApprovalTask pending = pendingTask();
        Instant decidedAt = Instant.parse("2026-09-23T01:02:03Z");

        assertThatThrownBy(() -> pending.approve(
                0L, new UserId("other"), decidedAt, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assigned");
        assertThatThrownBy(() -> pending.approve(
                1L, new UserId("approver"), decidedAt, null))
                .isInstanceOf(ConcurrentUpdateException.class);

        ApprovalTask approved = pending.approve(
                0L, new UserId("approver"), decidedAt, null);
        assertThatThrownBy(() -> approved.reject(
                1L, new UserId("approver"), decidedAt, "改变决定"))
                .isInstanceOf(BusinessConflictException.class);
    }

    /**
     * 创建领域测试使用的合法待处理任务。
     *
     * @return 初始版本待处理任务
     */
    private ApprovalTask pendingTask()
    {
        return ApprovalTask.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new UserId("requester"),
                new UserId("approver"),
                Instant.parse("2026-09-22T01:02:03Z"));
    }
}
