package io.github.yche18.juhengbackend.procurement;

import io.github.yche18.juhengbackend.audit.application.AuditEventStore;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import io.github.yche18.juhengbackend.procurement.application.CreateProcurementRequestCommand;
import io.github.yche18.juhengbackend.procurement.application.CreateProcurementRequestService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用真实 PostgreSQL 验证采购申请与审计写入属于同一本地事务。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(CreateProcurementRequestRollbackIntegrationTests.FailingAuditConfiguration.class)
class CreateProcurementRequestRollbackIntegrationTests
{

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("juheng_create_rollback_test")
            .withUsername("juheng_test")
            .withPassword("juheng_test");

    @Autowired
    private CreateProcurementRequestService createService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 清除上一个测试产生的数据。
     */
    @BeforeEach
    void clearBusinessData()
    {
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM procurement_item");
        jdbcTemplate.update("DELETE FROM procurement_request");
    }

    /**
     * 验证追加审计失败时主表和明细表都会随应用事务回滚。
     */
    @Test
    void rollsBackRequestAndItemsWhenAuditAppendFails()
    {
        CurrentUser requester = new CurrentUser(
                new UserId("demo-requester"),
                Set.of(Role.REQUESTER));

        assertThatThrownBy(() -> createService.create(validCommand(), requester))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated audit persistence failure");

        assertThat(rowCount("procurement_request")).isZero();
        assertThat(rowCount("procurement_item")).isZero();
        assertThat(rowCount("audit_event")).isZero();
    }

    /**
     * 创建事务回滚测试使用的合法应用命令。
     *
     * @return 创建采购草稿命令
     */
    private CreateProcurementRequestCommand validCommand()
    {
        return new CreateProcurementRequestCommand(
                "研发电脑采购",
                "补充开发设备",
                "研发部",
                LocalDate.of(2026, 10, 1),
                List.of(new CreateProcurementRequestCommand.ItemCommand(
                        "开发笔记本",
                        "LAPTOP",
                        "32GB 内存",
                        new BigDecimal("1"),
                        "台",
                        new BigDecimal("8999.00"))));
    }

    /**
     * 查询指定业务表的当前行数。
     *
     * @param tableName 测试内固定表名
     * @return 表行数
     */
    private Integer rowCount(String tableName)
    {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    /**
     * 用失败的审计端口替换生产适配器，以验证事务回滚边界。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditConfiguration
    {

        /**
         * 创建始终失败的首选审计端口。
         *
         * @return 失败审计端口
         */
        @Bean
        @Primary
        AuditEventStore failingAuditEventStore()
        {
            return event -> {
                throw new IllegalStateException("Simulated audit persistence failure");
            };
        }
    }
}
