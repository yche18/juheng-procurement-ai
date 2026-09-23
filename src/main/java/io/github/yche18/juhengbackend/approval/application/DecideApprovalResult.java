package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.approval.domain.ApprovalDecision;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;
import io.github.yche18.juhengbackend.approval.domain.ApprovalTask;
import io.github.yche18.juhengbackend.approval.domain.ApprovalTaskStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 首次审批成功或幂等重放时返回的稳定结果。
 *
 * @param approvalTaskId 审批任务标识
 * @param approvalTaskStatus 任务最终状态
 * @param approvalTaskVersion 任务最终版本
 * @param decisionId 审批决定标识
 * @param decision 决定类型
 * @param actorId 可信操作者标识
 * @param decidedAt 服务端决定时间
 * @param comment 决定意见
 */
public record DecideApprovalResult(
        UUID approvalTaskId,
        ApprovalTaskStatus approvalTaskStatus,
        long approvalTaskVersion,
        UUID decisionId,
        ApprovalDecisionType decision,
        String actorId,
        Instant decidedAt,
        String comment)
{

    /**
     * 从首次成功持久化的任务和决定构造响应结果。
     *
     * @param task 已进入终态的任务
     * @param decision 已保存的唯一决定
     * @return 稳定审批结果
     */
    public static DecideApprovalResult from(
            ApprovalTask task,
            ApprovalDecision decision)
    {
        return from(decision, task.version());
    }

    /**
     * 从幂等记录保存的最终任务版本和不可变决定恢复原结果。
     *
     * @param decision 已保存的唯一决定
     * @param taskVersion 首次执行后的任务版本
     * @return 稳定审批结果
     */
    public static DecideApprovalResult from(
            ApprovalDecision decision,
            long taskVersion)
    {
        return new DecideApprovalResult(
                decision.approvalTaskId(),
                ApprovalTaskStatus.valueOf(decision.decision().name()),
                taskVersion,
                decision.id(),
                decision.decision(),
                decision.actorId().value(),
                decision.decidedAt(),
                decision.comment());
    }
}
