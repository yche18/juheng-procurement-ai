package io.github.yche18.juhengbackend.common.error;

/**
 * 表示相同幂等请求已经被另一执行者占用但尚未产生可重放结果。
 */
public class IdempotencyInProgressException extends RuntimeException
{

    /**
     * 使用仅供服务端诊断的消息创建处理中异常。
     *
     * @param message 内部诊断消息
     */
    public IdempotencyInProgressException(String message)
    {
        super(message);
    }
}
