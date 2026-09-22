package io.github.yche18.juhengbackend.identity.web;

import io.github.yche18.juhengbackend.identity.application.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供当前可信认证身份的只读 API。
 */
@RestController
@RequestMapping("/api/current-user")
public class CurrentUserController
{

    private final CurrentUserProvider currentUserProvider;

    /**
     * 创建当前用户 Controller。
     *
     * @param currentUserProvider 可信当前用户提供端口
     */
    public CurrentUserController(CurrentUserProvider currentUserProvider)
    {
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 返回认证上下文中的用户 ID 和完整角色集合。
     *
     * @return 当前用户响应
     */
    @GetMapping
    public CurrentUserResponse getCurrentUser()
    {
        return CurrentUserResponse.from(currentUserProvider.getCurrentUser());
    }

}
