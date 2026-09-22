package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.common.application.PageResult;
import io.github.yche18.juhengbackend.common.error.ResourceNotFoundException;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.RoleAuthorizer;
import io.github.yche18.juhengbackend.identity.domain.Role;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * 编排审批人查看分配给自己任务的只读用例。
 */
@Service
@Profile("!no-database")
public class ApprovalTaskQueryService
{

    private final RoleAuthorizer roleAuthorizer;
    private final ApprovalTaskQueryRepository queryRepository;

    /**
     * 创建审批任务查询服务。
     *
     * @param roleAuthorizer 角色授权器
     * @param queryRepository 带审批人数据范围的查询端口
     */
    public ApprovalTaskQueryService(
            RoleAuthorizer roleAuthorizer,
            ApprovalTaskQueryRepository queryRepository)
    {
        this.roleAuthorizer = roleAuthorizer;
        this.queryRepository = queryRepository;
    }

    /**
     * 校验 APPROVER 角色后，仅查询分配给可信当前用户的审批任务。
     *
     * @param query 分页与状态条件
     * @param currentUser 可信当前用户
     * @return 当前审批人任务分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ApprovalTaskSummary> listAssigned(
            ListAssignedApprovalTasksQuery query,
            CurrentUser currentUser)
    {
        Objects.requireNonNull(query, "List query must not be null");
        Objects.requireNonNull(currentUser, "Current user must not be null");
        roleAuthorizer.requireRole(currentUser, Role.APPROVER);
        return queryRepository.findAssignedPage(currentUser.userId(), query);
    }

    /**
     * 按任务标识和可信审批人范围查询详情，越界访问与不存在使用同一失败结果。
     *
     * @param taskId 审批任务标识
     * @param currentUser 可信当前用户
     * @return 当前审批人拥有的任务详情
     */
    @Transactional(readOnly = true)
    public ApprovalTaskDetails getAssigned(UUID taskId, CurrentUser currentUser)
    {
        Objects.requireNonNull(taskId, "Approval task ID must not be null");
        Objects.requireNonNull(currentUser, "Current user must not be null");
        roleAuthorizer.requireRole(currentUser, Role.APPROVER);
        return queryRepository.findAssignedById(taskId, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approval task is not visible in the current user scope"));
    }
}
