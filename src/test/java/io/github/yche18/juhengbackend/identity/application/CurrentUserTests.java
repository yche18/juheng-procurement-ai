package io.github.yche18.juhengbackend.identity.application;

import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证可信当前用户上下文的角色集合语义。
 */
class CurrentUserTests
{

    /**
     * 验证多角色身份会完整保留，且创建后的角色集合不可被外部修改。
     */
    @Test
    void preservesCompleteImmutableRoleSet()
    {
        Set<Role> sourceRoles = new HashSet<>(Set.of(Role.REQUESTER, Role.APPROVER));

        CurrentUser currentUser = new CurrentUser(new UserId("user-1"), sourceRoles);
        sourceRoles.clear();

        assertThat(currentUser.roles()).containsExactlyInAnyOrder(Role.REQUESTER, Role.APPROVER);
        assertThat(currentUser.hasRole(Role.REQUESTER)).isTrue();
        assertThatThrownBy(() -> currentUser.roles().add(Role.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

}
