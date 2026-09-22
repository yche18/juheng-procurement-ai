package io.github.yche18.juhengbackend.common.error;

/**
 * 表示系统无法为申请解析出唯一且有效的审批人。
 */
public class ApprovalRoutingException extends RuntimeException
{

    /**
     * 使用仅供服务端诊断的路由原因创建异常。
     *
     * @param message 内部诊断消息
     */
    public ApprovalRoutingException(String message)
    {
        super(message);
    }
}
