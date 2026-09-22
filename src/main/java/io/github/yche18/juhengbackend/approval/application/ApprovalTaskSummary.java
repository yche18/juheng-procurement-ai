package io.github.yche18.juhengbackend.approval.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 审批任务列表使用的只读摘要。
 *
 * @param id 审批任务标识
 * @param status 审批任务状态
 * @param version 审批任务版本
 * @param createdAt 任务创建时间
 * @param updatedAt 任务更新时间
 * @param procurementRequest 任务关联的采购申请摘要
 */
public record ApprovalTaskSummary(
        UUID id,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        ProcurementRequestSummary procurementRequest)
{

    /**
     * 审批任务列表中的采购申请摘要。
     *
     * @param id 采购申请标识
     * @param businessNumber 业务编号
     * @param creatorId 申请创建者
     * @param title 申请标题
     * @param department 申请部门
     * @param expectedDeliveryDate 期望交付日期
     * @param currency 币种
     * @param estimatedTotal 预计总额
     * @param status 申请状态
     * @param version 申请版本
     */
    public record ProcurementRequestSummary(
            UUID id,
            String businessNumber,
            String creatorId,
            String title,
            String department,
            LocalDate expectedDeliveryDate,
            String currency,
            BigDecimal estimatedTotal,
            String status,
            long version)
    {
    }
}
