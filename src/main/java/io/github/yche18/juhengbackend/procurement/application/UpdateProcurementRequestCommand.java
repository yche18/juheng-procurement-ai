package io.github.yche18.juhengbackend.procurement.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 修改采购草稿用例接受的版本和完整可编辑字段快照。
 *
 * @param version 客户端读取到的当前版本
 * @param title 标题
 * @param purpose 采购目的
 * @param department 申请部门
 * @param expectedDeliveryDate 期望交付日期
 * @param items 完整采购项快照
 */
public record UpdateProcurementRequestCommand(
        long version,
        String title,
        String purpose,
        String department,
        LocalDate expectedDeliveryDate,
        List<ItemCommand> items)
{

    /**
     * 创建命令时复制采购项集合，防止调用方在事务执行中改变输入。
     */
    public UpdateProcurementRequestCommand
    {
        items = items == null ? null : List.copyOf(items);
    }

    /**
     * 修改请求中的单个采购项快照。
     *
     * @param name 名称
     * @param categoryCode 受控品类文本
     * @param specification 规格
     * @param quantity 数量
     * @param unit 计量单位
     * @param estimatedUnitPrice 预计单价
     */
    public record ItemCommand(
            String name,
            String categoryCode,
            String specification,
            BigDecimal quantity,
            String unit,
            BigDecimal estimatedUnitPrice)
    {
    }
}
