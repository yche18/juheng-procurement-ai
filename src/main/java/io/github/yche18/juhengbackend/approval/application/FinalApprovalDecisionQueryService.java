package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.common.error.ResourceNotFoundException;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.RoleAuthorizer;
import io.github.yche18.juhengbackend.identity.domain.Role;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 编排申请创建者或任务受理审批人查看最终审批决定的只读用例。
 */
@Service
@Profile("!no-database")
public class FinalApprovalDecisionQueryService
{

    private static final Set<Role> ALLOWED_ROLES = Set.of(Role.REQUESTER, Role.APPROVER);

    private final RoleAuthorizer roleAuthorizer;
    private final FinalApprovalDecisionQueryRepository queryRepository;

    /**
     * 创建最终审批决定查询服务。
     *
     * @param roleAuthorizer 角色授权器
     * @param queryRepository 带申请和任务数据范围的查询端口
     */
    public FinalApprovalDecisionQueryService(
            RoleAuthorizer roleAuthorizer,
            FinalApprovalDecisionQueryRepository queryRepository)
    {
        this.roleAuthorizer = roleAuthorizer;
        this.queryRepository = queryRepository;
    }

    /**
     * 校验业务角色后读取当前用户有权访问的唯一最终决定。
     *
     * <p>尚无决定、申请不存在与越权访问统一使用资源不存在语义，避免决定或申请枚举。</p>
     *
     * @param requestId 采购申请标识
     * @param currentUser 可信当前用户
     * @return 已保存的最终审批决定
     */
    @Transactional(readOnly = true)
    public FinalApprovalDecisionDetails getVisible(UUID requestId, CurrentUser currentUser)
    {
        Objects.requireNonNull(requestId, "Procurement request ID must not be null");
        Objects.requireNonNull(currentUser, "Current user must not be null");
        roleAuthorizer.requireAnyRole(currentUser, ALLOWED_ROLES);
        return queryRepository.findVisibleByRequestId(requestId, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Final approval decision is not visible in the current user scope"));
    }
}
