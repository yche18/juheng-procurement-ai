package io.github.yche18.juhengbackend.procurement.domain;

import java.util.Locale;

/**
 * R1 合成演示数据使用的受控采购品类。
 */
public enum CategoryCode
{
    LAPTOP,
    MONITOR,
    OFFICE_CHAIR,
    SOFTWARE_LICENSE;

    public static final String SUPPORTED_PATTERN =
            "LAPTOP|MONITOR|OFFICE_CHAIR|SOFTWARE_LICENSE";

    /**
     * 把已经通过协议层校验的品类文本转换为领域枚举。
     *
     * @param value 品类文本
     * @return 对应受控品类
     */
    public static CategoryCode from(String value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException("Category code must not be null");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
