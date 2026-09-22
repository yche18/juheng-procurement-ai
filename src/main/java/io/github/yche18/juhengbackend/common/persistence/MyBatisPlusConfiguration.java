package io.github.yche18.juhengbackend.common.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 配置项目共用的 MyBatis-Plus 数据库插件。
 */
@Configuration(proxyBeanMethods = false)
@Profile("!no-database")
public class MyBatisPlusConfiguration
{

    private static final long MAX_PAGE_SIZE = 100L;

    /**
     * 创建 PostgreSQL 分页拦截器，并在持久化边界再次限制单页大小。
     *
     * @return MyBatis-Plus 插件链
     */
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor()
    {
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
