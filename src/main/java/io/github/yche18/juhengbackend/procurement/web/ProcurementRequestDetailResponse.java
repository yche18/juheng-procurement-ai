package io.github.yche18.juhengbackend.procurement.web;

import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestDetails;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 当前申请人拥有的采购申请详情响应。
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
 * @param status 状态
 * @param version 版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param items 采购项
 */
public record ProcurementRequestDetailResponse(
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
     * 将应用详情映射为 HTTP 响应。
     *
     * @param details 应用详情
     * @return HTTP 详情响应
     */
    public static ProcurementRequestDetailResponse from(ProcurementRequestDetails details)
    {
        List<ItemResponse> items = details.items().stream()
                .map(ItemResponse::from)
                .toList();
        return new ProcurementRequestDetailResponse(
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

    /**
     * 详情响应中的采购项。
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
