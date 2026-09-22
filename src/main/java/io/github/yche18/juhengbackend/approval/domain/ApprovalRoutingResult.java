package io.github.yche18.juhengbackend.approval.domain;

import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.util.Objects;

/**
 * 显式表达单审批人路由成功或失败的结果。
 *
 * @param assigneeId 成功时的唯一审批人
 * @param failureReason 失败时的原因
 */
public record ApprovalRoutingResult(
        UserId assigneeId,
        ApprovalRoutingFailureReason failureReason)
{

    /**
     * 保证成功结果与失败结果互斥，避免调用方猜测空值语义。
     */
    public ApprovalRoutingResult
    {
        if ((assigneeId == null) == (failureReason == null))
        {
            throw new IllegalArgumentException(
                    "Routing result must contain either an assignee or a failure reason");
        }
    }

    /**
     * 创建包含唯一审批人的成功结果。
     *
     * @param assigneeId 唯一审批人
     * @return 成功路由结果
     */
    public static ApprovalRoutingResult success(UserId assigneeId)
    {
        return new ApprovalRoutingResult(
                Objects.requireNonNull(assigneeId, "Assignee ID must not be null"),
                null);
    }

    /**
     * 创建包含明确原因的失败结果。
     *
     * @param reason 失败原因
     * @return 失败路由结果
     */
    public static ApprovalRoutingResult failure(ApprovalRoutingFailureReason reason)
    {
        return new ApprovalRoutingResult(
                null,
                Objects.requireNonNull(reason, "Routing failure reason must not be null"));
    }

    /**
     * 判断本次路由是否成功得到审批人。
     *
     * @return 成功时为 true
     */
    public boolean successful()
    {
        return assigneeId != null;
    }
}
