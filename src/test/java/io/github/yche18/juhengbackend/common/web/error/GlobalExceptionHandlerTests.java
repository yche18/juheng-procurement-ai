package io.github.yche18.juhengbackend.common.web.error;

import io.github.yche18.juhengbackend.common.error.AuthenticationRequiredException;
import io.github.yche18.juhengbackend.common.error.ApprovalRoutingException;
import io.github.yche18.juhengbackend.common.error.AuthorizationDeniedException;
import io.github.yche18.juhengbackend.common.error.BusinessConflictException;
import io.github.yche18.juhengbackend.common.error.IdempotencyConflictException;
import io.github.yche18.juhengbackend.common.error.IdempotencyInProgressException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证统一异常处理器的 HTTP 状态、响应结构和敏感信息保护行为。
 */
@WebMvcTest(controllers = ErrorHandlingTestController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("no-database")
class GlobalExceptionHandlerTests
{

    @Autowired
    private MockMvc mockMvc;

    /**
     * 验证请求体 Bean Validation 错误会被映射为字段级信息。
     */
    @Test
    void mapsBeanValidationErrorsToFieldLevelResponse() throws Exception
    {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/test/errors/validation"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NotBlank"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"));
    }

    /**
     * 验证 Controller 请求参数约束也使用相同的字段错误结构。
     */
    @Test
    void mapsRequestParameterValidationToFieldLevelResponse() throws Exception
    {
        mockMvc.perform(get("/test/errors/parameter-validation").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("Min"));
    }

    /**
     * 验证格式错误的 JSON 返回稳定代码，且不暴露解析器实现细节。
     */
    @Test
    void mapsMalformedJsonWithoutExposingParserDetails() throws Exception
    {
        MvcResult result = mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("JsonEOFException", "HttpMessageNotReadableException", "stackTrace");
    }

    /**
     * 验证未认证和无权访问分别映射为 401 与 403，且隐藏内部消息。
     */
    @Test
    void distinguishesAuthenticationAndAuthorizationErrors() throws Exception
    {
        MvcResult authenticationResult = mockMvc.perform(get("/test/errors/authentication"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andReturn();

        MvcResult authorizationResult = mockMvc.perform(get("/test/errors/authorization"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andReturn();

        assertThat(authenticationResult.getResponse().getContentAsString())
                .doesNotContain("secret-token", "AuthenticationRequiredException");
        assertThat(authorizationResult.getResponse().getContentAsString())
                .doesNotContain("supplier-contract", "AuthorizationDeniedException");
    }

    /**
     * 验证业务冲突返回 409，而不是伪装成 500 系统故障。
     */
    @Test
    void mapsBusinessConflictWithoutReportingServerError() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/test/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("Request conflicts with the current business state"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("database version", "BusinessConflictException");
    }

    /**
     * 验证审批路由和两种幂等异常使用各自稳定的 409 错误代码。
     */
    @Test
    void distinguishesRoutingAndIdempotencyConflicts() throws Exception
    {
        mockMvc.perform(get("/test/errors/routing"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_ROUTING_FAILED"));
        mockMvc.perform(get("/test/errors/idempotency-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        mockMvc.perform(get("/test/errors/idempotency-in-progress"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_IN_PROGRESS"));
    }

    /**
     * 验证未预期异常使用通用响应，且不泄露凭据、异常类型或堆栈。
     */
    @Test
    void hidesUnexpectedExceptionDetailsAndSensitiveValues() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/test/errors/system"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/test/errors/system"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("password", "top-secret", "IllegalStateException", "stackTrace");
    }

}

/**
 * 仅供 Web 切片测试触发各类错误的测试 Controller，不进入生产代码。
 */
@RestController
@RequestMapping("/test/errors")
class ErrorHandlingTestController
{

    /**
     * 接收待校验请求体，用于触发 Bean Validation。
     *
     * @param request 测试请求体
     */
    @PostMapping("/validation")
    void validate(@Valid @RequestBody ValidationRequest request)
    {
    }

    /**
     * 接收带最小值约束的参数，用于触发方法参数校验。
     *
     * @param page 测试页码
     */
    @GetMapping("/parameter-validation")
    void validateParameter(@RequestParam @Min(1) int page)
    {
    }

    /**
     * 抛出未认证异常，验证 401 映射和内部消息保护。
     */
    @GetMapping("/authentication")
    void authenticationRequired()
    {
        throw new AuthenticationRequiredException("Rejected bearer secret-token");
    }

    /**
     * 抛出无权访问异常，验证 403 映射和内部消息保护。
     */
    @GetMapping("/authorization")
    void authorizationDenied()
    {
        throw new AuthorizationDeniedException("Denied access to supplier-contract");
    }

    /**
     * 抛出业务冲突异常，验证 409 映射。
     */
    @GetMapping("/conflict")
    void businessConflict()
    {
        throw new BusinessConflictException("Stale database version supplied");
    }

    /**
     * 抛出审批路由异常，验证专用 409 映射。
     */
    @GetMapping("/routing")
    void approvalRoutingFailed()
    {
        throw new ApprovalRoutingException("No configured approver");
    }

    /**
     * 抛出幂等载荷冲突，验证专用 409 映射。
     */
    @GetMapping("/idempotency-conflict")
    void idempotencyConflict()
    {
        throw new IdempotencyConflictException("Payload fingerprint differs");
    }

    /**
     * 抛出幂等处理中异常，验证专用 409 映射。
     */
    @GetMapping("/idempotency-in-progress")
    void idempotencyInProgress()
    {
        throw new IdempotencyInProgressException("Execution still owns the key");
    }

    /**
     * 抛出未预期异常，验证 500 兜底响应不会泄露敏感内容。
     */
    @GetMapping("/system")
    void systemError()
    {
        throw new IllegalStateException("password=top-secret");
    }

    /**
     * Web 校验测试使用的最小请求结构。
     *
     * @param title    必填标题
     * @param quantity 必须大于等于 1 的数量
     */
    record ValidationRequest(@NotBlank String title, @Min(1) int quantity)
    {
    }

}
