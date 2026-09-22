package io.github.yche18.juhengbackend.audit.domain;

import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 只追加的 R1 业务审计事实。
 *
 * @param id 审计事件标识
 * @param procurementRequestId 所属采购申请范围
 * @param actorId 可信操作者
 * @param action 动作
 * @param targetType 直接目标类型
 * @param targetId 直接目标标识
 * @param occurredAt 服务端发生时间
 * @param result 动作结果
 * @param requestIdentifier 请求标识
 */
public record AuditEvent(
        UUID id,
        UUID procurementRequestId,
        UserId actorId,
        String action,
        String targetType,
        UUID targetId,
        Instant occurredAt,
        String result,
        String requestIdentifier)
{

    private static final String CREATED_ACTION = "PROCUREMENT_REQUEST_CREATED";
    private static final String UPDATED_ACTION = "PROCUREMENT_REQUEST_UPDATED";
    private static final String SUBMITTED_ACTION = "PROCUREMENT_REQUEST_SUBMITTED";
    private static final String TASK_ASSIGNED_ACTION = "APPROVAL_TASK_ASSIGNED";
    private static final String REQUEST_TARGET = "PROCUREMENT_REQUEST";
    private static final String TASK_TARGET = "APPROVAL_TASK";
    private static final String SUCCESS_RESULT = "SUCCESS";

    /**
     * 校验审计事实的所有必需字段。
     */
    public AuditEvent
    {
        Objects.requireNonNull(id, "Audit event ID must not be null");
        Objects.requireNonNull(procurementRequestId, "Procurement request ID must not be null");
        Objects.requireNonNull(actorId, "Audit actor must not be null");
        action = requiredText(action, "Audit action");
        targetType = requiredText(targetType, "Audit target type");
        Objects.requireNonNull(targetId, "Audit target ID must not be null");
        Objects.requireNonNull(occurredAt, "Audit time must not be null");
        result = requiredText(result, "Audit result");
        requestIdentifier = requiredText(requestIdentifier, "Request identifier");
    }

    /**
     * 创建采购申请草稿成功时的审计事件。
     *
     * @param requestId 采购申请标识
     * @param actorId 可信创建者
     * @param occurredAt 服务端创建时间
     * @param requestIdentifier 服务端请求标识
     * @return 创建成功审计事件
     */
    public static AuditEvent procurementRequestCreated(
            UUID requestId,
            UserId actorId,
            Instant occurredAt,
            String requestIdentifier)
    {
        return new AuditEvent(
                UUID.randomUUID(),
                requestId,
                actorId,
                CREATED_ACTION,
                REQUEST_TARGET,
                requestId,
                occurredAt,
                SUCCESS_RESULT,
                requestIdentifier);
    }

    /**
     * 创建采购申请草稿修改成功时的审计事件。
     *
     * @param requestId 采购申请标识
     * @param actorId 可信修改者
     * @param occurredAt 服务端修改时间
     * @param requestIdentifier 服务端请求标识
     * @return 修改成功审计事件
     */
    public static AuditEvent procurementRequestUpdated(
            UUID requestId,
            UserId actorId,
            Instant occurredAt,
            String requestIdentifier)
    {
        return new AuditEvent(
                UUID.randomUUID(),
                requestId,
                actorId,
                UPDATED_ACTION,
                REQUEST_TARGET,
                requestId,
                occurredAt,
                SUCCESS_RESULT,
                requestIdentifier);
    }

    /**
     * 创建采购申请提交成功时的审计事件。
     *
     * @param requestId 采购申请标识
     * @param actorId 可信申请人
     * @param occurredAt 服务端提交时间
     * @param requestIdentifier 幂等键
     * @return 提交成功审计事件
     */
    public static AuditEvent procurementRequestSubmitted(
            UUID requestId,
            UserId actorId,
            Instant occurredAt,
            String requestIdentifier)
    {
        return new AuditEvent(
                UUID.randomUUID(),
                requestId,
                actorId,
                SUBMITTED_ACTION,
                REQUEST_TARGET,
                requestId,
                occurredAt,
                SUCCESS_RESULT,
                requestIdentifier);
    }

    /**
     * 创建审批任务分配成功时的审计事件。
     *
     * @param requestId 所属采购申请标识
     * @param taskId 审批任务标识
     * @param actorId 触发任务创建的可信申请人
     * @param occurredAt 服务端分配时间
     * @param requestIdentifier 幂等键
     * @return 任务分配成功审计事件
     */
    public static AuditEvent approvalTaskAssigned(
            UUID requestId,
            UUID taskId,
            UserId actorId,
            Instant occurredAt,
            String requestIdentifier)
    {
        return new AuditEvent(
                UUID.randomUUID(),
                requestId,
                actorId,
                TASK_ASSIGNED_ACTION,
                TASK_TARGET,
                taskId,
                occurredAt,
                SUCCESS_RESULT,
                requestIdentifier);
    }

    /**
     * 校验审计文本必填且去除首尾空白。
     *
     * @param value 原始值
     * @param fieldName 字段名称
     * @return 规范化值
     */
    private static String requiredText(String value, String fieldName)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
