package io.github.yche18.juhengbackend.approval.domain;

import io.github.yche18.juhengbackend.common.error.BusinessConflictException;
import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;
import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 表示一份采购申请在 R1 中唯一的人工审批任务及其可选最终决定。
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
    private final ApprovalDecision decision;

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
                createdAt,
                null);
    }

    /**
     * 从可信持久化快照恢复审批任务及其可选最终决定。
     *
     * @param id 任务标识
     * @param procurementRequestId 所属申请标识
     * @param assigneeId 受理审批人
     * @param status 当前状态
     * @param version 当前版本
     * @param createdAt 创建时间
     * @param updatedAt 最后更新时间
     * @param decision 已存在的最终决定；待处理任务为空
     * @return 已恢复并重新校验的审批任务
     */
    public static ApprovalTask restore(
            UUID id,
            UUID procurementRequestId,
            UserId assigneeId,
            ApprovalTaskStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt,
            ApprovalDecision decision)
    {
        return new ApprovalTask(
                id,
                procurementRequestId,
                assigneeId,
                status,
                version,
                createdAt,
                updatedAt,
                decision);
    }

    /**
     * 校验受理人、任务状态和客户端版本后生成批准终态及唯一决定。
     *
     * @param expectedVersion 客户端读取到的任务版本
     * @param actorId 可信当前审批人
     * @param decidedAt 服务端决定时间
     * @param comment 可选批准意见
     * @return 包含批准决定的新任务快照
     */
    public ApprovalTask approve(
            long expectedVersion,
            UserId actorId,
            Instant decidedAt,
            String comment)
    {
        return decide(expectedVersion, actorId, decidedAt, ApprovalDecisionType.APPROVED, comment);
    }

    /**
     * 校验受理人、任务状态、客户端版本和驳回原因后生成驳回终态及唯一决定。
     *
     * @param expectedVersion 客户端读取到的任务版本
     * @param actorId 可信当前审批人
     * @param decidedAt 服务端决定时间
     * @param reason 非空驳回原因
     * @return 包含驳回决定的新任务快照
     */
    public ApprovalTask reject(
            long expectedVersion,
            UserId actorId,
            Instant decidedAt,
            String reason)
    {
        return decide(expectedVersion, actorId, decidedAt, ApprovalDecisionType.REJECTED, reason);
    }

    /**
     * 构造审批任务并保护任务、状态和最终决定之间的不变量。
     */
    private ApprovalTask(
            UUID id,
            UUID procurementRequestId,
            UserId assigneeId,
            ApprovalTaskStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt,
            ApprovalDecision decision)
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
        validateDecision(status, decision, id);
        this.decision = decision;
    }

    /** 返回审批任务标识。 */
    public UUID id()
    {
        return id;
    }

    /** 返回任务所属采购申请标识。 */
    public UUID procurementRequestId()
    {
        return procurementRequestId;
    }

    /** 返回可信路由得到的审批人。 */
    public UserId assigneeId()
    {
        return assigneeId;
    }

    /** 返回任务状态。 */
    public ApprovalTaskStatus status()
    {
        return status;
    }

    /** 返回任务持久化版本。 */
    public long version()
    {
        return version;
    }

    /** 返回任务创建时间。 */
    public Instant createdAt()
    {
        return createdAt;
    }

    /** 返回任务最后更新时间。 */
    public Instant updatedAt()
    {
        return updatedAt;
    }

    /**
     * 返回任务已经形成的最终决定；待处理任务没有决定。
     *
     * @return 可选最终决定
     */
    public Optional<ApprovalDecision> decision()
    {
        return Optional.ofNullable(decision);
    }

    /**
     * 执行批准或驳回共有的授权、状态、版本和时间校验并创建下一版本。
     */
    private ApprovalTask decide(
            long expectedVersion,
            UserId actorId,
            Instant decidedAt,
            ApprovalDecisionType decisionType,
            String comment)
    {
        UserId trustedActor = Objects.requireNonNull(actorId, "Approval actor must not be null");
        if (!assigneeId.equals(trustedActor))
        {
            throw new IllegalArgumentException("Only the assigned approver can decide this task");
        }
        if (status != ApprovalTaskStatus.PENDING)
        {
            throw new BusinessConflictException("Only a pending approval task can be decided");
        }
        if (expectedVersion != version)
        {
            throw new ConcurrentUpdateException(
                    "Expected approval task version " + expectedVersion
                            + " but current version is " + version);
        }
        long nextVersion;
        try
        {
            nextVersion = Math.addExact(version, 1L);
        }
        catch (ArithmeticException exception)
        {
            throw new ConcurrentUpdateException("Approval task version cannot be incremented");
        }
        Instant trustedTime = Objects.requireNonNull(decidedAt, "Decision time must not be null");
        ApprovalDecision finalDecision = new ApprovalDecision(
                UUID.randomUUID(), id, decisionType, trustedActor, trustedTime, comment);
        return new ApprovalTask(
                id,
                procurementRequestId,
                assigneeId,
                ApprovalTaskStatus.valueOf(decisionType.name()),
                nextVersion,
                createdAt,
                trustedTime,
                finalDecision);
    }

    /**
     * 保证待处理任务没有决定，而终态任务恰好携带同类型的唯一决定。
     */
    private static void validateDecision(
            ApprovalTaskStatus status,
            ApprovalDecision decision,
            UUID taskId)
    {
        if (status == ApprovalTaskStatus.PENDING)
        {
            if (decision != null)
            {
                throw new IllegalArgumentException("Pending approval task must not have a decision");
            }
            return;
        }
        if (decision == null)
        {
            throw new IllegalArgumentException("Terminal approval task must have a decision");
        }
        if (!taskId.equals(decision.approvalTaskId()))
        {
            throw new IllegalArgumentException("Approval decision belongs to another task");
        }
        if (!status.name().equals(decision.decision().name()))
        {
            throw new IllegalArgumentException("Approval task status and decision must match");
        }
    }
}
