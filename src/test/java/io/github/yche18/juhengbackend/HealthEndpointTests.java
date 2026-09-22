package io.github.yche18.juhengbackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证健康检查端点可以稳定访问且不会暴露敏感运行信息。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("no-database")
class HealthEndpointTests
{

    @Autowired
    private MockMvc mockMvc;

    /**
     * 验证健康端点只返回汇总状态和检查分组，不包含组件详情或凭据。
     *
     * @throws Exception MockMvc 执行请求失败时抛出
     */
    @Test
    void healthEndpointReturnsStableNonSensitiveResponse() throws Exception
    {
        String responseBody = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody)
                .isEqualTo("{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"UP\"}");
        assertThat(responseBody.toLowerCase(Locale.ROOT))
                .doesNotContain("component", "detail", "password", "secret", "token", "credential");
    }

}
