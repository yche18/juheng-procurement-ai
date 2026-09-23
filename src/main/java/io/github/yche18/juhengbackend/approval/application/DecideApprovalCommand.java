package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;

import java.util.Objects;

/**
 * 审批人明确提交的批准或驳回命令。
 *
 * @param decision 决定类型
 * @param approvalTaskVersion 客户端读取到的任务版本
 * @param comment 可选批准意见或必填驳回原因
 */
public record DecideApprovalCommand(
        ApprovalDecisionType decision,
        long approvalTaskVersion,
        String comment)
{

    /**
     * 校验版本和驳回原因，并把意见规范化为稳定的幂等载荷。
     */
    public DecideApprovalCommand
    {
        Objects.requireNonNull(decision, "Approval decision type must not be null");
        if (approvalTaskVersion < 0)
        {
            throw new IllegalArgumentException("Approval task version must not be negative");
        }
        comment = normalizeComment(comment);
        if (decision == ApprovalDecisionType.REJECTED && comment == null)
        {
            throw new IllegalArgumentException("Rejection reason must not be blank");
        }
    }

    /**
     * 将空白批准意见转换为空值，其余意见去除首尾空白。
     *
     * @param value 原始意见
     * @return 规范化意见
     */
    private static String normalizeComment(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
