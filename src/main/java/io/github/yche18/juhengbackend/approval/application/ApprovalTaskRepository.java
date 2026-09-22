package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.approval.domain.ApprovalTask;

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
}
