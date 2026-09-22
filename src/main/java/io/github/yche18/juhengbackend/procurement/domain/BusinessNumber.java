package io.github.yche18.juhengbackend.procurement.domain;

import java.util.regex.Pattern;

/**
 * 面向用户展示且保持稳定的采购申请业务编号。
 *
 * @param value 形如 PR-yyyyMMdd-sequence 的编号
 */
public record BusinessNumber(String value)
{

    private static final Pattern FORMAT = Pattern.compile("PR-\\d{8}-\\d+");

    /**
     * 校验业务编号格式，阻止任意文本进入领域模型。
     */
    public BusinessNumber
    {
        if (value == null || !FORMAT.matcher(value).matches())
        {
            throw new IllegalArgumentException("Invalid procurement business number");
        }
    }

    /**
     * 返回适合日志和 API 使用的编号文本。
     *
     * @return 业务编号
     */
    @Override
    public String toString()
    {
        return value;
    }
}
