package io.github.yche18.juhengbackend.approval.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code approval_decision} 表的 MyBatis-Plus 持久化映射。
 */
@TableName("approval_decision")
public record ApprovalDecisionDO(
        @TableId(value = "id", type = IdType.INPUT) UUID id,
        UUID approvalTaskId,
        String decision,
        String actorId,
        Instant decidedAt,
        String comment)
{
}
