package io.github.yche18.juhengbackend.identity.infrastructure.security;

import io.github.yche18.juhengbackend.common.web.error.ApiErrorCode;
import io.github.yche18.juhengbackend.common.web.error.ApiErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 把 Spring Security 过滤器链中的授权失败转换为据衡统一 403 响应。
 */
public class ApiAccessDeniedHandler implements AccessDeniedHandler
{

    private final ApiErrorResponseWriter errorResponseWriter;

    /**
     * 创建无权访问响应处理器。
     *
     * @param errorResponseWriter 统一错误响应写入器
     */
    public ApiAccessDeniedHandler(ApiErrorResponseWriter errorResponseWriter)
    {
        this.errorResponseWriter = errorResponseWriter;
    }

    /**
     * 返回安全的统一无权限错误，不输出目标资源或底层异常细节。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param accessDeniedException Spring Security 授权异常
     * @throws IOException 响应写入失败时抛出
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException
    {
        errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED);
    }

}
