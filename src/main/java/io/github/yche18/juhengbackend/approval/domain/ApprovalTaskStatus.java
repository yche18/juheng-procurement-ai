package io.github.yche18.juhengbackend.approval.domain;

/**
 * R1 人工审批任务生命周期状态。
 */
public enum ApprovalTaskStatus
{
    PENDING,
    APPROVED,
    REJECTED;

    public static final String SUPPORTED_PATTERN = "PENDING|APPROVED|REJECTED";
}
