package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.approval.domain.ApprovalDecision;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;
import io.github.yche18.juhengbackend.approval.domain.ApprovalTask;
import io.github.yche18.juhengbackend.audit.application.AuditEventStore;
import io.github.yche18.juhengbackend.audit.domain.AuditEvent;
import io.github.yche18.juhengbackend.common.error.AuthorizationDeniedException;
import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;
import io.github.yche18.juhengbackend.common.error.ResourceNotFoundException;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.RoleAuthorizer;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.idempotency.application.IdempotencyAcquisition;
import io.github.yche18.juhengbackend.idempotency.application.IdempotencyResultReference;
import io.github.yche18.juhengbackend.idempotency.application.IdempotencyStore;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestRepository;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * 在一个本地事务中协调人工决定、双聚合终态、审计和幂等结果。
 */
@Service
@Profile("!no-database")
public class DecideApprovalApplicationService
{

    private static final Logger LOGGER = LoggerFactory.getLogger(
            DecideApprovalApplicationService.class);
    private static final String OPERATION = "DECIDE_APPROVAL";
    private static final String TASK_TARGET = "APPROVAL_TASK";
    private static final String DECISION_RESULT = "APPROVAL_DECISION";

    private final RoleAuthorizer roleAuthorizer;
    private final IdempotencyStore idempotencyStore;
    private final ApprovalTaskRepository taskRepository;
    private final ProcurementRequestRepository requestRepository;
    private final AuditEventStore auditEventStore;
    private final Clock clock;

    /**
     * 创建人工审批应用服务。
     *
     * @param roleAuthorizer 角色授权器
     * @param idempotencyStore 幂等持久化端口
     * @param taskRepository 审批任务持久化端口
     * @param requestRepository 采购申请持久化端口
     * @param auditEventStore 审计追加端口
     * @param clock 服务端时钟
     */
    public DecideApprovalApplicationService(
            RoleAuthorizer roleAuthorizer,
            IdempotencyStore idempotencyStore,
            ApprovalTaskRepository taskRepository,
            ProcurementRequestRepository requestRepository,
            AuditEventStore auditEventStore,
            Clock clock)
    {
        this.roleAuthorizer = roleAuthorizer;
        this.idempotencyStore = idempotencyStore;
        this.taskRepository = taskRepository;
        this.requestRepository = requestRepository;
        this.auditEventStore = auditEventStore;
        this.clock = clock;
    }

