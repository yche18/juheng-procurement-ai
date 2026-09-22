package io.github.yche18.juhengbackend.approval.domain;

import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证审批任务创建时的服务端默认值和自审不变量。
 */
class ApprovalTaskTests
{

    /**
     * 验证合法路由只能创建 PENDING、版本为 0 的任务。
     */
    @Test
    void createsPendingTaskWithServerControlledDefaults()
    {
        UUID taskId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-22T01:02:03Z");

        ApprovalTask task = ApprovalTask.createPending(
                taskId,
                requestId,
                new UserId("requester"),
                new UserId("approver"),
                createdAt);

        assertThat(task.id()).isEqualTo(taskId);
        assertThat(task.procurementRequestId()).isEqualTo(requestId);
        assertThat(task.assigneeId()).isEqualTo(new UserId("approver"));
        assertThat(task.status()).isEqualTo(ApprovalTaskStatus.PENDING);
        assertThat(task.version()).isZero();
        assertThat(task.createdAt()).isEqualTo(createdAt);
        assertThat(task.updatedAt()).isEqualTo(createdAt);
    }

    /**
     * 验证即使 Application 路由校验遗漏，Domain 仍拒绝申请人审批自己。
     */
    @Test
    void rejectsSelfAssignment()
    {
        UserId requester = new UserId("same-user");

        assertThatThrownBy(() -> ApprovalTask.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                requester,
                requester,
                Instant.parse("2026-09-22T01:02:03Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own request");
    }
}
