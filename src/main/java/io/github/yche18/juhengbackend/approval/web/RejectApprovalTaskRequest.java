package io.github.yche18.juhengbackend.approval.web;

import io.github.yche18.juhengbackend.approval.application.DecideApprovalCommand;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 人工驳回审批任务的 HTTP 请求体。
 *
 * @param approvalTaskVersion 客户端读取到的任务版本
 * @param comment 必填驳回原因
 */
public record RejectApprovalTaskRequest(
        @NotNull @PositiveOrZero Long approvalTaskVersion,
        @NotBlank @Size(max = 2000) String comment)
{

    /**
     * 将已通过协议校验的请求转换为驳回命令。
     *
     * @return 明确驳回命令
     */
    public DecideApprovalCommand toCommand()
    {
        return new DecideApprovalCommand(
                ApprovalDecisionType.REJECTED,
                approvalTaskVersion,
                comment);
    }
}
