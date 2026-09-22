package io.github.yche18.juhengbackend.common.error;

/**
 * 表示已持久化业务对象不满足当前用例要求的完整性校验。
 */
public class BusinessValidationException extends RuntimeException
{

    /**
     * 使用仅供服务端诊断的消息创建业务校验异常。
     *
     * @param message 内部诊断消息
     */
    public BusinessValidationException(String message)
    {
        super(message);
    }
}
