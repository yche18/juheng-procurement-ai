package io.github.yche18.juhengbackend.common.time;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 提供可在测试中替换的服务端时钟。
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration
{

    /**
     * 创建生产环境使用的 UTC 时钟。
     *
     * @return UTC 系统时钟
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock applicationClock()
    {
        return Clock.systemUTC();
    }
}
