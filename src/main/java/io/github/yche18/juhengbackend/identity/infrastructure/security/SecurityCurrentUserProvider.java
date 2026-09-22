package io.github.yche18.juhengbackend.identity.infrastructure.security;

import io.github.yche18.juhengbackend.common.error.AuthenticationRequiredException;
import io.github.yche18.juhengbackend.common.error.AuthorizationDeniedException;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.CurrentUserProvider;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 Spring Security 的认证结果转换为框架无关的当前用户上下文。
 */
@Component
public class SecurityCurrentUserProvider implements CurrentUserProvider
{

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * 从 SecurityContext 读取身份和完整据衡角色集合。
     *
     * @return 当前可信用户
     * @throws AuthenticationRequiredException 没有有效认证时抛出
     * @throws AuthorizationDeniedException 认证上下文包含未知据衡角色时抛出
     */
    @Override
    public CurrentUser getCurrentUser()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken)
        {
            throw new AuthenticationRequiredException("No authenticated principal is available");
        }

        Set<Role> roles = authentication.getAuthorities()
                .stream()
                .map(this::toRole)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());

        return new CurrentUser(new UserId(authentication.getName()), roles);
    }

    /**
     * 将 Spring Security authority 转换为据衡角色，忽略不属于角色命名空间的权限。
     *
     * @param authority Spring Security 权限
     * @return 可识别角色；非角色 authority 返回空
     */
    private Optional<Role> toRole(GrantedAuthority authority)
    {
        String value = authority.getAuthority();
        if (value == null || !value.startsWith(ROLE_PREFIX))
        {
            return Optional.empty();
        }

        String roleName = value.substring(ROLE_PREFIX.length());
        try
        {
            return Optional.of(Role.valueOf(roleName));
        }
        catch (IllegalArgumentException exception)
        {
            throw new AuthorizationDeniedException("Unsupported authenticated role: " + roleName);
        }
    }

}
