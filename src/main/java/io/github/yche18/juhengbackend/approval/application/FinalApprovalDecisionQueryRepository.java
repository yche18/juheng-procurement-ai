package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * 按申请创建者或任务受理人范围读取最终审批决定的查询端口。
 */
public interface FinalApprovalDecisionQueryRepository
{

    /**
     * 在数据库查询中直接限定查看者范围，并只返回已经保存的唯一最终决定。
     *
     * @param requestId 采购申请标识
     * @param viewerId 可信当前用户标识
     * @return 决定存在且位于当前用户范围内时返回只读投影，否则返回空
     */
    Optional<FinalApprovalDecisionDetails> findVisibleByRequestId(
            UUID requestId,
            UserId viewerId);
}