    /**
     * 执行一次明确的人工批准或驳回，或者安全重放首次成功结果。
     *
     * @param taskId 审批任务标识
     * @param command 明确决定及任务版本
     * @param idempotencyKey 客户端幂等键
     * @param currentUser 可信当前用户
     * @return 首次成功或重放的同一决定
     */
    @Transactional
    public DecideApprovalResult decide(
            UUID taskId,
            DecideApprovalCommand command,
            String idempotencyKey,
            CurrentUser currentUser)
    {
        Objects.requireNonNull(taskId, "Approval task ID must not be null");
        Objects.requireNonNull(command, "Approval decision command must not be null");
        Objects.requireNonNull(currentUser, "Current user must not be null");
        String normalizedKey = requiredIdempotencyKey(idempotencyKey);
        roleAuthorizer.requireRole(currentUser, Role.APPROVER);

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        IdempotencyAcquisition acquisition = idempotencyStore.acquire(
                currentUser.userId(),
                OPERATION,
                TASK_TARGET,
                taskId,
                normalizedKey,
                fingerprint(command),
                now);
        if (!acquisition.acquired())
        {
            return replay(taskId, currentUser, acquisition.replayResult());
        }

        ApprovalTask currentTask = taskRepository
                .findAssignedById(taskId, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approval task is not visible in the current user scope"));
        ProcurementRequest currentRequest = requestRepository
                .findById(currentTask.procurementRequestId())
                .orElseThrow(() -> new IllegalStateException(
                        "Approval task references a missing procurement request"));
        if (currentRequest.creatorId().equals(currentUser.userId()))
        {
            LOGGER.warn("Self-approval denied actorId={} taskId={}",
                    currentUser.userId().value(), taskId);
            throw new AuthorizationDeniedException(
                    "Requester cannot approve their own procurement request");
        }

        ApprovalTask decidedTask = decideTask(currentTask, command, currentUser, now);
        ApprovalDecision decision = decidedTask.decision().orElseThrow();
        ProcurementRequest decidedRequest = command.decision() == ApprovalDecisionType.APPROVED
                ? currentRequest.markApproved(now)
                : currentRequest.markRejected(now);

        if (!taskRepository.saveDecisionConditionally(
                decidedTask,
                command.approvalTaskVersion()))
        {
            throw new ConcurrentUpdateException(
                    "Approval task changed after it was loaded for decision");
        }
        if (!requestRepository.saveTerminalStateConditionally(
                decidedRequest,
                currentRequest.version()))
        {
            throw new ConcurrentUpdateException(
                    "Procurement request changed after it was loaded for decision");
        }
        auditEventStore.append(createAuditEvent(
                command.decision(),
                currentRequest.id(),
                taskId,
                currentUser,
                now,
                normalizedKey));
        idempotencyStore.complete(
                acquisition.recordId(),
                new IdempotencyResultReference(
                        DECISION_RESULT,
                        decision.id(),
                        decidedTask.version()),
                now);
        return DecideApprovalResult.from(decidedTask, decision);
    }

    /**
     * 调用聚合中与明确决定对应的状态转换。
     */
    private ApprovalTask decideTask(
            ApprovalTask task,
            DecideApprovalCommand command,
            CurrentUser currentUser,
            Instant now)
    {
        if (command.decision() == ApprovalDecisionType.APPROVED)
        {
            return task.approve(
                    command.approvalTaskVersion(),
                    currentUser.userId(),
                    now,
                    command.comment());
        }
        return task.reject(
                command.approvalTaskVersion(),
                currentUser.userId(),
                now,
                command.comment());
    }

    /**
     * 创建与批准或驳回类型匹配的成功审计事件。
     */
    private AuditEvent createAuditEvent(
            ApprovalDecisionType decision,
            UUID requestId,
            UUID taskId,
            CurrentUser currentUser,
            Instant now,
            String idempotencyKey)
    {
        if (decision == ApprovalDecisionType.APPROVED)
        {
            return AuditEvent.approvalTaskApproved(
                    requestId, taskId, currentUser.userId(), now, idempotencyKey);
        }
        return AuditEvent.approvalTaskRejected(
                requestId, taskId, currentUser.userId(), now, idempotencyKey);
    }

    /**
     * 从不可变决定和幂等结果版本恢复首次响应，并校验引用仍属于当前作用域。
     */
    private DecideApprovalResult replay(
            UUID taskId,
            CurrentUser currentUser,
            IdempotencyResultReference reference)
    {
        if (!DECISION_RESULT.equals(reference.referenceType()))
        {
            throw new IllegalStateException("Unexpected idempotency result reference type");
        }
        ApprovalDecision decision = taskRepository
                .findDecisionById(reference.referenceId())
                .orElseThrow(() -> new IllegalStateException(
                        "Completed idempotency record references a missing approval decision"));
        if (!taskId.equals(decision.approvalTaskId())
                || !currentUser.userId().equals(decision.actorId()))
        {
            throw new IllegalStateException(
                    "Completed idempotency record references a decision outside its scope");
        }
        return DecideApprovalResult.from(decision, reference.targetVersion());
    }

    /**
     * 对决定类型、任务版本和规范化意见计算稳定 SHA-256 指纹。
     */
    private String fingerprint(DecideApprovalCommand command)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String comment = command.comment() == null ? "" : command.comment();
            String payload = "decision=" + command.decision().name()
                    + "\nversion=" + command.approvalTaskVersion()
                    + "\ncommentLength=" + comment.length()
                    + "\ncomment=" + comment;
            return HexFormat.of().formatHex(
                    digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * 校验并规范化 API 传入的幂等键。
     */
    private String requiredIdempotencyKey(String value)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > 64)
        {
            throw new IllegalArgumentException("Idempotency key exceeds maximum length");
        }
        return normalized;
    }
}
