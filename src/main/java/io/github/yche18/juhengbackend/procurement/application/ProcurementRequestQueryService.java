package io.github.yche18.juhengbackend.procurement.application;

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
 * 编排申请人查看自己采购申请的只读用例。
 */
@Service
@Profile("!no-database")
public class ProcurementRequestQueryService
{

    private final RoleAuthorizer roleAuthorizer;
    private final ProcurementRequestQueryRepository queryRepository;

    /**
     * 创建采购申请查询服务。
     *
     * @param roleAuthorizer 角色授权器
     * @param queryRepository 带数据范围的查询端口
     */
    public ProcurementRequestQueryService(
            RoleAuthorizer roleAuthorizer,
            ProcurementRequestQueryRepository queryRepository)
    {
        this.roleAuthorizer = roleAuthorizer;
        this.queryRepository = queryRepository;
    }

    /**
     * 校验 REQUESTER 角色后，仅查询可信当前用户创建的申请。
     *
     * @param query 分页与状态条件
     * @param currentUser 可信当前用户
     * @return 当前用户申请分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ProcurementRequestSummary> listOwn(
            ListOwnProcurementRequestsQuery query,
            CurrentUser currentUser)
    {
        Objects.requireNonNull(query, "List query must not be null");
        Objects.requireNonNull(currentUser, "Current user must not be null");
        roleAuthorizer.requireRole(currentUser, Role.REQUESTER);
        return queryRepository.findOwnedPage(currentUser.userId(), query);
    }

    /**
     * 按申请标识和可信创建者范围查询详情；越界访问与不存在使用同一失败结果。
     *
     * @param requestId 申请标识
     * @param currentUser 可信当前用户
     * @return 当前用户拥有的申请详情
     */
    @Transactional(readOnly = true)
    public ProcurementRequestDetails getOwn(UUID requestId, CurrentUser currentUser)
    {
        Objects.requireNonNull(requestId, "Procurement request ID must not be null");
        Objects.requireNonNull(currentUser, "Current user must not be null");
        roleAuthorizer.requireRole(currentUser, Role.REQUESTER);
        return queryRepository.findOwnedById(requestId, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procurement request is not visible in the current user scope"));
    }
}
