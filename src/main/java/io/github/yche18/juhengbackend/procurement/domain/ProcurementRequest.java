package io.github.yche18.juhengbackend.procurement.domain;

import io.github.yche18.juhengbackend.common.error.BusinessConflictException;
import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;
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
     * 从可信持久化快照恢复采购申请聚合，并重新验证聚合级不变量。
     *
     * @param id 申请标识
     * @param businessNumber 稳定业务编号
     * @param creatorId 创建者
     * @param title 标题
     * @param purpose 采购目的
     * @param department 申请部门
     * @param expectedDeliveryDate 期望交付日期
     * @param currency 币种
     * @param status 当前状态
     * @param version 当前持久化版本
     * @param createdAt 创建时间
     * @param updatedAt 最后更新时间
     * @param items 当前采购项快照
     * @return 已恢复的采购申请聚合
     */
    public static ProcurementRequest restore(
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
        return new ProcurementRequest(
                id,
                businessNumber,
                creatorId,
                title,
                purpose,
                department,
                expectedDeliveryDate,
                currency,
                status,
                version,
                createdAt,
                updatedAt,
                items);
    }

    /**
     * 校验草稿状态和调用方版本后，用完整的新快照生成下一个申请版本。
     *
     * <p>该行为不修改当前实例；Repository 随后仍需使用旧版本执行条件更新，
     * 防止读取完成后发生的数据库并发覆盖。</p>
     *
     * @param expectedVersion 客户端读取到的旧版本
     * @param title 新标题
     * @param purpose 新采购目的
     * @param department 新申请部门
     * @param expectedDeliveryDate 新期望交付日期
     * @param items 完整的新采购项快照
     * @param updatedAt 服务端更新时间
     * @return 版本递增且总额已重新计算的新聚合快照
     */
    public ProcurementRequest updateDraft(
            long expectedVersion,
            String title,
            String purpose,
            String department,
            LocalDate expectedDeliveryDate,
            List<ProcurementItem> items,
            Instant updatedAt)
    {
        if (status != ProcurementRequestStatus.DRAFT)
        {
            throw new BusinessConflictException(
                    "Only a draft procurement request can be updated");
        }
        if (expectedVersion != version)
        {
            throw new ConcurrentUpdateException(
                    "Expected procurement request version " + expectedVersion
                            + " but current version is " + version);
        }
        long nextVersion;
        try
        {
            nextVersion = Math.addExact(version, 1L);
        }
        catch (ArithmeticException exception)
        {
            throw new ConcurrentUpdateException(
                    "Procurement request version cannot be incremented");
        }
        return new ProcurementRequest(
                id,
                businessNumber,
                creatorId,
                title,
                purpose,
                department,
                expectedDeliveryDate,
                currency,
                status,
                nextVersion,
                createdAt,
                Objects.requireNonNull(updatedAt, "Updated time must not be null"),
                items);
    }

    /**
     * 校验申请仍是客户端读取的完整草稿，并生成已提交的新版本快照。
     *
     * <p>该行为只改变采购申请自身状态；审批路由和任务创建由 Application
     * 在同一事务中协调。</p>
     *
     * @param expectedVersion 客户端读取到的草稿版本
     * @param submittedAt 服务端提交时间
     * @return 状态为 SUBMITTED 且版本递增的新聚合快照
     */
    public ProcurementRequest submit(long expectedVersion, Instant submittedAt)
    {
        if (status != ProcurementRequestStatus.DRAFT)
        {
            throw new BusinessConflictException(
                    "Only a draft procurement request can be submitted");
        }
        if (expectedVersion != version)
        {
            throw new ConcurrentUpdateException(
                    "Expected procurement request version " + expectedVersion
                            + " but current version is " + version);
        }
        long nextVersion;
        try
        {
            nextVersion = Math.addExact(version, 1L);
        }
        catch (ArithmeticException exception)
        {
            throw new ConcurrentUpdateException(
                    "Procurement request version cannot be incremented");
        }
        return new ProcurementRequest(
                id,
                businessNumber,
                creatorId,
                title,
                purpose,
                department,
                expectedDeliveryDate,
                currency,
                ProcurementRequestStatus.SUBMITTED,
                nextVersion,
                createdAt,
                Objects.requireNonNull(submittedAt, "Submitted time must not be null"),
                items);
    }

    /**
     * 将已提交申请转换为人工批准终态，并生成新的持久化版本。
     *
     * @param approvedAt 服务端批准时间
     * @return 状态为 APPROVED 的新聚合快照
     */
    public ProcurementRequest markApproved(Instant approvedAt)
    {
        return markDecided(ProcurementRequestStatus.APPROVED, approvedAt);
    }

    /**
     * 将已提交申请转换为人工驳回终态，并生成新的持久化版本。
     *
     * @param rejectedAt 服务端驳回时间
     * @return 状态为 REJECTED 的新聚合快照
     */
    public ProcurementRequest markRejected(Instant rejectedAt)
    {
        return markDecided(ProcurementRequestStatus.REJECTED, rejectedAt);
    }

    /**
     * 执行申请最终审批共有的状态、时间和版本转换。
     *
     * @param terminalStatus 批准或驳回终态
     * @param decidedAt 服务端决定时间
     * @return 已进入审批终态的新聚合快照
     */
    private ProcurementRequest markDecided(
            ProcurementRequestStatus terminalStatus,
            Instant decidedAt)
    {
        if (status != ProcurementRequestStatus.SUBMITTED)
        {
            throw new BusinessConflictException(
                    "Only a submitted procurement request can be decided");
        }
        if (terminalStatus != ProcurementRequestStatus.APPROVED
                && terminalStatus != ProcurementRequestStatus.REJECTED)
        {
            throw new IllegalArgumentException("Approval terminal status is invalid");
        }
        long nextVersion;
        try
        {
            nextVersion = Math.addExact(version, 1L);
        }
        catch (ArithmeticException exception)
        {
            throw new ConcurrentUpdateException(
                    "Procurement request version cannot be incremented");
        }
        return new ProcurementRequest(
                id,
                businessNumber,
                creatorId,
                title,
                purpose,
                department,
                expectedDeliveryDate,
                currency,
                terminalStatus,
                nextVersion,
                createdAt,
                Objects.requireNonNull(decidedAt, "Decision time must not be null"),
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
