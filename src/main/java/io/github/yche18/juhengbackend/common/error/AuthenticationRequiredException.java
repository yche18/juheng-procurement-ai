package io.github.yche18.juhengbackend.common.error;

/**
 * 表示当前请求缺少可信认证身份。
 *
 * <p>异常中的消息仅供服务端诊断，不能直接返回给 API 调用方。</p>
 */
public final class AuthenticationRequiredException extends RuntimeException
{

    /**
     * 创建未认证异常。
     *
     * @param internalMessage 仅供服务端使用的内部诊断信息
     */
    public AuthenticationRequiredException(String internalMessage)
    {
        super(internalMessage);
    }

}
