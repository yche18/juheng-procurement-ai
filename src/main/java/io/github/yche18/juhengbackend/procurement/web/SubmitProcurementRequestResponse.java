package io.github.yche18.juhengbackend.procurement.web;

import io.github.yche18.juhengbackend.procurement.application.SubmitProcurementRequestResult;

import java.util.UUID;

/**
 * 采购申请提交结果响应。
 *
 * @param requestId 申请标识
 * @param requestStatus 提交后的申请状态
 * @param requestVersion 提交后的申请版本
 * @param approvalTaskId 审批任务标识
 * @param approvalTaskStatus 审批任务状态
 */
public record SubmitProcurementRequestResponse(
        UUID requestId,
        String requestStatus,
        long requestVersion,
        UUID approvalTaskId,
        String approvalTaskStatus)
{

    /**
     * 将应用结果映射为稳定 HTTP 响应。
     *
     * @param result 提交应用结果
     * @return HTTP 响应对象
     */
    public static SubmitProcurementRequestResponse from(SubmitProcurementRequestResult result)
    {
        return new SubmitProcurementRequestResponse(
                result.requestId(),
                result.requestStatus().name(),
                result.requestVersion(),
                result.approvalTaskId(),
                result.approvalTaskStatus().name());
    }
}
