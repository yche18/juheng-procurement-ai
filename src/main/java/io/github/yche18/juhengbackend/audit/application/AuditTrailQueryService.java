package io.github.yche18.juhengbackend.audit.application;

import io.github.yche18.juhengbackend.audit.domain.AuditEvent;
import io.github.yche18.juhengbackend.common.error.ResourceNotFoundException;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.RoleAuthorizer;
import io.github.yche18.juhengbackend.identity.domain.Role;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 编排申请创建者或任务受理审批人查看审计轨迹的只读用例。
 */
@Service
@Profile("!no-database")
public class AuditTrailQueryService
{

    private static final Set<Role> ALLOWED_ROLES = Set.of(Role.REQUESTER, Role.APPROVER);

    private final RoleAuthorizer roleAuthorizer;
    private final AuditTrailQueryRepository queryRepository;

    /**
     * 创建审计轨迹查询服务。
     *
     * @param roleAuthorizer 角色授权器
     * @param queryRepository 带申请和任务数据范围的查询端口
     */
    public AuditTrailQueryService(
            RoleAuthorizer roleAuthorizer,
            AuditTrailQueryRepository queryRepository)
    {
        this.roleAuthorizer = roleAuthorizer;
        this.queryRepository = queryRepository;
    }

    /**
     * 校验业务角色后读取当前用户有权访问的申请审计轨迹。
     *
     * @param requestId 采购申请标识
     * @param currentUser 可信当前用户
     * @return 按时间和事件标识稳定升序排列的审计事件
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> getVisible(UUID requestId, CurrentUser currentUser)
    {
        Objects.requireNonNull(requestId, "Procurement request ID must not be null");
        Objects.requireNonNull(currentUser, "Current user must not be null");
        roleAuthorizer.requireAnyRole(currentUser, ALLOWED_ROLES);
        return queryRepository.findVisibleByRequestId(requestId, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procurement audit trail is not visible in the current user scope"));
    }
}
