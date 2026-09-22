package io.github.yche18.juhengbackend.common.error;

/**
 * 表示请求与当前业务状态发生冲突，例如状态已变化或版本已过期。
 *
 * <p>异常中的消息仅供服务端诊断，不能直接返回给 API 调用方。</p>
 */
public final class BusinessConflictException extends RuntimeException
{

    /**
     * 创建业务冲突异常。
     *
     * @param internalMessage 仅供服务端使用的内部诊断信息
     */
    public BusinessConflictException(String internalMessage)
    {
        super(internalMessage);
    }

}
