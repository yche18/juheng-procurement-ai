package io.github.yche18.juhengbackend.approval.web;

import io.github.yche18.juhengbackend.approval.application.DecideApprovalResult;

import java.time.Instant;
import java.util.UUID;

/**
 * 人工批准或驳回成功后的稳定 HTTP 响应。
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
public record ApprovalDecisionResponse(
        UUID approvalTaskId,
        String approvalTaskStatus,
        long approvalTaskVersion,
        UUID decisionId,
        String decision,
        String actorId,
        Instant decidedAt,
        String comment)
{

    /**
     * 将应用结果映射为稳定协议响应。
     *
     * @param result 审批应用结果
     * @return HTTP 响应对象
     */
    public static ApprovalDecisionResponse from(DecideApprovalResult result)
    {
        return new ApprovalDecisionResponse(
                result.approvalTaskId(),
                result.approvalTaskStatus().name(),
                result.approvalTaskVersion(),
                result.decisionId(),
                result.decision().name(),
                result.actorId(),
                result.decidedAt(),
                result.comment());
    }
}
