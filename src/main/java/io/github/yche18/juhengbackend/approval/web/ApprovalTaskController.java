package io.github.yche18.juhengbackend.approval.web;

import io.github.yche18.juhengbackend.approval.application.ApprovalTaskDetails;
import io.github.yche18.juhengbackend.approval.application.ApprovalTaskQueryService;
import io.github.yche18.juhengbackend.approval.application.ListAssignedApprovalTasksQuery;
import io.github.yche18.juhengbackend.approval.domain.ApprovalTaskStatus;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.CurrentUserProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 提供当前审批人任务列表与详情的 REST 入口。
 */
@RestController
@RequestMapping("/api/approval-tasks")
@Profile("!no-database")
public class ApprovalTaskController
{

    private final CurrentUserProvider currentUserProvider;
    private final ApprovalTaskQueryService queryService;

    /**
     * 创建审批任务查询 Controller。
     *
     * @param currentUserProvider 可信当前用户提供端口
     * @param queryService 审批任务查询服务
     */
    public ApprovalTaskController(
            CurrentUserProvider currentUserProvider,
            ApprovalTaskQueryService queryService)
    {
        this.currentUserProvider = currentUserProvider;
        this.queryService = queryService;
    }

    /**
     * 分页查看分配给可信当前审批人的任务，可按任务状态精确筛选。
     *
     * @param page 从零开始的页码
     * @param size 单页大小
     * @param status 可选 R1 任务状态
     * @return 当前审批人任务的分页响应
     */
    @GetMapping
    public ApprovalTaskPageResponse listAssigned(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(ListAssignedApprovalTasksQuery.MAX_PAGE_SIZE) int size,
            @RequestParam(required = false)
            @Pattern(regexp = ApprovalTaskStatus.SUPPORTED_PATTERN) String status)
    {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        ApprovalTaskStatus statusFilter = status == null
                ? null
                : ApprovalTaskStatus.valueOf(status);
        ListAssignedApprovalTasksQuery query = new ListAssignedApprovalTasksQuery(
                page,
                size,
                statusFilter);
        return ApprovalTaskPageResponse.from(queryService.listAssigned(query, currentUser));
    }

    /**
     * 查看分配给可信当前审批人的单个任务及完整采购申请详情。
     *
     * @param taskId 审批任务标识
     * @return 审批任务及采购申请详情
     */
    @GetMapping("/{taskId}")
    public ApprovalTaskDetailResponse getAssigned(@PathVariable UUID taskId)
    {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        ApprovalTaskDetails details = queryService.getAssigned(taskId, currentUser);
        return ApprovalTaskDetailResponse.from(details);
    }
}
