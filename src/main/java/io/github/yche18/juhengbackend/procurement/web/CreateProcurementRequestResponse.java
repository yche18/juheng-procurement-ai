package io.github.yche18.juhengbackend.procurement.web;

import io.github.yche18.juhengbackend.procurement.application.CreateProcurementRequestResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 创建采购草稿的 HTTP 响应。
 *
 * @param id 申请标识
 * @param businessNumber 业务编号
 * @param creatorId 可信创建者
 * @param title 标题
 * @param purpose 采购目的
 * @param department 申请部门
 * @param expectedDeliveryDate 期望交付日期
 * @param currency 服务端币种
 * @param estimatedTotal 服务端总额
 * @param status 服务端状态
 * @param version 初始版本
 * @param createdAt 创建时间
 * @param items 采购项
 */
public record CreateProcurementRequestResponse(
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
        List<ItemResponse> items)
{

    /**
     * 将应用结果映射为 HTTP 响应。
     *
     * @param result 创建用例结果
     * @return 创建响应
     */
    public static CreateProcurementRequestResponse from(CreateProcurementRequestResult result)
    {
        List<ItemResponse> itemResponses = result.items().stream()
                .map(ItemResponse::from)
                .toList();
        return new CreateProcurementRequestResponse(
                result.id(),
                result.businessNumber(),
                result.creatorId(),
                result.title(),
                result.purpose(),
                result.department(),
                result.expectedDeliveryDate(),
                result.currency(),
                result.estimatedTotal(),
                result.status(),
                result.version(),
                result.createdAt(),
                itemResponses);
    }

    /**
     * 创建响应中的采购项。
     *
     * @param id 采购项标识
     * @param lineNumber 行号
     * @param name 名称
     * @param categoryCode 品类
     * @param specification 规格
     * @param quantity 数量
     * @param unit 计量单位
     * @param estimatedUnitPrice 预计单价
     * @param estimatedLineTotal 服务端行金额
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
         * 将应用采购项结果映射为 HTTP 响应项。
         *
         * @param result 应用采购项结果
         * @return HTTP 采购项响应
         */
        private static ItemResponse from(CreateProcurementRequestResult.ItemResult result)
        {
            return new ItemResponse(
                    result.id(),
                    result.lineNumber(),
                    result.name(),
                    result.categoryCode(),
                    result.specification(),
                    result.quantity(),
                    result.unit(),
                    result.estimatedUnitPrice(),
                    result.estimatedLineTotal());
        }
    }
}
