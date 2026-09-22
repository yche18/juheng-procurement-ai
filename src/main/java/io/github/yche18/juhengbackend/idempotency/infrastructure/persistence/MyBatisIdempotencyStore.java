package io.github.yche18.juhengbackend.idempotency.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.yche18.juhengbackend.common.error.IdempotencyConflictException;
import io.github.yche18.juhengbackend.common.error.IdempotencyInProgressException;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import io.github.yche18.juhengbackend.idempotency.application.IdempotencyAcquisition;
import io.github.yche18.juhengbackend.idempotency.application.IdempotencyResultReference;
import io.github.yche18.juhengbackend.idempotency.application.IdempotencyStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 使用 PostgreSQL 唯一约束实现事务内幂等执行权竞争。
 */
@Repository
@Profile("!no-database")
public class MyBatisIdempotencyStore implements IdempotencyStore
{

    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";

    private final IdempotencyRecordMapper mapper;

    /**
     * 创建幂等记录持久化适配器。
     *
     * @param mapper 幂等记录 Mapper
     */
    public MyBatisIdempotencyStore(IdempotencyRecordMapper mapper)
    {
        this.mapper = mapper;
    }

    /**
     * 先尝试原子插入；发生唯一冲突时读取并解释已有记录。
     *
     * @param callerId 可信调用者
     * @param operation 操作类型
     * @param targetType 目标类型
     * @param targetId 目标标识
     * @param idempotencyKey 客户端幂等键
     * @param requestFingerprint 请求载荷摘要
     * @param now 服务端时间
     * @return 执行权或重放结果
     */
    @Override
    public IdempotencyAcquisition acquire(
            UserId callerId,
            String operation,
            String targetType,
            UUID targetId,
            String idempotencyKey,
            String requestFingerprint,
            Instant now)
    {
        Objects.requireNonNull(callerId, "Caller ID must not be null");
        UUID recordId = UUID.randomUUID();
        IdempotencyRecordDO candidate = new IdempotencyRecordDO(
                recordId,
                callerId.value(),
                requiredText(operation, "Operation"),
                requiredText(targetType, "Target type"),
                Objects.requireNonNull(targetId, "Target ID must not be null"),
                requiredText(idempotencyKey, "Idempotency key"),
                requiredText(requestFingerprint, "Request fingerprint"),
                IN_PROGRESS,
                null,
                null,
                null,
                Objects.requireNonNull(now, "Current time must not be null"),
                now);
        if (mapper.insertIfAbsent(candidate) == 1)
        {
            return IdempotencyAcquisition.acquired(recordId);
        }

        IdempotencyRecordDO existing = findExisting(candidate);
        if (!existing.requestFingerprint().equals(candidate.requestFingerprint()))
        {
            throw new IdempotencyConflictException(
                    "Idempotency key is already bound to a different request payload");
        }
        if (COMPLETED.equals(existing.status()))
        {
            return IdempotencyAcquisition.replay(
                    existing.id(),
                    new IdempotencyResultReference(
                            existing.resultReferenceType(),
                            existing.resultReferenceId(),
                            existing.resultTargetVersion()));
        }
        throw new IdempotencyInProgressException(
                "An identical idempotent request is still in progress");
    }

    /**
     * 原子完成幂等记录，更新失败表示调用方不再拥有有效执行权。
     *
     * @param recordId 幂等记录标识
     * @param result 业务结果引用
     * @param completedAt 服务端完成时间
     */
    @Override
    public void complete(
            UUID recordId,
            IdempotencyResultReference result,
            Instant completedAt)
    {
        Objects.requireNonNull(result, "Idempotency result must not be null");
        int updatedRows = mapper.complete(
                Objects.requireNonNull(recordId, "Idempotency record ID must not be null"),
                result.referenceType(),
                result.referenceId(),
                result.targetVersion(),
                Objects.requireNonNull(completedAt, "Completed time must not be null"));
        if (updatedRows != 1)
        {
            throw new IllegalStateException("Expected one idempotency record to be completed");
        }
    }

    /**
     * 按完整幂等作用域读取唯一已有记录。
     *
     * @param candidate 包含作用域字段的新记录候选
     * @return 已存在记录
     */
    private IdempotencyRecordDO findExisting(IdempotencyRecordDO candidate)
    {
        IdempotencyRecordDO existing = mapper.selectOne(
                new QueryWrapper<IdempotencyRecordDO>()
                        .eq("caller_id", candidate.callerId())
                        .eq("operation", candidate.operation())
                        .eq("target_type", candidate.targetType())
                        .eq("target_id", candidate.targetId())
                        .eq("idempotency_key", candidate.idempotencyKey()));
        if (existing == null)
        {
            throw new IllegalStateException(
                    "Idempotency uniqueness conflict occurred without a readable record");
        }
        return existing;
    }

    /**
     * 校验并规范化幂等文本字段。
     *
     * @param value 原始文本
     * @param fieldName 字段名称
     * @return 去除首尾空白后的文本
     */
    private String requiredText(String value, String fieldName)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
