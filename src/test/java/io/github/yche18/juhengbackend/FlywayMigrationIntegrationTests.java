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

/**
 * 使用真实 PostgreSQL 容器验证 Flyway 迁移链和 US-010 物理表。
 */
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

    /**
     * 验证空库会顺序执行 V1、V2，并且重复迁移不会再次应用已有版本。
     */
    @Test
    void migratesEmptyPostgreSqlDatabaseAndDoesNotReapplyBaseline()
    {
        assertThat(successfulMigrations()).isEqualTo(2);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(existingUs010Tables()).isEqualTo(3);

        MigrateResult repeatedMigration = flyway.migrate();

        assertThat(repeatedMigration.migrationsExecuted).isZero();
        assertThat(successfulMigrations()).isEqualTo(2);
    }

    /**
     * 查询 V1、V2 已经成功执行的迁移数量。
     *
     * @return 成功迁移记录数量
     */
    private Integer successfulMigrations()
    {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('1', '2') AND success = TRUE
                """, Integer.class);
    }

    /**
     * 查询 US-010 本次引入的三个业务表是否都存在。
     *
     * @return 已存在的目标表数量
     */
    private Integer existingUs010Tables()
    {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('procurement_request', 'procurement_item', 'audit_event')
                """, Integer.class);
    }

}
