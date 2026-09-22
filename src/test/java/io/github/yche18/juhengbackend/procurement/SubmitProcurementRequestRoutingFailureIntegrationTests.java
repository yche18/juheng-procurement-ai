package io.github.yche18.juhengbackend.procurement;

import io.github.yche18.juhengbackend.approval.application.ApprovalRoutingPolicy;
import io.github.yche18.juhengbackend.approval.domain.ApprovalRoutingFailureReason;
import io.github.yche18.juhengbackend.approval.domain.ApprovalRoutingResult;
import io.github.yche18.juhengbackend.common.error.ApprovalRoutingException;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用真实事务验证所有审批路由失败都会保持草稿且不留下幂等占位。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(SubmitProcurementRequestRoutingFailureIntegrationTests.RoutingTestConfiguration.class)
class SubmitProcurementRequestRoutingFailureIntegrationTests
{

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_submit_routing_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private SubmitProcurementRequestService submitService;

    @Autowired
    private MutableApprovalRoutingPolicy routingPolicy;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 清除上一个路由场景留下的数据。
     */
    @BeforeEach
    void clearBusinessData()
    {
        clearTables();
    }

    /**
     * 验证无审批人、多个审批人和自审三种失败均整体回滚。
     */
    @Test
    void rollsBackEveryExplicitRoutingFailure()
    {
        List<ApprovalRoutingFailureReason> reasons = List.of(
                ApprovalRoutingFailureReason.NO_APPROVER,
                ApprovalRoutingFailureReason.MULTIPLE_APPROVERS,
                ApprovalRoutingFailureReason.SELF_ASSIGNMENT);

        for (ApprovalRoutingFailureReason reason : reasons)
        {
            clearTables();
            UUID requestId = insertCompleteDraft();
            routingPolicy.failWith(reason);

            assertThatThrownBy(() -> submitService.submit(
                    requestId,
                    new SubmitProcurementRequestCommand(0L),
                    "routing-" + reason.name().toLowerCase(),
                    requester()))
                    .isInstanceOf(ApprovalRoutingException.class)
                    .hasMessageContaining(reason.name());

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM procurement_request WHERE id = ?",
                    String.class,
                    requestId)).isEqualTo("DRAFT");
            assertThat(rowCount("approval_task")).isZero();
            assertThat(rowCount("audit_event")).isZero();
            assertThat(rowCount("idempotency_record")).isZero();
        }
    }

    /**
     * 按外键依赖顺序清理测试业务表。
     */
    private void clearTables()
    {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM approval_task");
        jdbcTemplate.update("DELETE FROM procurement_item");
        jdbcTemplate.update("DELETE FROM procurement_request");
    }

    /**
     * 插入一份合法完整草稿。
     *
     * @return 草稿标识
     */
    private UUID insertCompleteDraft()
    {
        UUID requestId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-22T01:02:03Z");
        jdbcTemplate.update("""
                INSERT INTO procurement_request (
                    id, business_number, creator_id, title, purpose, department,
                    expected_delivery_date, currency, estimated_total, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId,
                "PR-20260922-" + Integer.toUnsignedString(requestId.hashCode()),
                "demo-requester",
                "路由失败测试申请",
                "验证失败关闭",
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
                requestId,
                1,
                "开发笔记本",
                "LAPTOP",
                "32GB 内存",
                new BigDecimal("1"),
                "台",
                new BigDecimal("10.00"));
        return requestId;
    }

    /**
     * 创建测试使用的可信申请人。
     *
     * @return REQUESTER 当前用户
     */
    private CurrentUser requester()
    {
        return new CurrentUser(
                new UserId("demo-requester"),
                Set.of(Role.REQUESTER));
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
     * 提供测试可切换的主路由策略。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class RoutingTestConfiguration
    {

        /**
         * 创建优先级更高且可由测试设置失败原因的路由策略。
         *
         * @return 可变路由测试替身
         */
        @Bean
        @Primary
        MutableApprovalRoutingPolicy mutableApprovalRoutingPolicy()
        {
            return new MutableApprovalRoutingPolicy();
        }
    }

    /**
     * 只用于事务集成测试的显式失败路由策略。
     */
    static class MutableApprovalRoutingPolicy implements ApprovalRoutingPolicy
    {

        private final AtomicReference<ApprovalRoutingFailureReason> reason =
                new AtomicReference<>(ApprovalRoutingFailureReason.NO_APPROVER);

        /**
         * 设置下一次路由应返回的失败原因。
         *
         * @param failureReason 失败原因
         */
        void failWith(ApprovalRoutingFailureReason failureReason)
        {
            reason.set(failureReason);
        }

        /**
         * 返回测试指定的失败结果。
         *
         * @param requesterId 申请创建者
         * @return 路由失败结果
         */
        @Override
        public ApprovalRoutingResult resolveAssignee(UserId requesterId)
        {
            return ApprovalRoutingResult.failure(reason.get());
        }
    }
}
