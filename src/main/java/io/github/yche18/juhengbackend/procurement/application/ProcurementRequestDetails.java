package io.github.yche18.juhengbackend.procurement.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 采购申请详情使用的只读模型。
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
 * @param items 按行号排序的采购项
 */
public record ProcurementRequestDetails(
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
        List<ItemDetails> items)
{

    /**
     * 复制采购项列表，保证查询结果创建后保持不可变。
     */
    public ProcurementRequestDetails
    {
        items = List.copyOf(items);
    }

    /**
     * 采购申请详情中的只读采购项。
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
    public record ItemDetails(
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
    }
}
