package io.github.yche18.juhengbackend.procurement.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * `procurement_request` 表的 MyBatis-Plus 持久化映射。
 *
 * @param id 主键
 * @param businessNumber 业务编号
 * @param creatorId 创建者
 * @param title 标题
 * @param purpose 采购目的
 * @param department 部门
 * @param expectedDeliveryDate 期望交付日期
 * @param currency 币种
 * @param estimatedTotal 预计总额
 * @param status 状态
 * @param version 版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
@TableName("procurement_request")
public record ProcurementRequestDO(
        @TableId(value = "id", type = IdType.INPUT) UUID id,
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
        Instant updatedAt)
{
}
