package io.github.yche18.juhengbackend.procurement.application;

import io.github.yche18.juhengbackend.approval.application.ApprovalRoutingPolicy;
import io.github.yche18.juhengbackend.approval.application.ApprovalTaskRepository;
import io.github.yche18.juhengbackend.approval.domain.ApprovalRoutingResult;
import io.github.yche18.juhengbackend.approval.domain.ApprovalTask;
import io.github.yche18.juhengbackend.approval.domain.ApprovalTaskStatus;
import io.github.yche18.juhengbackend.audit.application.AuditEventStore;
import io.github.yche18.juhengbackend.audit.domain.AuditEvent;
import io.github.yche18.juhengbackend.common.error.ApprovalRoutingException;
import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;
import io.github.yche18.juhengbackend.common.error.ResourceNotFoundException;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.RoleAuthorizer;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.idempotency.application.IdempotencyAcquisition;
import io.github.yche18.juhengbackend.idempotency.application.IdempotencyResultReference;
import io.github.yche18.juhengbackend.idempotency.application.IdempotencyStore;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequest;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequestStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * 编排采购申请提交、审批路由、幂等、任务创建与审计的应用用例。
 */
@Service
@Profile("!no-database")
public class SubmitProcurementRequestService
{

    private static final String OPERATION = "SUBMIT_PROCUREMENT_REQUEST";
    private static final String REQUEST_TARGET = "PROCUREMENT_REQUEST";
    private static final String TASK_RESULT = "APPROVAL_TASK";

    private final RoleAuthorizer roleAuthorizer;
    private final IdempotencyStore idempotencyStore;
    private final ProcurementRequestRepository requestRepository;
    private final ApprovalRoutingPolicy routingPolicy;
    private final ApprovalTaskRepository taskRepository;
    private final AuditEventStore auditEventStore;
    private final Clock clock;

    /**
     * 创建采购申请提交服务。
     *
     * @param roleAuthorizer 角色授权器
     * @param idempotencyStore 幂等持久化端口
     * @param requestRepository 申请聚合持久化端口
     * @param routingPolicy 审批路由策略
     * @param taskRepository 审批任务持久化端口
     * @param auditEventStore 审计追加端口
     * @param clock 服务端时钟
     */
    public SubmitProcurementRequestService(
            RoleAuthorizer roleAuthorizer,
            IdempotencyStore idempotencyStore,
            ProcurementRequestRepository requestRepository,
            ApprovalRoutingPolicy routingPolicy,
            ApprovalTaskRepository taskRepository,
            AuditEventStore auditEventStore,
            Clock clock)
    {
        this.roleAuthorizer = roleAuthorizer;
        this.idempotencyStore = idempotencyStore;
        this.requestRepository = requestRepository;
        this.routingPolicy = routingPolicy;
        this.taskRepository = taskRepository;
        this.auditEventStore = auditEventStore;
        this.clock = clock;
    }

    /**
     * 在一个本地事务中执行提交的全部确定性副作用，或重放首次成功结果。
     *
     * @param requestId 待提交申请标识
     * @param command 客户端读取到的版本
     * @param idempotencyKey 幂等键
     * @param currentUser 可信当前用户
     * @return 首次提交或重放的相同结果
     */
    @Transactional
    public SubmitProcurementRequestResult submit(
            UUID requestId,
            SubmitProcurementRequestCommand command,
            String idempotencyKey,
            CurrentUser currentUser)
    {
        Objects.requireNonNull(requestId, "Procurement request ID must not be null");
        Objects.requireNonNull(command, "Submit command must not be null");
        Objects.requireNonNull(currentUser, "Current user must not be null");
        String normalizedKey = requiredIdempotencyKey(idempotencyKey);
        roleAuthorizer.requireRole(currentUser, Role.REQUESTER);

        Instant now = clock.instant();
        IdempotencyAcquisition acquisition = idempotencyStore.acquire(
                currentUser.userId(),
                OPERATION,
                REQUEST_TARGET,
                requestId,
                normalizedKey,
                fingerprint(command),
                now);
        if (!acquisition.acquired())
        {
            return replay(requestId, acquisition.replayResult());
        }

        ProcurementRequest current = requestRepository
                .findOwnedById(requestId, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procurement request is not visible in the current user scope"));
        ProcurementRequest submitted = current.submit(command.version(), now);
        ApprovalRoutingResult routingResult = routingPolicy.resolveAssignee(current.creatorId());
        if (!routingResult.successful())
        {
            throw new ApprovalRoutingException(
                    "Approval routing failed: " + routingResult.failureReason());
        }

        ApprovalTask task = ApprovalTask.createPending(
                UUID.randomUUID(),
                requestId,
                current.creatorId(),
                routingResult.assigneeId(),
                now);
        if (!requestRepository.submitConditionally(submitted, command.version()))
        {
            throw new ConcurrentUpdateException(
                    "Procurement request changed after it was loaded for submission");
        }
        taskRepository.save(task);
        auditEventStore.append(AuditEvent.procurementRequestSubmitted(
                requestId,
                currentUser.userId(),
                now,
                normalizedKey));
        auditEventStore.append(AuditEvent.approvalTaskAssigned(
                requestId,
                task.id(),
                currentUser.userId(),
                now,
                normalizedKey));
        idempotencyStore.complete(
                acquisition.recordId(),
                new IdempotencyResultReference(TASK_RESULT, task.id(), submitted.version()),
                now);

        return new SubmitProcurementRequestResult(
                requestId,
                submitted.status(),
                submitted.version(),
                task.id(),
                task.status());
    }

    /**
     * 从可信幂等结果引用重建首次提交响应。
     *
     * @param requestId 请求目标申请标识
     * @param reference 首次执行结果引用
     * @return 与首次成功语义一致的提交结果
     */
    private SubmitProcurementRequestResult replay(
            UUID requestId,
            IdempotencyResultReference reference)
    {
        if (!TASK_RESULT.equals(reference.referenceType()))
        {
            throw new IllegalStateException("Unexpected idempotency result reference type");
        }
        return new SubmitProcurementRequestResult(
                requestId,
                ProcurementRequestStatus.SUBMITTED,
                reference.targetVersion(),
                reference.referenceId(),
                ApprovalTaskStatus.PENDING);
    }

    /**
     * 对影响提交语义的规范化命令字段计算稳定 SHA-256 指纹。
     *
     * @param command 提交命令
     * @return 64 位小写十六进制摘要
     */
    private String fingerprint(SubmitProcurementRequestCommand command)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    ("version=" + command.version()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * 校验并规范化 API 传入的幂等键。
     *
     * @param value 原始幂等键
     * @return 去除首尾空白后的键
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
