package io.github.yche18.juhengbackend.common.error;

/**
 * 表示同一幂等作用域和键已经绑定到不同请求载荷。
 */
public class IdempotencyConflictException extends RuntimeException
{

    /**
     * 使用仅供服务端诊断的消息创建幂等冲突异常。
     *
     * @param message 内部诊断消息
     */
    public IdempotencyConflictException(String message)
    {
        super(message);
    }
}
