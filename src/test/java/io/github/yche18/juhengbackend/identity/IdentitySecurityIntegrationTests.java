package io.github.yche18.juhengbackend.identity;

import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.CurrentUserProvider;
import io.github.yche18.juhengbackend.identity.application.RoleAuthorizer;
import io.github.yche18.juhengbackend.identity.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 Spring Security 过滤器链验证 US-003 的认证、角色和防伪造边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("no-database")
@Import({IdentitySecurityIntegrationTests.IdentityTestController.class,
        IdentitySecurityIntegrationTests.TestWriteProbe.class})
class IdentitySecurityIntegrationTests
{

    private static final String DEMO_PASSWORD = "juheng-local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestWriteProbe writeProbe;

    /**
     * 在每个测试前清空写操作探针，避免测试之间相互影响。
     */
    @BeforeEach
    void resetWriteProbe()
    {
        writeProbe.reset();
    }

    /**
     * 验证三个基础角色都能由固定本地身份建立可信当前用户上下文。
     */
    @Test
    void resolvesEachSingleRoleIdentity() throws Exception
    {
        assertCurrentUser("demo-requester", "REQUESTER");
        assertCurrentUser("demo-approver", "APPROVER");
        assertCurrentUser("demo-admin", "ADMIN");
    }

    /**
     * 验证同一用户同时拥有 REQUESTER 与 APPROVER 时不会丢失任一角色。
     */
    @Test
    void preservesMultipleRolesFromAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/current-user")
                        .with(httpBasic("demo-requester-approver", DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("demo-requester-approver"))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("REQUESTER"))
                .andExpect(jsonPath("$.roles[1]").value("APPROVER"));
    }

    /**
     * 验证缺失或错误凭据返回统一 401，且请求不会进入受保护的写操作。
     */
    @Test
    void rejectsMissingAndInvalidCredentialsWithoutWriting() throws Exception
    {
        mockMvc.perform(post("/test/identity/write"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"Juheng\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/test/identity/write"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        MvcResult invalidCredentialsResult = mockMvc.perform(get("/api/current-user")
                        .with(httpBasic("demo-requester", "incorrect-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andReturn();

        assertThat(writeProbe.count()).isZero();
        assertThat(invalidCredentialsResult.getResponse().getContentAsString())
                .doesNotContain("incorrect-password", "BadCredentialsException", "stackTrace");
    }

    /**
     * 验证已认证但缺少 APPROVER 角色时返回统一 403，并隐藏资源内容。
     */
    @Test
    void deniesMissingRoleWithoutLeakingSensitiveResource() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/test/identity/approver-only")
                        .with(httpBasic("demo-requester", DEMO_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.path").value("/test/identity/approver-only"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("supplier-contract-secret", "AuthorizationDeniedException");
    }

    /**
     * 验证请求体、查询参数和自定义请求头都不能覆盖认证上下文中的身份与角色。
     */
    @Test
    void ignoresClientSuppliedIdentityAndRoles() throws Exception
    {
        mockMvc.perform(get("/api/current-user")
                        .with(httpBasic("demo-requester", DEMO_PASSWORD))
                        .queryParam("userId", "demo-admin")
                        .queryParam("roles", "ADMIN")
                        .header("X-User-Id", "demo-admin")
                        .header("X-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"demo-admin","roles":["ADMIN"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("demo-requester"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("REQUESTER"));
    }

    /**
     * 调用当前用户接口并断言指定单角色身份。
     *
     * @param username 本地演示用户名
     * @param roleName 预期角色名称
     */
    private void assertCurrentUser(String username, String roleName) throws Exception
    {
        mockMvc.perform(get("/api/current-user")
                        .with(httpBasic(username, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(username))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value(roleName));
    }

    /**
     * 仅供集成测试验证应用层角色授权和认证过滤器短路行为。
     */
    @RestController
    @RequestMapping("/test/identity")
    static class IdentityTestController
    {

        private final CurrentUserProvider currentUserProvider;
        private final RoleAuthorizer roleAuthorizer;
        private final TestWriteProbe writeProbe;

        /**
         * 创建测试 Controller。
         *
         * @param currentUserProvider 可信当前用户提供端口
         * @param roleAuthorizer 应用层角色授权器
         * @param writeProbe 写操作探针
         */
        IdentityTestController(
                CurrentUserProvider currentUserProvider,
                RoleAuthorizer roleAuthorizer,
                TestWriteProbe writeProbe)
        {
            this.currentUserProvider = currentUserProvider;
            this.roleAuthorizer = roleAuthorizer;
            this.writeProbe = writeProbe;
        }

        /**
         * 仅允许 APPROVER 角色访问，并在成功时返回模拟敏感资源。
         *
         * @return 模拟敏感资源
         */
        @GetMapping("/approver-only")
        String approverOnly()
        {
            CurrentUser currentUser = currentUserProvider.getCurrentUser();
            roleAuthorizer.requireRole(currentUser, Role.APPROVER);
            return "supplier-contract-secret";
        }

        /**
         * 记录一次受保护写操作，用于证明未认证请求不会产生业务副作用。
         */
        @PostMapping("/write")
        void write()
        {
            writeProbe.recordWrite();
        }

    }

    /**
     * 记录测试写操作次数的内存探针。
     */
    static class TestWriteProbe
    {

        private final AtomicInteger writeCount = new AtomicInteger();

        /**
         * 记录一次写操作。
         */
        void recordWrite()
        {
            writeCount.incrementAndGet();
        }

        /**
         * 返回已经记录的写操作次数。
         *
         * @return 写操作次数
         */
        int count()
        {
            return writeCount.get();
        }

        /**
         * 清空写操作次数。
         */
        void reset()
        {
            writeCount.set(0);
        }

    }

}
