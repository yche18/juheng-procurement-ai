package io.github.yche18.juhengbackend.audit.infrastructure.persistence;

import io.github.yche18.juhengbackend.audit.application.AuditTrailQueryRepository;
import io.github.yche18.juhengbackend.audit.domain.AuditEvent;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 使用 MyBatis 执行带申请创建者或任务受理人范围的审计轨迹查询。
 */
@Repository
@Profile("!no-database")
public class MyBatisAuditTrailQueryRepository implements AuditTrailQueryRepository
{

    private final AuditEventMapper auditEventMapper;

    /**
     * 创建审计轨迹查询适配器。
     *
     * @param auditEventMapper 审计事件 Mapper
     */
    public MyBatisAuditTrailQueryRepository(AuditEventMapper auditEventMapper)
    {
        this.auditEventMapper = auditEventMapper;
    }

    /**
     * 先按可信查看者确认申请或任务范围，命中后再执行同样带范围条件的事件查询。
     *
     * @param requestId 采购申请标识
     * @param viewerId 可信当前用户标识
     * @return 当前范围内存在时返回稳定排序的事件列表
     */
    @Override
    public Optional<List<AuditEvent>> findVisibleByRequestId(UUID requestId, UserId viewerId)
    {
        if (!auditEventMapper.existsVisibleRequest(requestId, viewerId.value()))
        {
            return Optional.empty();
        }
        List<AuditEvent> events = auditEventMapper.selectVisibleTrail(requestId, viewerId.value())
                .stream()
                .map(this::toDomain)
                .toList();
        return Optional.of(events);
    }

    /**
     * 将持久化对象转换为不可变审计事实。
     *
     * @param event 持久化审计事件
     * @return 领域审计事件
     */
    private AuditEvent toDomain(AuditEventDO event)
    {
        return new AuditEvent(
                event.id(),
                event.procurementRequestId(),
                new UserId(event.actorId()),
                event.action(),
                event.targetType(),
                event.targetId(),
                event.occurredAt(),
                event.result(),
                event.requestIdentifier());
    }
}
