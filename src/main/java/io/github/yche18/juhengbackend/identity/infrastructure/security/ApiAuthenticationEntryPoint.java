package io.github.yche18.juhengbackend.identity.infrastructure.security;

import io.github.yche18.juhengbackend.common.web.error.ApiErrorCode;
import io.github.yche18.juhengbackend.common.web.error.ApiErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 把 Spring Security 的未认证失败转换为据衡统一 401 响应。
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint
{

    private static final String BASIC_CHALLENGE = "Basic realm=\"Juheng\"";
    private final ApiErrorResponseWriter errorResponseWriter;

    /**
     * 创建未认证响应入口。
     *
     * @param errorResponseWriter 统一错误响应写入器
     */
    public ApiAuthenticationEntryPoint(ApiErrorResponseWriter errorResponseWriter)
    {
        this.errorResponseWriter = errorResponseWriter;
    }

    /**
     * 返回 Basic challenge 和安全的统一未认证错误，不输出底层认证失败原因。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param authenticationException Spring Security 认证异常
     * @throws IOException 响应写入失败时抛出
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException
    {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BASIC_CHALLENGE);
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.AUTHENTICATION_REQUIRED);
    }

}
