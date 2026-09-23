package io.github.yche18.juhengbackend.common.web.error;

import io.github.yche18.juhengbackend.common.error.AuthenticationRequiredException;
import io.github.yche18.juhengbackend.common.error.ApprovalRoutingException;
import io.github.yche18.juhengbackend.common.error.AuthorizationDeniedException;
import io.github.yche18.juhengbackend.common.error.BusinessConflictException;
import io.github.yche18.juhengbackend.common.error.BusinessValidationException;
import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;
import io.github.yche18.juhengbackend.common.error.IdempotencyConflictException;
import io.github.yche18.juhengbackend.common.error.IdempotencyInProgressException;
import io.github.yche18.juhengbackend.common.error.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 将 Web 层和业务层异常统一转换为稳定、安全的 API 错误响应。
 *
 * <p>该类只负责协议层映射，不在这里实现认证、授权或领域规则。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_VALUE = "Invalid value";

    /**
     * 将请求体对象的 Bean Validation 错误转换为字段级响应。
     *
     * @param exception Spring MVC 抛出的请求体校验异常
     * @param request   当前 HTTP 请求
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request)
    {
        List<ApiErrorResponse.FieldViolation> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldViolation)
                .sorted(fieldViolationComparator())
                .toList();

        return error(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, request, fieldErrors);
    }

    /**
     * 将 Controller 方法参数的校验错误转换为字段级响应。
     *
     * @param exception Spring 方法参数校验异常
     * @param request   当前 HTTP 请求
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request)
    {
        List<ApiErrorResponse.FieldViolation> fieldErrors = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults())
        {
            String parameterName = result.getMethodParameter().getParameterName();
            String field = parameterName == null ? "parameter" : parameterName;
            for (MessageSourceResolvable resolvable : result.getResolvableErrors())
            {
                if (resolvable instanceof FieldError fieldError)
                {
                    fieldErrors.add(toFieldViolation(fieldError));
                }
                else
                {
                    fieldErrors.add(new ApiErrorResponse.FieldViolation(
                            field,
                            simpleValidationCode(resolvable.getCodes()),
                            safeValidationMessage(resolvable.getDefaultMessage())));
                }
            }
        }
        fieldErrors.sort(fieldViolationComparator());

        return error(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, request, fieldErrors);
    }

    /**
     * 将方法级 Jakarta Validation 约束错误转换为字段级响应。
     *
     * @param exception Jakarta Validation 约束异常
     * @param request   当前 HTTP 请求
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request)
    {
        List<ApiErrorResponse.FieldViolation> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(this::toFieldViolation)
                .sorted(fieldViolationComparator())
                .toList();

        return error(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, request, fieldErrors);
    }

    /**
     * 处理 JSON 等请求体无法解析的情况，同时隐藏底层解析器细节。
     *
     * @param exception 请求体解析异常
     * @param request   当前 HTTP 请求
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST, request);
    }

    /**
     * 将路径变量或查询参数的类型转换失败映射为安全的请求格式错误。
     *
     * @param exception Spring MVC 参数类型转换异常
     * @param request 当前 HTTP 请求
     * @return HTTP 400 统一错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST, request);
    }

    /**
     * 将缺少可信身份的异常映射为未认证响应。
     *
     * @param exception 未认证异常；其内部消息不会写入响应
     * @param request   当前 HTTP 请求
     * @return HTTP 401 统一错误响应
     */
    @ExceptionHandler(AuthenticationRequiredException.class)
    ResponseEntity<ApiErrorResponse> handleAuthenticationRequired(
            AuthenticationRequiredException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTHENTICATION_REQUIRED, request);
    }

    /**
     * 将权限不足异常映射为禁止访问响应。
     *
     * @param exception 无权访问异常；其内部消息不会写入响应
     * @param request   当前 HTTP 请求
     * @return HTTP 403 统一错误响应
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, request);
    }

    /**
     * 将当前数据范围内不可见的资源统一映射为不存在，避免泄露资源归属。
     *
     * @param exception 资源不存在异常；其内部消息不会写入响应
     * @param request 当前 HTTP 请求
     * @return HTTP 404 统一错误响应
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, request);
    }

    /**
     * 将业务对象完整性不足映射为安全的请求校验失败。
     *
     * @param exception 业务完整性异常；内部消息不会写入响应
     * @param request 当前 HTTP 请求
     * @return HTTP 400 业务校验错误响应
     */
    @ExceptionHandler(BusinessValidationException.class)
    ResponseEntity<ApiErrorResponse> handleBusinessValidation(
            BusinessValidationException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, request);
    }

    /**
     * 将审批路由失败映射为可区分的冲突响应。
     *
     * @param exception 审批路由异常；内部消息不会写入响应
     * @param request 当前 HTTP 请求
     * @return HTTP 409 审批路由错误响应
     */
    @ExceptionHandler(ApprovalRoutingException.class)
    ResponseEntity<ApiErrorResponse> handleApprovalRouting(
            ApprovalRoutingException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.CONFLICT, ApiErrorCode.APPROVAL_ROUTING_FAILED, request);
    }

    /**
     * 将幂等键载荷冲突映射为可区分的冲突响应。
     *
     * @param exception 幂等冲突异常；内部消息不会写入响应
     * @param request 当前 HTTP 请求
     * @return HTTP 409 幂等冲突响应
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT, request);
    }

    /**
     * 将尚未完成的相同幂等请求映射为明确处理中响应。
     *
     * @param exception 幂等处理中异常；内部消息不会写入响应
     * @param request 当前 HTTP 请求
     * @return HTTP 409 幂等处理中响应
     */
    @ExceptionHandler(IdempotencyInProgressException.class)
    ResponseEntity<ApiErrorResponse> handleIdempotencyInProgress(
            IdempotencyInProgressException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_IN_PROGRESS, request);
    }

    /**
     * 将业务状态冲突映射为冲突响应，避免被误报为服务器故障。
     *
     * @param exception 业务冲突异常；其内部消息不会写入响应
     * @param request   当前 HTTP 请求
     * @return HTTP 409 统一错误响应
     */
    @ExceptionHandler(BusinessConflictException.class)
    ResponseEntity<ApiErrorResponse> handleBusinessConflict(
            BusinessConflictException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.CONFLICT, ApiErrorCode.BUSINESS_CONFLICT, request);
    }

    /**
     * 将乐观版本或数据库条件更新失败映射为可区分的并发冲突响应。
     *
     * @param exception 并发更新异常；内部消息不会写入响应
     * @param request 当前 HTTP 请求
     * @return HTTP 409 并发修改错误响应
     */
    @ExceptionHandler(ConcurrentUpdateException.class)
    ResponseEntity<ApiErrorResponse> handleConcurrentUpdate(
            ConcurrentUpdateException exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.CONFLICT, ApiErrorCode.CONCURRENT_MODIFICATION, request);
    }

    /**
     * 兜底处理未预期异常，只记录异常类型和请求路径并返回通用消息。
     *
     * @param exception 未预期异常
     * @param request   当前 HTTP 请求
     * @return HTTP 500 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request)
    {
        LOGGER.error("Unhandled exception type={} path={}",
                exception.getClass().getName(), request.getRequestURI());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR, request);
    }

    /**
     * 构造不包含字段错误的响应实体。
     *
     * @param status    HTTP 状态
     * @param errorCode 对外错误代码
     * @param request   当前 HTTP 请求
     * @return 统一错误响应实体
     */
    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            ApiErrorCode errorCode,
            HttpServletRequest request)
    {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(errorCode, request.getRequestURI()));
    }

    /**
     * 构造包含字段级校验信息的响应实体。
     *
     * @param status      HTTP 状态
     * @param errorCode   对外错误代码
     * @param request     当前 HTTP 请求
     * @param fieldErrors 字段级校验错误
     * @return 统一错误响应实体
     */
    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            ApiErrorCode errorCode,
            HttpServletRequest request,
            List<ApiErrorResponse.FieldViolation> fieldErrors)
    {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.withFieldErrors(errorCode, request.getRequestURI(), fieldErrors));
    }

    /**
     * 将 Spring 字段错误转换为统一字段错误，并排除被拒绝的原始值。
     *
     * @param fieldError Spring 字段校验错误
     * @return 对外字段错误
     */
    private ApiErrorResponse.FieldViolation toFieldViolation(FieldError fieldError)
    {
        return new ApiErrorResponse.FieldViolation(
                fieldError.getField(),
                fieldError.getCode() == null ? "Invalid" : fieldError.getCode(),
                safeValidationMessage(fieldError.getDefaultMessage()));
    }

    /**
     * 将 Jakarta Validation 约束错误转换为统一字段错误。
     *
     * @param violation 约束违反信息
     * @return 对外字段错误
     */
    private ApiErrorResponse.FieldViolation toFieldViolation(ConstraintViolation<?> violation)
    {
        return new ApiErrorResponse.FieldViolation(
                violation.getPropertyPath().toString(),
                violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                safeValidationMessage(violation.getMessage()));
    }

    /**
     * 从 Spring 的错误代码链中取得最具体、最稳定的约束代码。
     *
     * @param codes Spring 提供的错误代码链
     * @return 简化后的约束代码
     */
    private String simpleValidationCode(String[] codes)
    {
        if (codes == null || codes.length == 0)
        {
            return "Invalid";
        }
        return codes[codes.length - 1];
    }

    /**
     * 确保对外校验消息始终存在，避免返回空值。
     *
     * @param message 原始校验消息
     * @return 可安全展示的非空消息
     */
    private String safeValidationMessage(String message)
    {
        return message == null || message.isBlank() ? INVALID_VALUE : message;
    }

    /**
     * 创建字段错误的稳定排序规则，保证相同输入得到一致的响应顺序。
     *
     * @return 先按字段名、再按约束代码排序的比较器
     */
    private Comparator<ApiErrorResponse.FieldViolation> fieldViolationComparator()
    {
        return Comparator.comparing(ApiErrorResponse.FieldViolation::field)
                .thenComparing(ApiErrorResponse.FieldViolation::code);
    }

}
