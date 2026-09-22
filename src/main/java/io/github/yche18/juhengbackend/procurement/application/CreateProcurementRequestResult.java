package io.github.yche18.juhengbackend.procurement.application;

import io.github.yche18.juhengbackend.procurement.domain.ProcurementItem;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 创建采购草稿后返回给协议层的应用结果。
 *
 * @param id 申请标识
 * @param businessNumber 业务编号
 * @param creatorId 创建者
 * @param title 标题
 * @param purpose 采购目的
 * @param department 申请部门
 * @param expectedDeliveryDate 期望交付日期
 * @param currency 币种
 * @param estimatedTotal 服务端计算总额
 * @param status 状态
 * @param version 版本
 * @param createdAt 创建时间
 * @param items 采购项结果
 */
public record CreateProcurementRequestResult(
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
        List<ItemResult> items)
{

    /**
     * 从已持久化聚合生成稳定应用结果。
     *
     * @param request 采购申请聚合
     * @return 创建结果
     */
    public static CreateProcurementRequestResult from(ProcurementRequest request)
    {
        List<ItemResult> itemResults = request.items().stream()
                .map(ItemResult::from)
                .toList();
        return new CreateProcurementRequestResult(
                request.id(),
                request.businessNumber().value(),
                request.creatorId().value(),
                request.title(),
                request.purpose(),
                request.department(),
                request.expectedDeliveryDate(),
                request.currency().name(),
                request.estimatedTotal().amount(),
                request.status().name(),
                request.version(),
                request.createdAt(),
                itemResults);
    }

    /**
     * 创建结果中的采购项视图。
     *
     * @param id 采购项标识
     * @param lineNumber 行号
     * @param name 名称
     * @param categoryCode 品类
     * @param specification 规格
     * @param quantity 数量
     * @param unit 计量单位
     * @param estimatedUnitPrice 预计单价
     * @param estimatedLineTotal 服务端计算行金额
     */
    public record ItemResult(
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
         * 从领域采购项生成响应所需结果。
         *
         * @param item 领域采购项
         * @return 采购项结果
         */
        private static ItemResult from(ProcurementItem item)
        {
            return new ItemResult(
                    item.id(),
                    item.lineNumber(),
                    item.name(),
                    item.categoryCode().name(),
                    item.specification(),
                    item.quantity(),
                    item.unit(),
                    item.estimatedUnitPrice().amount(),
                    item.estimatedLineTotal().amount());
        }
    }
}
