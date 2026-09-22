package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.approval.domain.ApprovalTask;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecision;
import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * 保存人工审批任务聚合的持久化端口。
 */
public interface ApprovalTaskRepository
{

    /**
     * 持久化一个新的待审批任务。
     *
     * @param task 待保存任务
     */
    void save(ApprovalTask task);

    /**
     * 在 SQL 查询中同时限制任务标识和可信受理人，避免先泄漏再授权。
     *
     * @param taskId 任务标识
     * @param assigneeId 可信当前审批人
     * @return 当前审批人范围内的任务聚合
     */
    Optional<ApprovalTask> findAssignedById(UUID taskId, UserId assigneeId);

    /**
     * 先条件更新任务终态，再插入该任务的唯一决定。
     *
     * @param task 已生成最终决定的新任务快照
     * @param expectedVersion 客户端读取到的旧版本
     * @return 条件更新恰好命中一行时为 {@code true}
     */
    boolean saveDecisionConditionally(ApprovalTask task, long expectedVersion);

    /**
     * 按决定标识读取幂等重放所需的不可变结果。
     *
     * @param decisionId 决定标识
     * @return 已持久化决定
     */
    Optional<ApprovalDecision> findDecisionById(UUID decisionId);
}
