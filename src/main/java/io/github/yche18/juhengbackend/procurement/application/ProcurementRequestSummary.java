package io.github.yche18.juhengbackend.procurement.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 采购申请列表使用的只读摘要。
 *
 * @param id 申请标识
 * @param businessNumber 业务编号
 * @param title 标题
 * @param department 申请部门
 * @param expectedDeliveryDate 期望交付日期
 * @param currency 币种
 * @param estimatedTotal 预计总额
 * @param status 状态
 * @param version 版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ProcurementRequestSummary(
        UUID id,
        String businessNumber,
        String title,
        String department,
        LocalDate expectedDeliveryDate,
        String currency,
        BigDecimal estimatedTotal,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt)
{
}
