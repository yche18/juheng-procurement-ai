package io.github.yche18.juhengbackend.approval.web;

import io.github.yche18.juhengbackend.approval.application.DecideApprovalCommand;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 人工批准审批任务的 HTTP 请求体。
 *
 * @param approvalTaskVersion 客户端读取到的任务版本
 * @param comment 可选批准意见
 */
public record ApproveApprovalTaskRequest(
        @NotNull @PositiveOrZero Long approvalTaskVersion,
        @Size(max = 2000) String comment)
{

    /**
     * 将已通过协议校验的请求转换为批准命令。
     *
     * @return 明确批准命令
     */
    public DecideApprovalCommand toCommand()
    {
        return new DecideApprovalCommand(
                ApprovalDecisionType.APPROVED,
                approvalTaskVersion,
                comment);
    }
}
