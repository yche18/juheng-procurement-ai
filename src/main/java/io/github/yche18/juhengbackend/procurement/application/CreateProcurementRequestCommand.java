package io.github.yche18.juhengbackend.procurement.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建采购草稿用例接受的客户端可控字段。
 *
 * @param title 标题
 * @param purpose 采购目的
 * @param department 申请部门
 * @param expectedDeliveryDate 期望交付日期
 * @param items 采购项
 */
public record CreateProcurementRequestCommand(
        String title,
        String purpose,
        String department,
        LocalDate expectedDeliveryDate,
        List<ItemCommand> items)
{

    /**
     * 创建命令时复制采购项集合，避免调用方在用例执行中修改输入。
     */
    public CreateProcurementRequestCommand
    {
        items = items == null ? null : List.copyOf(items);
    }

    /**
     * 单个采购项的创建命令。
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
