package io.github.yche18.juhengbackend.common.error;

/**
 * 表示当前访问范围内不存在目标资源。
 *
 * <p>该异常也用于隐藏“资源存在但属于其他用户”的情况，避免调用方枚举资源。</p>
 */
public class ResourceNotFoundException extends RuntimeException
{

    /**
     * 创建资源不存在异常；内部消息仅用于诊断，不会直接返回给客户端。
     *
     * @param message 内部诊断消息
     */
    public ResourceNotFoundException(String message)
    {
        super(message);
    }
}
