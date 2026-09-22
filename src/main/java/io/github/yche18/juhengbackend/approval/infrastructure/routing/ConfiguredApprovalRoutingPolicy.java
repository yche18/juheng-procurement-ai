package io.github.yche18.juhengbackend.approval.infrastructure.routing;

import io.github.yche18.juhengbackend.approval.application.ApprovalRoutingPolicy;
import io.github.yche18.juhengbackend.approval.domain.ApprovalRoutingFailureReason;
import io.github.yche18.juhengbackend.approval.domain.ApprovalRoutingResult;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 使用显式配置候选人的 R1 单审批人路由实现。
 */
@Component
public class ConfiguredApprovalRoutingPolicy implements ApprovalRoutingPolicy
{

    private final List<UserId> candidates;

    /**
     * 解析逗号分隔的候选审批人配置，并去除空值和重复值。
     *
     * @param configuredAssigneeIds 候选审批人 ID 配置
     */
    public ConfiguredApprovalRoutingPolicy(
            @Value("${juheng.approval.assignee-ids:}") String configuredAssigneeIds)
    {
        String rawValue = configuredAssigneeIds == null ? "" : configuredAssigneeIds;
        LinkedHashSet<UserId> uniqueCandidates = new LinkedHashSet<>();
        Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(UserId::new)
                .forEach(uniqueCandidates::add);
        this.candidates = List.copyOf(uniqueCandidates);
    }

    /**
     * 只接受唯一且不是申请创建者本人的配置候选人。
     *
     * @param requesterId 申请创建者
     * @return 成功结果或明确的失败原因
     */
    @Override
    public ApprovalRoutingResult resolveAssignee(UserId requesterId)
    {
        Objects.requireNonNull(requesterId, "Requester ID must not be null");
        if (candidates.isEmpty())
        {
            return ApprovalRoutingResult.failure(ApprovalRoutingFailureReason.NO_APPROVER);
        }
        if (candidates.size() > 1)
        {
            return ApprovalRoutingResult.failure(ApprovalRoutingFailureReason.MULTIPLE_APPROVERS);
        }
        UserId candidate = candidates.get(0);
        if (candidate.equals(requesterId))
        {
            return ApprovalRoutingResult.failure(ApprovalRoutingFailureReason.SELF_ASSIGNMENT);
        }
        return ApprovalRoutingResult.success(candidate);
    }
}
