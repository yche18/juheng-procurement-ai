package io.github.yche18.juhengbackend.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

/**
 * `audit_event` 表的 MyBatis-Plus 持久化映射。
 *
 * @param id 主键
 * @param procurementRequestId 所属申请
 * @param actorId 操作者
 * @param action 动作
 * @param targetType 目标类型
 * @param targetId 目标标识
 * @param occurredAt 发生时间
 * @param result 结果
 * @param requestIdentifier 请求标识
 */
@TableName("audit_event")
public record AuditEventDO(
        @TableId(value = "id", type = IdType.INPUT) UUID id,
        UUID procurementRequestId,
        String actorId,
        String action,
        String targetType,
        UUID targetId,
        Instant occurredAt,
        String result,
        String requestIdentifier)
{
}
