package io.github.yche18.juhengbackend.audit.application;

import io.github.yche18.juhengbackend.audit.domain.AuditEvent;
import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 按申请创建者或任务受理人范围读取审计轨迹的查询端口。
 */
public interface AuditTrailQueryRepository
{

    /**
     * 在数据库查询中直接限定查看者范围，并按发生时间与事件标识稳定排序。
     *
     * @param requestId 采购申请标识
     * @param viewerId 可信当前用户标识
     * @return 当前用户有权查看时返回审计事件列表，否则返回空 Optional
     */
    Optional<List<AuditEvent>> findVisibleByRequestId(UUID requestId, UserId viewerId);
}
