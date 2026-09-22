package io.github.yche18.juhengbackend.common.error;

/**
 * 表示当前可信身份没有执行目标操作的权限。
 *
 * <p>异常中的消息仅供服务端诊断，不能直接返回给 API 调用方。</p>
 */
public final class AuthorizationDeniedException extends RuntimeException
{

    /**
     * 创建无权访问异常。
     *
     * @param internalMessage 仅供服务端使用的内部诊断信息
     */
    public AuthorizationDeniedException(String internalMessage)
    {
        super(internalMessage);
    }

}
