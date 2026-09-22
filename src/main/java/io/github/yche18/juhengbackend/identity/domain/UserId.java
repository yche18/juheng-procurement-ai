package io.github.yche18.juhengbackend.identity.domain;

/**
 * 在据衡内部稳定标识一个已认证用户。
 *
 * @param value 非空的用户标识值
 */
public record UserId(String value)
{

    /**
     * 校验并规范化用户标识，避免空白身份进入应用层。
     */
    public UserId
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException("User ID must not be blank");
        }
        value = value.trim();
    }

    /**
     * 返回便于日志和响应使用的用户标识文本。
     *
     * @return 用户标识值
     */
    @Override
    public String toString()
    {
        return value;
    }

}
