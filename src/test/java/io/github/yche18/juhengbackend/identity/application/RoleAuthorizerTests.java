package io.github.yche18.juhengbackend.identity.application;

import io.github.yche18.juhengbackend.common.error.AuthorizationDeniedException;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证应用层显式角色授权行为。
 */
class RoleAuthorizerTests
{

    private final RoleAuthorizer roleAuthorizer = new RoleAuthorizer();

    /**
     * 验证具有所需角色时允许用例继续执行。
     */
    @Test
    void permitsUserWithRequiredRole()
    {
        CurrentUser currentUser = new CurrentUser(
                new UserId("requester-1"),
                Set.of(Role.REQUESTER, Role.APPROVER));

        assertThatCode(() -> roleAuthorizer.requireRole(currentUser, Role.APPROVER))
                .doesNotThrowAnyException();
    }

    /**
     * 验证缺少所需角色时抛出统一授权异常。
     */
    @Test
    void deniesUserWithoutRequiredRole()
    {
        CurrentUser currentUser = new CurrentUser(
                new UserId("requester-1"),
                Set.of(Role.REQUESTER));

        assertThatThrownBy(() -> roleAuthorizer.requireRole(currentUser, Role.APPROVER))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

}
