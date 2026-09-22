package io.github.yche18.juhengbackend.procurement.domain;

import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 维护采购草稿核心字段、采购项和金额不变量的聚合根。
 */
public final class ProcurementRequest
{

    private static final int MAX_ITEM_COUNT = 100;

    private final UUID id;
    private final BusinessNumber businessNumber;
    private final UserId creatorId;
    private final String title;
    private final String purpose;
    private final String department;
    private final LocalDate expectedDeliveryDate;
    private final Currency currency;
    private final Money estimatedTotal;
    private final ProcurementRequestStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<ProcurementItem> items;

    /**
     * 创建一个服务端控制的合法采购草稿。
     *
     * @param id 申请标识
     * @param businessNumber 稳定业务编号
     * @param creatorId 可信创建者
     * @param title 标题
     * @param purpose 采购目的
     * @param department 申请部门
     * @param expectedDeliveryDate 期望交付日期
     * @param items 至少一个采购项
     * @param createdAt 服务端创建时间
     * @return 状态为 DRAFT、币种为 CNY、版本为 0 的聚合
     */
    public static ProcurementRequest createDraft(
            UUID id,
            BusinessNumber businessNumber,
            UserId creatorId,
            String title,
            String purpose,
            String department,
            LocalDate expectedDeliveryDate,
            List<ProcurementItem> items,
            Instant createdAt)
    {
        return new ProcurementRequest(
                id,
                businessNumber,
                creatorId,
                title,
                purpose,
                department,
                expectedDeliveryDate,
                Currency.CNY,
                ProcurementRequestStatus.DRAFT,
                0,
                createdAt,
                createdAt,
                items);
    }

    /**
     * 构造采购申请并集中验证聚合级不变量。
     */
    private ProcurementRequest(
            UUID id,
            BusinessNumber businessNumber,
            UserId creatorId,
            String title,
            String purpose,
            String department,
            LocalDate expectedDeliveryDate,
            Currency currency,
            ProcurementRequestStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<ProcurementItem> items)
    {
        this.id = Objects.requireNonNull(id, "Procurement request ID must not be null");
        this.businessNumber = Objects.requireNonNull(businessNumber, "Business number must not be null");
        this.creatorId = Objects.requireNonNull(creatorId, "Creator ID must not be null");
        this.title = requiredText(title, "Title", 200);
        this.purpose = requiredText(purpose, "Purpose", 2000);
        this.department = requiredText(department, "Department", 100);
        this.expectedDeliveryDate = Objects.requireNonNull(
                expectedDeliveryDate,
                "Expected delivery date must not be null");
        this.currency = Objects.requireNonNull(currency, "Currency must not be null");
        this.status = Objects.requireNonNull(status, "Status must not be null");
        if (version < 0)
        {
            throw new IllegalArgumentException("Version must not be negative");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "Created time must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated time must not be null");
        if (items == null || items.isEmpty())
        {
            throw new IllegalArgumentException("Procurement request must contain at least one item");
        }
        if (items.size() > MAX_ITEM_COUNT)
        {
            throw new IllegalArgumentException("Procurement request must contain at most 100 items");
        }
        if (items.stream().anyMatch(Objects::isNull))
        {
            throw new IllegalArgumentException("Procurement request items must not contain null");
        }
        this.items = List.copyOf(items);
        this.estimatedTotal = calculateEstimatedTotal(this.items, currency);
    }

    /**
     * 返回申请标识。
     *
     * @return UUID 标识
     */
    public UUID id()
    {
        return id;
    }

    /**
     * 返回稳定业务编号。
     *
     * @return 业务编号
     */
    public BusinessNumber businessNumber()
    {
        return businessNumber;
    }

    /**
     * 返回可信创建者标识。
     *
     * @return 创建者 ID
     */
    public UserId creatorId()
    {
        return creatorId;
    }

    /**
     * 返回申请标题。
     *
     * @return 标题
     */
    public String title()
    {
        return title;
    }

    /**
     * 返回采购目的。
     *
     * @return 目的
     */
    public String purpose()
    {
        return purpose;
    }

    /**
     * 返回申请部门。
     *
     * @return 部门
     */
    public String department()
    {
        return department;
    }

    /**
     * 返回期望交付日期。
     *
     * @return 交付日期
     */
    public LocalDate expectedDeliveryDate()
    {
        return expectedDeliveryDate;
    }

    /**
     * 返回服务端控制的币种。
     *
     * @return CNY
     */
    public Currency currency()
    {
        return currency;
    }

    /**
     * 返回由采购项计算的预计总额。
     *
     * @return 总额
     */
    public Money estimatedTotal()
    {
        return estimatedTotal;
    }

    /**
     * 返回采购申请状态。
     *
     * @return 当前状态
     */
    public ProcurementRequestStatus status()
    {
        return status;
    }

    /**
     * 返回持久化版本。
     *
     * @return 版本号
     */
    public long version()
    {
        return version;
    }

    /**
     * 返回创建时间。
     *
     * @return UTC 时间点
     */
    public Instant createdAt()
    {
        return createdAt;
    }

    /**
     * 返回最后更新时间。
     *
     * @return UTC 时间点
     */
    public Instant updatedAt()
    {
        return updatedAt;
    }

    /**
     * 返回不可变的采购项集合。
     *
     * @return 采购项
     */
    public List<ProcurementItem> items()
    {
        return items;
    }

    /**
     * 汇总所有已经舍入的行金额，确保行金额与总额展示一致。
     *
     * @param items 采购项
     * @param currency 申请币种
     * @return 预计总额
     */
    private static Money calculateEstimatedTotal(List<ProcurementItem> items, Currency currency)
    {
        Money total = Money.zero(currency);
        for (ProcurementItem item : items)
        {
            total = total.add(item.estimatedLineTotal());
        }
        return total;
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
