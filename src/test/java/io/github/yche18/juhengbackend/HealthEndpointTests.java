package io.github.yche18.juhengbackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTests
{

    @Autowired
    private MockMvc mockMvc;

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
