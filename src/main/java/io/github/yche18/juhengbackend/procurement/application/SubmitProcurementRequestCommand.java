package io.github.yche18.juhengbackend.procurement.application;

/**
 * 提交采购申请所需的客户端并发版本。
 *
 * @param version 客户端读取到的草稿版本
 */
public record SubmitProcurementRequestCommand(long version)
{

    /**
     * 拒绝不可能存在的负版本。
     */
    public SubmitProcurementRequestCommand
    {
        if (version < 0)
        {
            throw new IllegalArgumentException("Version must not be negative");
        }
    }
}
