package io.github.yche18.juhengbackend.approval.infrastructure.routing;

import io.github.yche18.juhengbackend.approval.domain.ApprovalRoutingFailureReason;
import io.github.yche18.juhengbackend.approval.domain.ApprovalRoutingResult;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证配置驱动的 R1 单审批人路由结果。
 */
class ConfiguredApprovalRoutingPolicyTests
{

    /**
     * 验证唯一且非本人的候选人被明确返回。
     */
    @Test
    void resolvesOneValidApprover()
    {
        ConfiguredApprovalRoutingPolicy policy = new ConfiguredApprovalRoutingPolicy("approver");

        ApprovalRoutingResult result = policy.resolveAssignee(new UserId("requester"));

        assertThat(result.successful()).isTrue();
        assertThat(result.assigneeId()).isEqualTo(new UserId("approver"));
        assertThat(result.failureReason()).isNull();
    }

    /**
     * 验证空配置会显式报告没有审批人。
     */
    @Test
    void reportsNoApprover()
    {
        ApprovalRoutingResult result = new ConfiguredApprovalRoutingPolicy("  ")
                .resolveAssignee(new UserId("requester"));

        assertThat(result.successful()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ApprovalRoutingFailureReason.NO_APPROVER);
    }

    /**
     * 验证多个不同候选人会失败关闭，而不是任意选择一个。
     */
    @Test
    void reportsMultipleApprovers()
    {
        ApprovalRoutingResult result = new ConfiguredApprovalRoutingPolicy("first, second")
                .resolveAssignee(new UserId("requester"));

        assertThat(result.successful()).isFalse();
        assertThat(result.failureReason()).isEqualTo(
                ApprovalRoutingFailureReason.MULTIPLE_APPROVERS);
    }

    /**
     * 验证唯一候选人等于申请人时显式报告自审失败。
     */
    @Test
    void reportsSelfAssignment()
    {
        ApprovalRoutingResult result = new ConfiguredApprovalRoutingPolicy("requester")
                .resolveAssignee(new UserId("requester"));

        assertThat(result.successful()).isFalse();
        assertThat(result.failureReason()).isEqualTo(
                ApprovalRoutingFailureReason.SELF_ASSIGNMENT);
    }

    /**
     * 验证重复配置同一用户不会被误判为多个候选人。
     */
    @Test
    void deduplicatesRepeatedCandidate()
    {
        ApprovalRoutingResult result = new ConfiguredApprovalRoutingPolicy("approver, approver")
                .resolveAssignee(new UserId("requester"));

        assertThat(result.successful()).isTrue();
        assertThat(result.assigneeId()).isEqualTo(new UserId("approver"));
    }
}
