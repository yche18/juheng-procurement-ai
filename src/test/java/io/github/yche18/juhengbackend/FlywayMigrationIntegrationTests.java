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
 * 使用真实 PostgreSQL 容器验证 Flyway 迁移链、R1 物理表和查询索引。
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
     * 验证空库会顺序执行 V1 至 V6，并且重复迁移不会再次应用已有版本。
     */
    @Test
    void migratesEmptyPostgreSqlDatabaseAndDoesNotReapplyBaseline()
    {
        assertThat(successfulMigrations()).isEqualTo(6);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(existingUs010Tables()).isEqualTo(3);
        assertThat(existingUs011Indexes()).isEqualTo(2);
        assertThat(existingUs013Tables()).isEqualTo(2);
        assertThat(existingUs014Indexes()).isEqualTo(2);
        assertThat(existingUs015Tables()).isEqualTo(1);

        MigrateResult repeatedMigration = flyway.migrate();

        assertThat(repeatedMigration.migrationsExecuted).isZero();
        assertThat(successfulMigrations()).isEqualTo(6);
    }

    /**
     * 查询 V1 至 V6 已经成功执行的迁移数量。
     *
     * @return 成功迁移记录数量
     */
    private Integer successfulMigrations()
    {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('1', '2', '3', '4', '5', '6') AND success = TRUE
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

    /**
     * 查询 US-011 为创建者分页和状态筛选增加的索引数量。
     *
     * @return 已存在的目标索引数量
     */
    private Integer existingUs011Indexes()
    {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                      'idx_procurement_request_creator_created_id',
                      'idx_procurement_request_creator_status_created_id'
                  )
                """, Integer.class);
    }

    /**
     * 查询 US-013 引入的审批任务与幂等记录表是否存在。
     *
     * @return 已存在的目标表数量
     */
    private Integer existingUs013Tables()
    {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('approval_task', 'idempotency_record')
                """, Integer.class);
    }

    /**
     * 查询 US-014 为审批人分页和任务状态筛选增加的索引数量。
     *
     * @return 已存在的目标索引数量
     */
    private Integer existingUs014Indexes()
    {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                      'idx_approval_task_assignee_created_id',
                      'idx_approval_task_assignee_status_created_id'
                  )
                """, Integer.class);
    }

    /**
     * 查询 US-015 引入的唯一审批决定表是否存在。
     *
     * @return 已存在的目标表数量
     */
    private Integer existingUs015Tables()
    {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'approval_decision'
                """, Integer.class);
    }

}
