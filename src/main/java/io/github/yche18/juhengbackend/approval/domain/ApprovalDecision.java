package io.github.yche18.juhengbackend.approval.domain;

import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 保存人工审批形成的唯一且不可变的最终决定。
 *
 * @param id 决定标识
 * @param approvalTaskId 所属审批任务标识
 * @param decision 明确的批准或驳回决定
 * @param actorId 来自可信认证上下文的操作者
 * @param decidedAt 服务端决定时间
 * @param comment 可选批准意见或必填驳回原因
 */
public record ApprovalDecision(
        UUID id,
        UUID approvalTaskId,
        ApprovalDecisionType decision,
        UserId actorId,
        Instant decidedAt,
        String comment)
{

    private static final int MAX_COMMENT_LENGTH = 2000;

    /**
     * 校验决定身份、操作者、时间以及决定类型对应的意见约束。
     */
    public ApprovalDecision
    {
        Objects.requireNonNull(id, "Approval decision ID must not be null");
        Objects.requireNonNull(approvalTaskId, "Approval task ID must not be null");
        Objects.requireNonNull(decision, "Approval decision type must not be null");
        Objects.requireNonNull(actorId, "Approval decision actor must not be null");
        Objects.requireNonNull(decidedAt, "Approval decision time must not be null");
        comment = normalizeComment(comment);
        if (decision == ApprovalDecisionType.REJECTED && comment == null)
        {
            throw new IllegalArgumentException("Rejection reason must not be blank");
        }
    }

    /**
     * 去除意见首尾空白，并把批准场景的空白意见规范化为空值。
     *
     * @param value 原始意见
     * @return 规范化意见或空值
     */
    private static String normalizeComment(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_COMMENT_LENGTH)
        {
            throw new IllegalArgumentException("Approval decision comment exceeds maximum length");
        }
        return normalized;
    }
}
