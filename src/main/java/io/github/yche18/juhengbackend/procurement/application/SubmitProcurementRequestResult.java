package io.github.yche18.juhengbackend.procurement.application;

import io.github.yche18.juhengbackend.approval.domain.ApprovalTaskStatus;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequestStatus;

import java.util.UUID;

/**
 * 提交成功或幂等重放时返回的稳定业务结果。
 *
 * @param requestId 采购申请标识
 * @param requestStatus 提交后的申请状态
 * @param requestVersion 提交后的申请版本
 * @param approvalTaskId 审批任务标识
 * @param approvalTaskStatus 创建后的任务状态
 */
public record SubmitProcurementRequestResult(
        UUID requestId,
        ProcurementRequestStatus requestStatus,
        long requestVersion,
        UUID approvalTaskId,
        ApprovalTaskStatus approvalTaskStatus)
{
}
