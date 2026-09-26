package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;

import java.time.Instant;
import java.util.UUID;

/**
 * 当前查看者范围内已经保存的最终审批决定只读投影。
 *
 * @param procurementRequestId 采购申请标识
 * @param approvalTaskId 审批任务标识
 * @param decisionId 最终决定标识
 * @param decision 决定类型
 * @param actorId 可信操作者标识
 * @param decidedAt 服务端决定时间
 * @param comment 决定意见；批准时可为空
 */
public record FinalApprovalDecisionDetails(
        UUID procurementRequestId,
        UUID approvalTaskId,
        UUID decisionId,
        ApprovalDecisionType decision,
        String actorId,
        Instant decidedAt,
        String comment)
{
}
