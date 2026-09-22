package io.github.yche18.juhengbackend.identity.infrastructure.security;

import io.github.yche18.juhengbackend.common.web.error.ApiErrorResponseWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.ObjectMapper;

/**
 * US-003 的最小无状态认证配置。
 *
 * <p>R1 使用内存演示身份，不建立用户表，也不接入 OAuth2 身份供应商。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration
{

    /**
     * 配置无状态 HTTP Basic、安全失败响应和受保护 API 范围。
     *
     * @param http Spring Security HTTP 配置入口
     * @param authenticationEntryPoint 未认证响应入口
     * @param accessDeniedHandler 无权访问响应处理器
     * @return 应用使用的安全过滤器链
     * @throws Exception Spring Security 构建过滤器链失败时抛出
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler) throws Exception
    {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * 创建四个固定的本地演示身份，覆盖三个单角色和一个多角色场景。
     *
     * @param passwordEncoder 密码编码器
     * @param demoPassword 可由环境变量覆盖的本地演示密码
     * @return 内存用户查询服务
     */
    @Bean
    UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${juheng.security.demo-password}") String demoPassword)
    {
        if (demoPassword == null || demoPassword.isBlank())
        {
            throw new IllegalArgumentException("Demo password must not be blank");
        }

        String encodedPassword = passwordEncoder.encode(demoPassword);
        return new InMemoryUserDetailsManager(
                User.withUsername("demo-requester")
                        .password(encodedPassword)
                        .roles("REQUESTER")
                        .build(),
                User.withUsername("demo-approver")
                        .password(encodedPassword)
                        .roles("APPROVER")
                        .build(),
                User.withUsername("demo-admin")
                        .password(encodedPassword)
                        .roles("ADMIN")
                        .build(),
                User.withUsername("demo-requester-approver")
                        .password(encodedPassword)
                        .roles("REQUESTER", "APPROVER")
                        .build());
    }

    /**
     * 创建 BCrypt 密码编码器，避免在内存用户服务中保存明文密码。
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    /**
     * 创建过滤器链使用的统一错误响应写入器。
     *
     * @param objectMapper Spring Boot 统一 JSON 映射器
     * @return 错误响应写入器
     */
    @Bean
    ApiErrorResponseWriter apiErrorResponseWriter(ObjectMapper objectMapper)
    {
        return new ApiErrorResponseWriter(objectMapper);
    }

    /**
     * 创建 HTTP Basic 未认证响应入口。
     *
     * @param errorResponseWriter 统一错误响应写入器
     * @return 未认证响应入口
     */
    @Bean
    ApiAuthenticationEntryPoint apiAuthenticationEntryPoint(ApiErrorResponseWriter errorResponseWriter)
    {
        return new ApiAuthenticationEntryPoint(errorResponseWriter);
    }

    /**
     * 创建过滤器链无权访问响应处理器。
     *
     * @param errorResponseWriter 统一错误响应写入器
     * @return 无权访问响应处理器
     */
    @Bean
    ApiAccessDeniedHandler apiAccessDeniedHandler(ApiErrorResponseWriter errorResponseWriter)
    {
        return new ApiAccessDeniedHandler(errorResponseWriter);
    }

}
