package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.common.application.PageResult;
import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * 按可信审批人范围读取审批任务的查询端口。
 */
public interface ApprovalTaskQueryRepository
{

    /**
     * 在数据库任务查询中直接限定受理审批人、可选状态和分页范围。
     *
     * @param assigneeId 可信审批人标识
     * @param query 分页和状态条件
     * @return 当前审批人范围内的稳定排序分页结果
     */
    PageResult<ApprovalTaskSummary> findAssignedPage(
            UserId assigneeId,
            ListAssignedApprovalTasksQuery query);

    /**
     * 使用任务标识和可信审批人共同定位任务及其申请详情。
     *
     * @param taskId 审批任务标识
     * @param assigneeId 可信审批人标识
     * @return 当前审批人范围内存在时返回详情
     */
    Optional<ApprovalTaskDetails> findAssignedById(UUID taskId, UserId assigneeId);
}
