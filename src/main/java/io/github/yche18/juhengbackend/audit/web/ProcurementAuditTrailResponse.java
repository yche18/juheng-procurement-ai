package io.github.yche18.juhengbackend.audit.web;

import io.github.yche18.juhengbackend.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 单个采购申请的只读审计轨迹响应。
 *
 * @param procurementRequestId 采购申请标识
 * @param events 按发生时间和事件标识稳定升序排列的事件
 */
public record ProcurementAuditTrailResponse(
        UUID procurementRequestId,
        List<AuditEventResponse> events)
{

    /**
     * 将领域审计事实映射为仅包含允许公开字段的响应。
     *
     * @param requestId 采购申请标识
     * @param events 有序审计事实
     * @return 审计轨迹响应
     */
    public static ProcurementAuditTrailResponse from(UUID requestId, List<AuditEvent> events)
    {
        return new ProcurementAuditTrailResponse(
                requestId,
                events.stream().map(AuditEventResponse::from).toList());
    }

    /**
     * 对外公开的单条审计事件白名单结构。
     *
     * @param id 审计事件标识
     * @param actorId 可信操作者标识
     * @param action 业务动作
     * @param targetType 目标类型
     * @param targetId 目标标识
     * @param timestamp 服务端发生时间
     * @param result 动作结果
     * @param requestIdentifier 请求或幂等标识
     */
    public record AuditEventResponse(
            UUID id,
            String actorId,
            String action,
            String targetType,
            UUID targetId,
            Instant timestamp,
            String result,
            String requestIdentifier)
    {

        /**
         * 将领域审计事件映射为不包含凭据、异常或内部载荷的白名单字段。
         *
         * @param event 领域审计事件
         * @return API 审计事件
         */
        private static AuditEventResponse from(AuditEvent event)
        {
            return new AuditEventResponse(
                    event.id(),
                    event.actorId().value(),
                    event.action(),
                    event.targetType(),
                    event.targetId(),
                    event.occurredAt(),
                    event.result(),
                    event.requestIdentifier());
        }
    }
}
