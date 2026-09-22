package io.github.yche18.juhengbackend.identity.application;

/**
 * 为 Web 适配器提供可信当前用户的端口。
 */
public interface CurrentUserProvider
{

    /**
     * 从当前受信任认证上下文读取用户身份。
     *
     * @return 当前用户及其完整角色集合
     * @throws io.github.yche18.juhengbackend.common.error.AuthenticationRequiredException
     *         当前请求没有有效认证身份时抛出
     */
    CurrentUser getCurrentUser();

}
