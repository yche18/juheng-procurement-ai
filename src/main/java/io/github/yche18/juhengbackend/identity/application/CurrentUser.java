package io.github.yche18.juhengbackend.identity.application;

import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.util.Objects;
import java.util.Set;

/**
 * 进入应用用例时使用的可信当前用户上下文。
 *
 * @param userId 当前用户的稳定标识
 * @param roles  当前用户在认证上下文中的完整据衡角色集合
 */
public record CurrentUser(UserId userId, Set<Role> roles)
{

    /**
     * 校验必需字段并复制角色集合，防止调用方在创建后篡改身份上下文。
     */
    public CurrentUser
    {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(roles, "Roles must not be null");
        roles = Set.copyOf(roles);
    }

    /**
     * 判断当前用户是否具有指定角色。
     *
     * @param role 需要检查的角色
     * @return 具有该角色时返回 true
     */
    public boolean hasRole(Role role)
    {
        return roles.contains(Objects.requireNonNull(role, "Role must not be null"));
    }

}
