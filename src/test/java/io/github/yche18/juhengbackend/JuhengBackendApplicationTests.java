package io.github.yche18.juhengbackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("no-database")
class JuhengBackendApplicationTests
{

    @Test
    void contextLoads()
    {
    }

}
