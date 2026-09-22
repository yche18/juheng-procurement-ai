package io.github.yche18.juhengbackend.common.error;

/**
 * 表示客户端依据的资源版本已经过期，继续写入会覆盖其他请求的结果。
 *
 * <p>异常中的消息仅供服务端诊断，API 对外返回稳定的并发冲突代码。</p>
 */
public final class ConcurrentUpdateException extends RuntimeException
{

    /**
     * 创建并发更新冲突异常。
     *
     * @param internalMessage 仅供服务端使用的内部诊断信息
     */
    public ConcurrentUpdateException(String internalMessage)
    {
        super(internalMessage);
    }
}
