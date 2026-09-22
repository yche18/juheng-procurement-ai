package io.github.yche18.juhengbackend.procurement;

import io.github.yche18.juhengbackend.audit.application.AuditEventStore;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import io.github.yche18.juhengbackend.procurement.application.SubmitProcurementRequestCommand;
import io.github.yche18.juhengbackend.procurement.application.SubmitProcurementRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用真实 PostgreSQL 验证提交末端失败时所有写入整体回滚。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(SubmitProcurementRequestRollbackIntegrationTests.FailingAuditConfiguration.class)
class SubmitProcurementRequestRollbackIntegrationTests
{

    private static final UUID REQUEST_ID = UUID.fromString(
            "80000000-0000-0000-0000-000000000001");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_submit_rollback_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private SubmitProcurementRequestService submitService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 为每个测试重建同一份完整采购草稿。
     */
    @BeforeEach
    void resetBusinessData()
    {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM approval_task");
        jdbcTemplate.update("DELETE FROM procurement_item");
        jdbcTemplate.update("DELETE FROM procurement_request");
        Instant createdAt = Instant.parse("2026-09-22T01:02:03Z");
        jdbcTemplate.update("""
                INSERT INTO procurement_request (
                    id, business_number, creator_id, title, purpose, department,
                    expected_delivery_date, currency, estimated_total, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                REQUEST_ID,
                "PR-20260922-800",
                "demo-requester",
                "事务回滚测试申请",
                "验证提交原子性",
                "研发部",
                Date.valueOf(LocalDate.of(2026, 10, 1)),
                "CNY",
                new BigDecimal("10.00"),
                "DRAFT",
                0L,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        jdbcTemplate.update("""
                INSERT INTO procurement_item (
                    id, procurement_request_id, line_no, name, category_code,
                    specification, quantity, unit, estimated_unit_price
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                REQUEST_ID,
                1,
                "开发笔记本",
                "LAPTOP",
                "32GB 内存",
                new BigDecimal("1"),
                "台",
                new BigDecimal("10.00"));
    }

    /**
     * 验证任务已经插入后审计失败，申请、任务、审计和幂等记录仍全部回滚。
     */
    @Test
    void rollsBackEverySubmissionWriteWhenAuditFails()
    {
        CurrentUser requester = new CurrentUser(
                new UserId("demo-requester"),
                Set.of(Role.REQUESTER));

        assertThatThrownBy(() -> submitService.submit(
                REQUEST_ID,
                new SubmitProcurementRequestCommand(0L),
                "rollback-on-audit",
                requester))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated submission audit failure");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM procurement_request WHERE id = ?",
                String.class,
                REQUEST_ID)).isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM procurement_request WHERE id = ?",
                Long.class,
                REQUEST_ID)).isZero();
        assertThat(rowCount("approval_task")).isZero();
        assertThat(rowCount("audit_event")).isZero();
        assertThat(rowCount("idempotency_record")).isZero();
    }

    /**
     * 查询受控测试表的总行数。
     *
     * @param tableName 表名
     * @return 行数
     */
    private int rowCount(String tableName)
    {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class);
    }

    /**
     * 提供始终失败的审计端口，强制验证提交事务整体回滚。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditConfiguration
    {

        /**
         * 创建优先级更高的提交审计失败替身。
         *
         * @return 写入时抛出异常的审计端口
         */
        @Bean
        @Primary
        AuditEventStore failingAuditEventStore()
        {
            return event ->
            {
                throw new IllegalStateException("Simulated submission audit failure");
            };
        }
    }
}
