package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestDetails;

import java.time.Instant;
import java.util.UUID;

/**
 * 审批任务详情使用的只读模型。
 *
 * @param id 审批任务标识
 * @param assigneeId 任务受理审批人
 * @param status 审批任务状态
 * @param version 审批任务版本
 * @param createdAt 任务创建时间
 * @param updatedAt 任务更新时间
 * @param procurementRequest 完整采购申请详情
 */
public record ApprovalTaskDetails(
        UUID id,
        String assigneeId,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        ProcurementRequestDetails procurementRequest)
{
}
