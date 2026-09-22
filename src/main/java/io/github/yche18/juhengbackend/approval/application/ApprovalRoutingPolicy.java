package io.github.yche18.juhengbackend.approval.application;

import io.github.yche18.juhengbackend.approval.domain.ApprovalRoutingResult;
import io.github.yche18.juhengbackend.identity.domain.UserId;

/**
 * 为提交申请解析唯一人工审批人的业务边界。
 */
public interface ApprovalRoutingPolicy
{

    /**
     * 根据申请创建者解析唯一且非本人的审批人。
     *
     * @param requesterId 申请创建者
     * @return 显式成功或失败的路由结果
     */
    ApprovalRoutingResult resolveAssignee(UserId requesterId);
}
