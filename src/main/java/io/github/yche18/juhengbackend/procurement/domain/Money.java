package io.github.yche18.juhengbackend.procurement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 使用十进制定点语义表示采购金额。
 *
 * @param amount 两位小数且非负的金额
 * @param currency 币种
 */
public record Money(BigDecimal amount, Currency currency)
{

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * 校验金额并规范为两位小数，禁止以隐式舍入接纳非法单价。
     */
    public Money
    {
        Objects.requireNonNull(amount, "Money amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        if (amount.signum() < 0)
        {
            throw new IllegalArgumentException("Money amount must not be negative");
        }
        try
        {
            amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        }
        catch (ArithmeticException exception)
        {
            throw new IllegalArgumentException("Money amount must have at most two decimal places", exception);
        }
        if (amount.precision() > 19)
        {
            throw new IllegalArgumentException("Money amount exceeds supported precision");
        }
    }

    /**
     * 创建指定币种的零金额。
     *
     * @param currency 币种
     * @return 两位小数的零金额
     */
    public static Money zero(Currency currency)
    {
        return new Money(BigDecimal.ZERO.setScale(SCALE), currency);
    }

    /**
     * 按数量计算行金额，并明确使用 HALF_UP 保留两位小数。
     *
     * @param quantity 正数量
     * @return 舍入后的行金额
     */
    public Money multiply(BigDecimal quantity)
    {
        Objects.requireNonNull(quantity, "Quantity must not be null");
        if (quantity.signum() <= 0)
        {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        BigDecimal lineAmount = amount.multiply(quantity).setScale(SCALE, ROUNDING_MODE);
        return new Money(lineAmount, currency);
    }

    /**
     * 把相同币种的金额相加。
     *
     * @param other 待相加金额
     * @return 相加后的新金额
     */
    public Money add(Money other)
    {
        Objects.requireNonNull(other, "Other money must not be null");
        if (currency != other.currency)
        {
            throw new IllegalArgumentException("Cannot add money with different currencies");
        }
        return new Money(amount.add(other.amount), currency);
    }
}
