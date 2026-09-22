package io.github.yche18.juhengbackend.approval.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

/**
 * `approval_task` 表的 MyBatis-Plus 持久化映射。
 *
 * @param id 主键
 * @param procurementRequestId 所属申请
 * @param assigneeId 审批人
 * @param status 任务状态
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
@TableName("approval_task")
public record ApprovalTaskDO(
        @TableId(value = "id", type = IdType.INPUT) UUID id,
        UUID procurementRequestId,
        String assigneeId,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt)
{
}
