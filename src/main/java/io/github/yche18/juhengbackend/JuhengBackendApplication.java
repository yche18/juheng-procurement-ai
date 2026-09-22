package io.github.yche18.juhengbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 据衡后端应用的启动入口。
 */
@SpringBootApplication
public class JuhengBackendApplication
{

    /**
     * 启动 Spring Boot 应用并创建应用上下文。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args)
    {
        SpringApplication.run(JuhengBackendApplication.class, args);
    }

}
