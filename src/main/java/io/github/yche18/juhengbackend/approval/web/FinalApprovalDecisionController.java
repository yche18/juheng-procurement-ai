package io.github.yche18.juhengbackend.approval.web;

import io.github.yche18.juhengbackend.approval.application.FinalApprovalDecisionDetails;
import io.github.yche18.juhengbackend.approval.application.FinalApprovalDecisionQueryService;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.CurrentUserProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 提供按采购申请查看最终审批决定的只读 REST 入口。
 */
@RestController
@RequestMapping("/api/procurement-requests/{requestId}/approval-decision")
@Profile("!no-database")
public class FinalApprovalDecisionController
{

    private final CurrentUserProvider currentUserProvider;
    private final FinalApprovalDecisionQueryService queryService;

    /**
     * 创建最终审批决定查询 Controller。
     *
     * @param currentUserProvider 可信当前用户提供端口
     * @param queryService 最终审批决定查询服务
     */
    public FinalApprovalDecisionController(
            CurrentUserProvider currentUserProvider,
            FinalApprovalDecisionQueryService queryService)
    {
        this.currentUserProvider = currentUserProvider;
        this.queryService = queryService;
    }

    /**
     * 返回当前申请创建者或任务受理审批人可见的唯一最终决定。
     *
     * @param requestId 采购申请标识
     * @return 只包含白名单字段的最终审批决定响应
     */
    @GetMapping
    public FinalApprovalDecisionResponse getVisible(@PathVariable UUID requestId)
    {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        FinalApprovalDecisionDetails details = queryService.getVisible(requestId, currentUser);
        return FinalApprovalDecisionResponse.from(details);
    }
}
