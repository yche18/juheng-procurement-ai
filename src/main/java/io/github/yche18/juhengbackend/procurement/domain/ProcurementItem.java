package io.github.yche18.juhengbackend.procurement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * 采购申请聚合内的一条采购项。
 */
public final class ProcurementItem
{

    private static final int QUANTITY_SCALE = 4;
    private static final int MAX_QUANTITY_INTEGER_DIGITS = 7;

    private final UUID id;
    private final int lineNumber;
    private final String name;
    private final CategoryCode categoryCode;
    private final String specification;
    private final BigDecimal quantity;
    private final String unit;
    private final Money estimatedUnitPrice;

    /**
     * 创建并校验采购项，保证数量、文本和单价满足领域不变量。
     *
     * @param id 采购项标识
     * @param lineNumber 申请内稳定行号
     * @param name 名称
     * @param categoryCode 受控品类
     * @param specification 规格说明
     * @param quantity 数量
     * @param unit 计量单位
     * @param estimatedUnitPrice 预计单价
     */
    public ProcurementItem(
            UUID id,
            int lineNumber,
            String name,
            CategoryCode categoryCode,
            String specification,
            BigDecimal quantity,
            String unit,
            Money estimatedUnitPrice)
    {
        this.id = Objects.requireNonNull(id, "Procurement item ID must not be null");
        if (lineNumber <= 0)
        {
            throw new IllegalArgumentException("Line number must be positive");
        }
        this.lineNumber = lineNumber;
        this.name = requiredText(name, "Item name", 200);
        this.categoryCode = Objects.requireNonNull(categoryCode, "Category code must not be null");
        this.specification = requiredText(specification, "Specification", 2000);
        this.quantity = normalizeQuantity(quantity);
        this.unit = requiredText(unit, "Unit", 40);
        this.estimatedUnitPrice = Objects.requireNonNull(
                estimatedUnitPrice,
                "Estimated unit price must not be null");
        if (estimatedUnitPrice.currency() != Currency.CNY)
        {
            throw new IllegalArgumentException("R1 procurement item currency must be CNY");
        }
        if (estimatedUnitPrice.amount().precision() - estimatedUnitPrice.amount().scale() > 8)
        {
            throw new IllegalArgumentException("Estimated unit price exceeds supported range");
        }
    }

    /**
     * 返回采购项标识。
     *
     * @return UUID 标识
     */
    public UUID id()
    {
        return id;
    }

    /**
     * 返回申请内行号。
     *
     * @return 正整数行号
     */
    public int lineNumber()
    {
        return lineNumber;
    }

    /**
     * 返回采购项名称。
     *
     * @return 名称
     */
    public String name()
    {
        return name;
    }

    /**
     * 返回受控品类。
     *
     * @return 品类编码
     */
    public CategoryCode categoryCode()
    {
        return categoryCode;
    }

    /**
     * 返回规格说明。
     *
     * @return 规格说明
     */
    public String specification()
    {
        return specification;
    }

    /**
     * 返回规范化为四位小数的数量。
     *
     * @return 数量
     */
    public BigDecimal quantity()
    {
        return quantity;
    }

    /**
     * 返回计量单位。
     *
     * @return 单位
     */
    public String unit()
    {
        return unit;
    }

    /**
     * 返回预计单价。
     *
     * @return 单价
     */
    public Money estimatedUnitPrice()
    {
        return estimatedUnitPrice;
    }

    /**
     * 根据数量和单价实时计算两位小数的行金额。
     *
     * @return 行金额
     */
    public Money estimatedLineTotal()
    {
        return estimatedUnitPrice.multiply(quantity);
    }

    /**
     * 校验并规范数量，数据库和 Java 统一使用四位小数。
     *
     * @param value 原始数量
     * @return 四位小数数量
     */
    private static BigDecimal normalizeQuantity(BigDecimal value)
    {
        Objects.requireNonNull(value, "Quantity must not be null");
        if (value.signum() <= 0)
        {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        BigDecimal normalized;
        try
        {
            normalized = value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        }
        catch (ArithmeticException exception)
        {
            throw new IllegalArgumentException("Quantity must have at most four decimal places", exception);
        }
        if (normalized.precision() - normalized.scale() > MAX_QUANTITY_INTEGER_DIGITS)
        {
            throw new IllegalArgumentException("Quantity exceeds supported range");
        }
        return normalized;
    }

    /**
     * 校验、去除首尾空白并限制必填领域文本长度。
     *
     * @param value 原始文本
     * @param fieldName 字段名称
     * @param maxLength 最大长度
     * @return 规范化文本
     */
    private static String requiredText(String value, String fieldName, int maxLength)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength)
        {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length");
        }
        return normalized;
    }
}
