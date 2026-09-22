package io.github.yche18.juhengbackend.audit.infrastructure.persistence;

import io.github.yche18.juhengbackend.audit.application.AuditEventStore;
import io.github.yche18.juhengbackend.audit.domain.AuditEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 只追加审计事件的持久化适配器。
 */
@Repository
@Profile("!no-database")
public class MyBatisAuditEventStore implements AuditEventStore
{

    private final AuditEventMapper auditEventMapper;

    /**
     * 创建审计事件持久化适配器。
     *
     * @param auditEventMapper 审计 Mapper
     */
    public MyBatisAuditEventStore(AuditEventMapper auditEventMapper)
    {
        this.auditEventMapper = auditEventMapper;
    }

    /**
     * 插入一条审计事件，并拒绝静默的零行写入。
     *
     * @param event 审计事件
     */
    @Override
    public void append(AuditEvent event)
    {
        int inserted = auditEventMapper.insert(new AuditEventDO(
                event.id(),
                event.procurementRequestId(),
                event.actorId().value(),
                event.action(),
                event.targetType(),
                event.targetId(),
                event.occurredAt(),
                event.result(),
                event.requestIdentifier()));
        if (inserted != 1)
        {
            throw new IllegalStateException("Expected one audit event row to be inserted");
        }
    }
}
