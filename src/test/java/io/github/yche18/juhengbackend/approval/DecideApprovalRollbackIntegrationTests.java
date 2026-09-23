package io.github.yche18.juhengbackend.approval;

import io.github.yche18.juhengbackend.approval.application.DecideApprovalApplicationService;
import io.github.yche18.juhengbackend.approval.application.DecideApprovalCommand;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;
import io.github.yche18.juhengbackend.audit.application.AuditEventStore;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.junit.jupiter.api.AfterEach;
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
 * 使用真实 PostgreSQL 验证决定或审计写入失败时审批事务完整回滚。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(DecideApprovalRollbackIntegrationTests.FailingAuditConfiguration.class)
class DecideApprovalRollbackIntegrationTests
{

    private static final UUID REQUEST_ID = UUID.fromString(
            "90000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString(
            "90000000-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_decide_rollback_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private DecideApprovalApplicationService decisionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 清除故障触发器和业务数据，并重建一份可审批申请与任务。
     */
    @BeforeEach
    void resetBusinessData()
    {
        removeDecisionFailureTrigger();
        jdbcTemplate.update("DELETE FROM approval_decision");
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
                "PR-20260923-900",
                "demo-requester",
                "审批事务回滚测试申请",
                "验证审批写入原子性",
                "采购部",
                Date.valueOf(LocalDate.of(2026, 10, 1)),
                "CNY",
                new BigDecimal("10.00"),
                "SUBMITTED",
                1L,
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
                "办公椅",
                "OFFICE_CHAIR",
                "人体工学",
                new BigDecimal("1"),
                "把",
                new BigDecimal("10.00"));
        jdbcTemplate.update("""
                INSERT INTO approval_task (
                    id, procurement_request_id, assignee_id, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, 'demo-approver', 'PENDING', 0, ?, ?)
                """,
                TASK_ID,
                REQUEST_ID,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    /**
     * 每个测试结束后移除数据库故障注入对象，避免影响容器中的后续测试。
     */
    @AfterEach
    void cleanFailureTrigger()
    {
        removeDecisionFailureTrigger();
    }

    /**
     * 验证任务已条件更新后决定插入失败，任务、申请、决定和幂等记录仍全部回滚。
     */
    @Test
    void rollsBackEveryWriteWhenDecisionInsertFails()
    {
        installDecisionFailureTrigger();

        assertThatThrownBy(() -> approve("decision-write-failure"))
                .isInstanceOf(RuntimeException.class);

        assertOriginalStateWithoutSideEffects();
    }

    /**
     * 验证决定和双聚合已经写入后审计失败，整个本地事务仍全部回滚。
     */
    @Test
    void rollsBackEveryWriteWhenAuditFails()
    {
        assertThatThrownBy(() -> approve("audit-write-failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated approval audit failure");

        assertOriginalStateWithoutSideEffects();
    }

    /**
     * 使用可信审批人上下文执行一次批准命令。
     */
    private void approve(String key)
    {
        decisionService.decide(
                TASK_ID,
                new DecideApprovalCommand(ApprovalDecisionType.APPROVED, 0L, null),
                key,
                new CurrentUser(new UserId("demo-approver"), Set.of(Role.APPROVER)));
    }

    /**
     * 在审批决定插入前抛出 PostgreSQL 异常，以验证插入失败回滚路径。
     */
    private void installDecisionFailureTrigger()
    {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_approval_decision_write()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'simulated approval decision failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_fail_approval_decision_write
                BEFORE INSERT ON approval_decision
                FOR EACH ROW
                EXECUTE FUNCTION fail_approval_decision_write()
                """);
    }

    /**
     * 安全移除可重复创建的数据库故障注入触发器和函数。
     */
    private void removeDecisionFailureTrigger()
    {
        jdbcTemplate.execute(
                "DROP TRIGGER IF EXISTS trg_fail_approval_decision_write ON approval_decision");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_approval_decision_write()");
    }

    /**
     * 断言失败事务未改变任务和申请，也未留下决定、审计或幂等占位。
     */
    private void assertOriginalStateWithoutSideEffects()
    {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM approval_task WHERE id = ?",
                String.class,
                TASK_ID)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM approval_task WHERE id = ?",
                Long.class,
                TASK_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM procurement_request WHERE id = ?",
                String.class,
                REQUEST_ID)).isEqualTo("SUBMITTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM procurement_request WHERE id = ?",
                Long.class,
                REQUEST_ID)).isEqualTo(1L);
        assertThat(rowCount("approval_decision")).isZero();
        assertThat(rowCount("audit_event")).isZero();
        assertThat(rowCount("idempotency_record")).isZero();
    }

    /**
     * 查询指定表的总行数。
     */
    private int rowCount(String tableName)
    {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    /**
     * 提供始终失败的审计端口，以验证审批事务整体回滚。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditConfiguration
    {

        /**
         * 创建优先级更高的审批审计失败替身。
         *
         * @return 写入时抛出异常的审计端口
         */
        @Bean
        @Primary
        AuditEventStore failingAuditEventStore()
        {
            return event ->
            {
                throw new IllegalStateException("Simulated approval audit failure");
            };
        }
    }
}
