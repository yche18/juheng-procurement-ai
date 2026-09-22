package io.github.yche18.juhengbackend.approval.domain;

import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 表示一份采购申请在 R1 中唯一的人工审批任务。
 */
public final class ApprovalTask
{

    private final UUID id;
    private final UUID procurementRequestId;
    private final UserId assigneeId;
    private final ApprovalTaskStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 为已完成路由的采购申请创建待处理审批任务，并再次阻止自审。
     *
     * @param id 任务标识
     * @param procurementRequestId 采购申请标识
     * @param requesterId 申请创建者
     * @param assigneeId 路由得到的审批人
     * @param createdAt 服务端创建时间
     * @return 状态为 PENDING、版本为 0 的审批任务
     */
    public static ApprovalTask createPending(
            UUID id,
            UUID procurementRequestId,
            UserId requesterId,
            UserId assigneeId,
            Instant createdAt)
    {
        Objects.requireNonNull(requesterId, "Requester ID must not be null");
        Objects.requireNonNull(assigneeId, "Assignee ID must not be null");
        if (requesterId.equals(assigneeId))
        {
            throw new IllegalArgumentException("Requester cannot approve their own request");
        }
        return new ApprovalTask(
                id,
                procurementRequestId,
                assigneeId,
                ApprovalTaskStatus.PENDING,
                0L,
                createdAt,
                createdAt);
    }

    /**
     * 构造审批任务并保护任务自身的不变量。
     */
    private ApprovalTask(
            UUID id,
            UUID procurementRequestId,
            UserId assigneeId,
            ApprovalTaskStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt)
    {
        this.id = Objects.requireNonNull(id, "Approval task ID must not be null");
        this.procurementRequestId = Objects.requireNonNull(
                procurementRequestId,
                "Procurement request ID must not be null");
        this.assigneeId = Objects.requireNonNull(assigneeId, "Assignee ID must not be null");
        this.status = Objects.requireNonNull(status, "Approval task status must not be null");
        if (version < 0)
        {
            throw new IllegalArgumentException("Approval task version must not be negative");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "Created time must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated time must not be null");
    }

    /**
     * 返回审批任务标识。
     *
     * @return 任务 UUID
     */
    public UUID id()
    {
        return id;
    }

    /**
     * 返回任务所属采购申请标识。
     *
     * @return 申请 UUID
     */
    public UUID procurementRequestId()
    {
        return procurementRequestId;
    }

    /**
     * 返回可信路由得到的审批人。
     *
     * @return 审批人 ID
     */
    public UserId assigneeId()
    {
        return assigneeId;
    }

    /**
     * 返回任务状态。
     *
     * @return 当前状态
     */
    public ApprovalTaskStatus status()
    {
        return status;
    }

    /**
     * 返回任务持久化版本。
     *
     * @return 版本号
     */
    public long version()
    {
        return version;
    }

    /**
     * 返回任务创建时间。
     *
     * @return UTC 时间点
     */
    public Instant createdAt()
    {
        return createdAt;
    }

    /**
     * 返回任务最后更新时间。
     *
     * @return UTC 时间点
     */
    public Instant updatedAt()
    {
        return updatedAt;
    }
}
