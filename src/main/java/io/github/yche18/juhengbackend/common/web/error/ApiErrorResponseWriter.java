package io.github.yche18.juhengbackend.common.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在 Spring MVC 之外将统一错误结构写入 Servlet 响应。
 *
 * <p>Spring Security 过滤器发生错误时不会进入 ControllerAdvice，因此需要该适配器复用 US-002 契约。</p>
 */
public class ApiErrorResponseWriter
{

    private final ObjectMapper objectMapper;

    /**
     * 创建使用应用统一 JSON 映射器的响应写入器。
     *
     * @param objectMapper Spring Boot 配置的 JSON 映射器
     */
    public ApiErrorResponseWriter(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    /**
     * 写入不包含内部异常信息的统一 JSON 错误响应。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param status 要返回的 HTTP 状态
     * @param errorCode 对外稳定错误代码
     * @throws IOException 响应输出流写入失败时抛出
     */
    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ApiErrorCode errorCode) throws IOException
    {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(errorCode, request.getRequestURI()));
    }

}
