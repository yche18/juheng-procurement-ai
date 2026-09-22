package io.github.yche18.juhengbackend.audit.application;

import io.github.yche18.juhengbackend.audit.domain.AuditEvent;

/**
 * 在业务事务中只追加审计事件的持久化端口。
 */
public interface AuditEventStore
{

    /**
     * 追加一条不可变审计事实。
     *
     * @param event 审计事件
     */
    void append(AuditEvent event);
}
