package io.github.yche18.juhengbackend.procurement;

import io.github.yche18.juhengbackend.audit.application.AuditEventStore;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import io.github.yche18.juhengbackend.procurement.application.UpdateProcurementRequestCommand;
import io.github.yche18.juhengbackend.procurement.application.UpdateProcurementRequestService;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用真实 PostgreSQL 验证修改草稿和修改审计属于同一本地事务。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(UpdateProcurementRequestRollbackIntegrationTests.FailingAuditConfiguration.class)
class UpdateProcurementRequestRollbackIntegrationTests
{

    private static final UUID REQUEST_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000001");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_update_rollback_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private UpdateProcurementRequestService updateService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 为每个测试重建同一份原始草稿快照。
     */
    @BeforeEach
    void resetBusinessData()
    {
        jdbcTemplate.update("DELETE FROM audit_event");
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
                "PR-20260922-701",
                "demo-requester",
                "原始标题",
                "原始采购目的",
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
                "原始采购项",
                "LAPTOP",
                "原始规格",
                new BigDecimal("1"),
                "台",
                new BigDecimal("10.00"));
    }

    /**
     * 验证审计追加失败时主表条件更新和采购项替换都会回滚。
     */
    @Test
    void rollsBackRequestAndItemsWhenAuditAppendFails()
    {
        CurrentUser requester = new CurrentUser(
                new UserId("demo-requester"),
                Set.of(Role.REQUESTER));

        assertThatThrownBy(() -> updateService.update(REQUEST_ID, validCommand(), requester))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated audit persistence failure");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM procurement_request WHERE id = ?",
                String.class,
                REQUEST_ID)).isEqualTo("原始标题");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM procurement_request WHERE id = ?",
                Long.class,
                REQUEST_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM procurement_item WHERE procurement_request_id = ?",
                String.class,
                REQUEST_ID)).isEqualTo("原始采购项");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM procurement_item WHERE procurement_request_id = ?",
                Integer.class,
                REQUEST_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event",
                Integer.class)).isZero();
    }

    /**
     * 创建事务回滚测试使用的合法更新命令。
     *
     * @return 修改采购草稿命令
     */
    private UpdateProcurementRequestCommand validCommand()
    {
        return new UpdateProcurementRequestCommand(
                0L,
                "不应提交的标题",
                "不应提交的目的",
                "采购部",
                LocalDate.of(2026, 11, 1),
                List.of(new UpdateProcurementRequestCommand.ItemCommand(
                        "不应提交的采购项",
                        "MONITOR",
                        "27 英寸",
                        new BigDecimal("2"),
                        "台",
                        new BigDecimal("999.00"))));
    }

    /**
     * 提供始终失败的审计端口，强制触发应用事务回滚。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditConfiguration
    {

        /**
         * 创建优先级更高的失败审计替身。
         *
         * @return 写入时抛出异常的审计端口
         */
        @Bean
        @Primary
        AuditEventStore failingAuditEventStore()
        {
            return event ->
            {
                throw new IllegalStateException("Simulated audit persistence failure");
            };
        }
    }
}
