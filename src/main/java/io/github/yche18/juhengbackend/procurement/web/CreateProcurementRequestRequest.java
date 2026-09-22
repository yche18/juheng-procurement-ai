package io.github.yche18.juhengbackend.procurement.web;

import io.github.yche18.juhengbackend.procurement.application.CreateProcurementRequestCommand;
import io.github.yche18.juhengbackend.procurement.domain.CategoryCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建采购草稿的 HTTP 请求，只暴露客户端允许填写的字段。
 *
 * @param title 标题
 * @param purpose 采购目的
 * @param department 申请部门
 * @param expectedDeliveryDate 期望交付日期
 * @param items 采购项
 */
public record CreateProcurementRequestRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String purpose,
        @NotBlank @Size(max = 100) String department,
        @NotNull LocalDate expectedDeliveryDate,
        @NotEmpty @Size(max = 100) List<@Valid ItemRequest> items)
{

    /**
     * 将已经通过协议校验的请求映射为应用命令。
     *
     * @return 创建采购申请命令
     */
    public CreateProcurementRequestCommand toCommand()
    {
        List<CreateProcurementRequestCommand.ItemCommand> itemCommands = items.stream()
                .map(ItemRequest::toCommand)
                .toList();
        return new CreateProcurementRequestCommand(
                title,
                purpose,
                department,
                expectedDeliveryDate,
                itemCommands);
    }

    /**
     * 创建采购项请求。
     *
     * @param name 名称
     * @param categoryCode 受控品类
     * @param specification 规格说明
     * @param quantity 正数量，最多四位小数
     * @param unit 计量单位
     * @param estimatedUnitPrice 非负预计单价，最多两位小数
     */
    public record ItemRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = CategoryCode.SUPPORTED_PATTERN) String categoryCode,
            @NotBlank @Size(max = 2000) String specification,
            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 7, fraction = 4)
            BigDecimal quantity,
            @NotBlank @Size(max = 40) String unit,
            @NotNull
            @DecimalMin("0")
            @Digits(integer = 8, fraction = 2)
            BigDecimal estimatedUnitPrice)
    {

        /**
         * 将采购项请求映射为应用命令。
         *
         * @return 采购项命令
         */
        private CreateProcurementRequestCommand.ItemCommand toCommand()
        {
            return new CreateProcurementRequestCommand.ItemCommand(
                    name,
                    categoryCode,
                    specification,
                    quantity,
                    unit,
                    estimatedUnitPrice);
        }
    }
}
