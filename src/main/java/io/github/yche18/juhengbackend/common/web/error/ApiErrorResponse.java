package io.github.yche18.juhengbackend.common.web.error;

import java.util.List;

/**
 * API 统一错误响应。
 *
 * @param code        稳定错误代码
 * @param message     可安全展示给调用方的消息
 * @param path        发生错误的请求路径
 * @param fieldErrors 字段级校验错误；非校验错误时为空列表
 */
public record ApiErrorResponse(
        String code,
        String message,
        String path,
        List<FieldViolation> fieldErrors)
{

    /**
     * 对字段错误列表进行防御性复制，防止响应创建后被外部代码修改。
     */
    public ApiErrorResponse
    {
        fieldErrors = List.copyOf(fieldErrors);
    }

    /**
     * 创建不包含字段错误的统一错误响应。
     *
     * @param errorCode 对外错误代码
     * @param path      发生错误的请求路径
     * @return 具有空字段错误列表的响应
     */
    public static ApiErrorResponse of(ApiErrorCode errorCode, String path)
    {
        return new ApiErrorResponse(errorCode.name(), errorCode.defaultMessage(), path, List.of());
    }

    /**
     * 创建包含字段级校验信息的统一错误响应。
     *
     * @param errorCode  对外错误代码
     * @param path       发生错误的请求路径
     * @param fieldErrors 字段级校验信息
     * @return 包含字段错误的响应
     */
    public static ApiErrorResponse withFieldErrors(
            ApiErrorCode errorCode,
            String path,
            List<FieldViolation> fieldErrors)
    {
        return new ApiErrorResponse(errorCode.name(), errorCode.defaultMessage(), path, fieldErrors);
    }

    /**
     * 描述单个字段违反的校验约束。
     *
     * @param field   字段或参数名称
     * @param code    校验约束代码
     * @param message 可安全展示的校验消息
     */
    public record FieldViolation(String field, String code, String message)
    {
    }

}
