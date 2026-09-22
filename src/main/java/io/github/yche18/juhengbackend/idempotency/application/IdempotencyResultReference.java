package io.github.yche18.juhengbackend.idempotency.application;

import java.util.Objects;
import java.util.UUID;

/**
 * 保存重放响应所需的最小业务结果引用。
 *
 * @param referenceType 结果对象类型
 * @param referenceId 结果对象标识
 * @param targetVersion 操作完成后的目标版本
 */
public record IdempotencyResultReference(
        String referenceType,
        UUID referenceId,
        long targetVersion)
{

    /**
     * 校验结果引用可安全用于重放。
     */
    public IdempotencyResultReference
    {
        if (referenceType == null || referenceType.isBlank())
        {
            throw new IllegalArgumentException("Result reference type must not be blank");
        }
        referenceType = referenceType.trim();
        Objects.requireNonNull(referenceId, "Result reference ID must not be null");
        if (targetVersion < 0)
        {
            throw new IllegalArgumentException("Result target version must not be negative");
        }
    }
}
