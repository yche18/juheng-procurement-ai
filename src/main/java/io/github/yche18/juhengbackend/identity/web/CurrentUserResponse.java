package io.github.yche18.juhengbackend.identity.web;

import io.github.yche18.juhengbackend.identity.application.CurrentUser;

import java.util.List;

/**
 * 当前用户 API 的只读响应。
 *
 * @param userId 当前可信用户 ID
 * @param roles 按稳定顺序返回的完整角色集合
 */
public record CurrentUserResponse(String userId, List<String> roles)
{

    /**
     * 从应用层当前用户上下文创建稳定响应，不接收客户端身份声明。
     *
     * @param currentUser 当前可信用户
     * @return 当前用户响应
     */
    public static CurrentUserResponse from(CurrentUser currentUser)
    {
        List<String> roleNames = currentUser.roles()
                .stream()
                .sorted()
                .map(Enum::name)
                .toList();
        return new CurrentUserResponse(currentUser.userId().value(), roleNames);
    }

}
