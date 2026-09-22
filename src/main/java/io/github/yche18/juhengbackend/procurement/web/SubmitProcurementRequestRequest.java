package io.github.yche18.juhengbackend.procurement.web;

import io.github.yche18.juhengbackend.procurement.application.SubmitProcurementRequestCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 提交采购申请的 HTTP 请求体。
 *
 * @param version 客户端读取到的草稿版本
 */
public record SubmitProcurementRequestRequest(
        @NotNull @PositiveOrZero Long version)
{

    /**
     * 将已通过协议校验的请求转换为应用命令。
     *
     * @return 提交命令
     */
    public SubmitProcurementRequestCommand toCommand()
    {
        return new SubmitProcurementRequestCommand(version);
    }
}
