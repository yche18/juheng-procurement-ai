package io.github.yche18.juhengbackend.procurement.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * `procurement_item` 表的 MyBatis-Plus 持久化映射。
 *
 * @param id 主键
 * @param procurementRequestId 所属申请
 * @param lineNo 行号
 * @param name 名称
 * @param categoryCode 品类
 * @param specification 规格
 * @param quantity 数量
 * @param unit 计量单位
 * @param estimatedUnitPrice 预计单价
 */
@TableName("procurement_item")
public record ProcurementItemDO(
        @TableId(value = "id", type = IdType.INPUT) UUID id,
        UUID procurementRequestId,
        int lineNo,
        String name,
        String categoryCode,
        String specification,
        BigDecimal quantity,
        String unit,
        BigDecimal estimatedUnitPrice)
{
}
