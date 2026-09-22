package io.github.yche18.juhengbackend.idempotency.application;

import java.util.Objects;
import java.util.UUID;

/**
 * 表示当前调用取得执行权，或应重放已完成结果。
 *
 * @param recordId 幂等记录标识
 * @param replayResult 已完成请求的结果；取得执行权时为空
 */
public record IdempotencyAcquisition(
        UUID recordId,
        IdempotencyResultReference replayResult)
{

    /**
     * 校验幂等获取结果至少包含记录标识。
     */
    public IdempotencyAcquisition
    {
        Objects.requireNonNull(recordId, "Idempotency record ID must not be null");
    }

    /**
     * 创建表示当前调用取得执行权的结果。
     *
     * @param recordId 新幂等记录标识
     * @return 执行权结果
     */
    public static IdempotencyAcquisition acquired(UUID recordId)
    {
        return new IdempotencyAcquisition(recordId, null);
    }

    /**
     * 创建表示应返回首次结果的重放结果。
     *
     * @param recordId 已存在幂等记录标识
     * @param result 首次执行结果引用
     * @return 重放结果
     */
    public static IdempotencyAcquisition replay(
            UUID recordId,
            IdempotencyResultReference result)
    {
        return new IdempotencyAcquisition(
                recordId,
                Objects.requireNonNull(result, "Replay result must not be null"));
    }

    /**
     * 判断当前调用是否取得实际业务执行权。
     *
     * @return 需要执行业务时为 true
     */
    public boolean acquired()
    {
        return replayResult == null;
    }
}
