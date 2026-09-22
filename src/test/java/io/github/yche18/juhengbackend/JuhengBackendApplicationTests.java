package io.github.yche18.juhengbackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 验证据衡后端的最小 Spring 应用上下文能够正常启动。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("no-database")
class JuhengBackendApplicationTests
{

    /**
     * 加载最小应用上下文；能够完成测试即表示启动配置有效。
     */
    @Test
    void contextLoads()
    {
    }

}
