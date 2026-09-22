package io.github.yche18.juhengbackend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class FlywayMigrationIntegrationTests
{

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migratesEmptyPostgreSqlDatabaseAndDoesNotReapplyBaseline()
    {
        assertThat(successfulBaselineMigrations()).isEqualTo(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        MigrateResult repeatedMigration = flyway.migrate();

        assertThat(repeatedMigration.migrationsExecuted).isZero();
        assertThat(successfulBaselineMigrations()).isEqualTo(1);
    }

    private Integer successfulBaselineMigrations()
    {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '1' AND success = TRUE
                """, Integer.class);
    }

}
