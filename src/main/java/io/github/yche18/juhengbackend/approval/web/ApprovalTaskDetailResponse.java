package io.github.yche18.juhengbackend.approval.web;

import io.github.yche18.juhengbackend.approval.application.ApprovalTaskDetails;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestDetails;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 当前审批人拥有的审批任务详情响应。
 *
 * @param id 任务标识
 * @param assigneeId 受理审批人
 * @param status 任务状态
 * @param version 任务版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param procurementRequest 完整采购申请详情
 */
public record ApprovalTaskDetailResponse(
        UUID id,
        String assigneeId,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        ProcurementRequestResponse procurementRequest)
{

    /**
     * 将应用详情映射为 HTTP 响应。
     *
     * @param details 应用任务详情
     * @return HTTP 任务详情
     */
    public static ApprovalTaskDetailResponse from(ApprovalTaskDetails details)
    {
        return new ApprovalTaskDetailResponse(
                details.id(),
                details.assigneeId(),
                details.status(),
                details.version(),
                details.createdAt(),
                details.updatedAt(),
                ProcurementRequestResponse.from(details.procurementRequest()));
    }

    /**
     * 审批任务详情中的完整采购申请响应。
     *
     * @param id 申请标识
     * @param businessNumber 业务编号
     * @param creatorId 创建者
     * @param title 标题
     * @param purpose 采购目的
     * @param department 申请部门
     * @param expectedDeliveryDate 期望交付日期
     * @param currency 币种
     * @param estimatedTotal 预计总额
     * @param status 申请状态
     * @param version 申请版本
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @param items 按行号排序的采购项
     */
    public record ProcurementRequestResponse(
            UUID id,
            String businessNumber,
            String creatorId,
            String title,
            String purpose,
            String department,
            LocalDate expectedDeliveryDate,
            String currency,
            BigDecimal estimatedTotal,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<ItemResponse> items)
    {

        /**
         * 将应用申请详情映射为审批任务 HTTP 响应。
         *
         * @param details 应用申请详情
         * @return 审批任务中的申请响应
         */
        private static ProcurementRequestResponse from(ProcurementRequestDetails details)
        {
            List<ItemResponse> items = details.items().stream()
                    .map(ItemResponse::from)
                    .toList();
            return new ProcurementRequestResponse(
                    details.id(),
                    details.businessNumber(),
                    details.creatorId(),
                    details.title(),
                    details.purpose(),
                    details.department(),
                    details.expectedDeliveryDate(),
                    details.currency(),
                    details.estimatedTotal(),
                    details.status(),
                    details.version(),
                    details.createdAt(),
                    details.updatedAt(),
                    items);
        }
    }

    /**
     * 审批任务申请详情中的采购项响应。
     *
     * @param id 采购项标识
     * @param lineNumber 行号
     * @param name 名称
     * @param categoryCode 品类编码
     * @param specification 规格
     * @param quantity 数量
     * @param unit 计量单位
     * @param estimatedUnitPrice 预计单价
     * @param estimatedLineTotal 服务端计算的行金额
     */
    public record ItemResponse(
            UUID id,
            int lineNumber,
            String name,
            String categoryCode,
            String specification,
            BigDecimal quantity,
            String unit,
            BigDecimal estimatedUnitPrice,
            BigDecimal estimatedLineTotal)
    {

        /**
         * 将应用采购项详情映射为 HTTP 响应项。
         *
         * @param item 应用采购项详情
         * @return HTTP 采购项
         */
        private static ItemResponse from(ProcurementRequestDetails.ItemDetails item)
        {
            return new ItemResponse(
                    item.id(),
                    item.lineNumber(),
                    item.name(),
                    item.categoryCode(),
                    item.specification(),
                    item.quantity(),
                    item.unit(),
                    item.estimatedUnitPrice(),
                    item.estimatedLineTotal());
        }
    }
}
