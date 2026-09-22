package io.github.yche18.juhengbackend.common.web.error;

/**
 * API 对外公开的稳定错误代码及其安全默认消息。
 *
 * <p>调用方应依赖错误代码进行分支处理，不应依赖可能调整的自然语言消息。</p>
 */
public enum ApiErrorCode
{
    INVALID_REQUEST("Request is malformed"),
    VALIDATION_FAILED("Request validation failed"),
    AUTHENTICATION_REQUIRED("Authentication is required"),
    ACCESS_DENIED("Access is denied"),
    RESOURCE_NOT_FOUND("Resource was not found"),
    BUSINESS_CONFLICT("Request conflicts with the current business state"),
    INTERNAL_ERROR("An unexpected error occurred");

    private final String defaultMessage;

    /**
     * 创建一个带安全默认消息的错误代码。
     *
     * @param defaultMessage 可以直接返回给调用方的默认消息
     */
    ApiErrorCode(String defaultMessage)
    {
        this.defaultMessage = defaultMessage;
    }

    /**
     * 返回该错误代码对应的安全默认消息。
     *
     * @return 不包含内部异常细节的默认消息
     */
    public String defaultMessage()
    {
        return defaultMessage;
    }
}
