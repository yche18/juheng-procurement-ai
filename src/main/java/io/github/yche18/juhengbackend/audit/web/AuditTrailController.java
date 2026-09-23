package io.github.yche18.juhengbackend.audit.web;

import io.github.yche18.juhengbackend.audit.application.AuditTrailQueryService;
import io.github.yche18.juhengbackend.audit.domain.AuditEvent;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.CurrentUserProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 提供按采购申请查看授权审计轨迹的只读 REST 入口。
 */
@RestController
@RequestMapping("/api/procurement-requests/{requestId}/audit-events")
@Profile("!no-database")
public class AuditTrailController
{

    private final CurrentUserProvider currentUserProvider;
    private final AuditTrailQueryService queryService;

    /**
     * 创建审计轨迹查询 Controller。
     *
     * @param currentUserProvider 可信当前用户提供端口
     * @param queryService 审计轨迹查询服务
     */
    public AuditTrailController(
            CurrentUserProvider currentUserProvider,
            AuditTrailQueryService queryService)
    {
        this.currentUserProvider = currentUserProvider;
        this.queryService = queryService;
    }

    /**
     * 返回当前申请创建者或任务受理审批人有权查看的稳定审计轨迹。
     *
     * @param requestId 采购申请标识
     * @return 只包含白名单字段的审计轨迹响应
     */
    @GetMapping
    public ProcurementAuditTrailResponse getVisible(@PathVariable UUID requestId)
    {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        List<AuditEvent> events = queryService.getVisible(requestId, currentUser);
        return ProcurementAuditTrailResponse.from(requestId, events);
    }
}
