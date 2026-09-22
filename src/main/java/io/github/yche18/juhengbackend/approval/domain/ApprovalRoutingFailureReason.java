package io.github.yche18.juhengbackend.approval.domain;

/**
 * 单审批人路由无法产生有效结果的确定性原因。
 */
public enum ApprovalRoutingFailureReason
{
    NO_APPROVER,
    MULTIPLE_APPROVERS,
    SELF_ASSIGNMENT
}
