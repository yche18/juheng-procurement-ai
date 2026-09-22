package io.github.yche18.juhengbackend.identity.application;

import io.github.yche18.juhengbackend.common.error.AuthorizationDeniedException;
import io.github.yche18.juhengbackend.identity.domain.Role;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 在应用边界执行显式角色授权。
 *
 * <p>该组件只判断角色；所有权、任务归属和职责分离仍由具体业务用例继续校验。</p>
 */
@Component
public class RoleAuthorizer
{

    /**
     * 要求当前用户具有指定角色，否则拒绝继续执行用例。
     *
     * @param currentUser 当前可信用户
     * @param requiredRole 用例要求的角色
     * @throws AuthorizationDeniedException 当前用户缺少所需角色时抛出
     */
    public void requireRole(CurrentUser currentUser, Role requiredRole)
    {
        Objects.requireNonNull(currentUser, "Current user must not be null");
        Objects.requireNonNull(requiredRole, "Required role must not be null");
        if (!currentUser.hasRole(requiredRole))
        {
            throw new AuthorizationDeniedException(
                    "User " + currentUser.userId() + " does not have role " + requiredRole);
        }
    }

}
