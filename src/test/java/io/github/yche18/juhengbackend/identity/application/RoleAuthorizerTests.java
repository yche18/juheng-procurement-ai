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

    /**
     * 验证具有任一允许角色时，多角色授权允许用例继续执行。
     */
    @Test
    void permitsUserWithAnyAllowedRole()
    {
        CurrentUser requester = new CurrentUser(
                new UserId("requester-1"),
                Set.of(Role.REQUESTER));
        CurrentUser approver = new CurrentUser(
                new UserId("approver-1"),
                Set.of(Role.APPROVER));
        Set<Role> allowedRoles = Set.of(Role.REQUESTER, Role.APPROVER);

        assertThatCode(() -> roleAuthorizer.requireAnyRole(requester, allowedRoles))
                .doesNotThrowAnyException();
        assertThatCode(() -> roleAuthorizer.requireAnyRole(approver, allowedRoles))
                .doesNotThrowAnyException();
    }

    /**
     * 验证不具有任一允许角色时，多角色授权返回统一授权异常。
     */
    @Test
    void deniesUserWithoutAnyAllowedRole()
    {
        CurrentUser administrator = new CurrentUser(
                new UserId("administrator-1"),
                Set.of(Role.ADMIN));

        assertThatThrownBy(() -> roleAuthorizer.requireAnyRole(
                administrator,
                Set.of(Role.REQUESTER, Role.APPROVER)))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

}
