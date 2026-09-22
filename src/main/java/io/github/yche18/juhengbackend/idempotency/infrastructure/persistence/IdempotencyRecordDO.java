package io.github.yche18.juhengbackend.idempotency.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

/**
 * `idempotency_record` 表的 MyBatis-Plus 持久化映射。
 */
@TableName("idempotency_record")
public record IdempotencyRecordDO(
        @TableId(value = "id", type = IdType.INPUT) UUID id,
        String callerId,
        String operation,
        String targetType,
        UUID targetId,
        String idempotencyKey,
        String requestFingerprint,
        String status,
        String resultReferenceType,
        UUID resultReferenceId,
        Long resultTargetVersion,
        Instant createdAt,
        Instant updatedAt)
{
}
