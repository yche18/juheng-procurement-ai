package io.github.yche18.juhengbackend.idempotency.application;

import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * 原子取得幂等执行权并保存可重放结果的持久化边界。
 */
public interface IdempotencyStore
{

    /**
     * 尝试取得指定幂等作用域的执行权，或返回已经完成的同载荷结果。
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
    IdempotencyAcquisition acquire(
            UserId callerId,
            String operation,
            String targetType,
            UUID targetId,
            String idempotencyKey,
            String requestFingerprint,
            Instant now);

    /**
     * 将当前调用拥有的幂等记录更新为可重放的完成状态。
     *
     * @param recordId 幂等记录标识
     * @param result 业务结果引用
     * @param completedAt 服务端完成时间
     */
    void complete(
            UUID recordId,
            IdempotencyResultReference result,
            Instant completedAt);
}
