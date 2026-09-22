package io.github.yche18.juhengbackend.procurement.domain;

/**
 * 采购申请的 R1 生命周期状态。
 */
public enum ProcurementRequestStatus
{
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED;

    public static final String SUPPORTED_PATTERN = "DRAFT|SUBMITTED|APPROVED|REJECTED";
}
