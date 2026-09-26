package io.github.yche18.juhengbackend.approval.web;

import io.github.yche18.juhengbackend.approval.application.FinalApprovalDecisionDetails;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;

import java.time.Instant;
import java.util.UUID;

/**
 * 对外公开的最终审批决定白名单响应。
 *
 * @param procurementRequestId 采购申请标识
 * @param approvalTaskId 审批任务标识
 * @param decisionId 最终决定标识
 * @param decision 决定类型
 * @param actorId 可信操作者标识
 * @param decidedAt 服务端决定时间
 * @param comment 决定意见；批准时可以为空
 */
public record FinalApprovalDecisionResponse(
        UUID procurementRequestId,
        UUID approvalTaskId,
        UUID decisionId,
        ApprovalDecisionType decision,
        String actorId,
        Instant decidedAt,
        String comment)
{

    /**
     * 将应用层只读投影映射为稳定的 API 白名单字段。
     *
     * @param details 最终审批决定只读投影
     * @return 最终审批决定响应
     */
    public static FinalApprovalDecisionResponse from(FinalApprovalDecisionDetails details)
    {
        return new FinalApprovalDecisionResponse(
                details.procurementRequestId(),
                details.approvalTaskId(),
                details.decisionId(),
                details.decision(),
                details.actorId(),
                details.decidedAt(),
                details.comment());
    }
}
