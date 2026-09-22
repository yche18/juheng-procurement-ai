package io.github.yche18.juhengbackend.procurement.domain;

import io.github.yche18.juhengbackend.common.error.BusinessConflictException;
import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证采购草稿聚合的服务端控制字段、金额规则和集合边界。
 */
class ProcurementRequestTests
{

    /**
     * 验证每行先按 HALF_UP 保留两位，再汇总为申请预计总额。
     */
    @Test
    void calculatesTotalFromRoundedLineAmounts()
    {
        ProcurementItem firstItem = item(1, "1.005", "19.99");
        ProcurementItem secondItem = item(2, "2", "10.00");

        ProcurementRequest request = request(List.of(firstItem, secondItem));

        assertThat(firstItem.estimatedLineTotal().amount()).isEqualByComparingTo("20.09");
        assertThat(secondItem.estimatedLineTotal().amount()).isEqualByComparingTo("20.00");
        assertThat(request.estimatedTotal().amount()).isEqualByComparingTo("40.09");
    }

    /**
     * 验证创建入口只能产生 CNY、DRAFT 和初始版本 0。
     */
    @Test
    void createsDraftWithServerControlledDefaults()
    {
        Instant createdAt = Instant.parse("2026-09-22T01:02:03Z");

        ProcurementRequest request = ProcurementRequest.createDraft(
                UUID.randomUUID(),
                new BusinessNumber("PR-20260922-1"),
                new UserId("demo-requester"),
                "研发电脑采购",
                "补充开发设备",
                "研发部",
                LocalDate.of(2026, 10, 1),
                List.of(item(1, "1", "8999.00")),
                createdAt);

        assertThat(request.currency()).isEqualTo(Currency.CNY);
        assertThat(request.status()).isEqualTo(ProcurementRequestStatus.DRAFT);
        assertThat(request.version()).isZero();
        assertThat(request.createdAt()).isEqualTo(createdAt);
        assertThat(request.updatedAt()).isEqualTo(createdAt);
    }

    /**
     * 验证非法精度、非正数量和负单价会在领域边界被拒绝。
     */
    @Test
    void rejectsInvalidQuantityAndMoney()
    {
        assertThatThrownBy(() -> item(1, "1.00001", "10.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("four decimal places");
        assertThatThrownBy(() -> item(1, "0", "10.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> item(1, "1", "10.001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
        assertThatThrownBy(() -> item(1, "1", "-0.01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    /**
     * 验证聚合至少包含一项，并防御创建后从外部修改采购项列表。
     */
    @Test
    void requiresItemsAndCopiesInputCollection()
    {
        assertThatThrownBy(() -> request(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");

        List<ProcurementItem> mutableItems = new ArrayList<>();
        mutableItems.add(item(1, "1", "10.00"));
        ProcurementRequest request = request(mutableItems);
        mutableItems.clear();

        assertThat(request.items()).hasSize(1);
        assertThatThrownBy(() -> request.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证修改草稿会生成新聚合快照、递增版本并重新计算总额。
     */
    @Test
    void updatesDraftAsNewVersionAndRecalculatesTotal()
    {
        ProcurementRequest current = request(List.of(item(1, "1", "10.00")));
        Instant updatedAt = Instant.parse("2026-09-22T02:03:04Z");

        ProcurementRequest updated = current.updateDraft(
                0,
                "更新后的标题",
                "更新后的目的",
                "采购部",
                LocalDate.of(2026, 11, 1),
                List.of(item(1, "2", "20.00")),
                updatedAt);

        assertThat(updated.id()).isEqualTo(current.id());
        assertThat(updated.businessNumber()).isEqualTo(current.businessNumber());
        assertThat(updated.creatorId()).isEqualTo(current.creatorId());
        assertThat(updated.currency()).isEqualTo(Currency.CNY);
        assertThat(updated.status()).isEqualTo(ProcurementRequestStatus.DRAFT);
        assertThat(updated.title()).isEqualTo("更新后的标题");
        assertThat(updated.estimatedTotal().amount()).isEqualByComparingTo("40.00");
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.createdAt()).isEqualTo(current.createdAt());
        assertThat(updated.updatedAt()).isEqualTo(updatedAt);
        assertThat(current.title()).isEqualTo("研发电脑采购");
        assertThat(current.version()).isZero();
    }

    /**
     * 验证非草稿状态不能通过普通修改行为改变核心字段。
     */
    @Test
    void rejectsUpdatesOutsideDraftState()
    {
        ProcurementRequest submitted = restoredRequest(ProcurementRequestStatus.SUBMITTED, 2);

        assertThatThrownBy(() -> submitted.updateDraft(
                2,
                "非法修改",
                "非法修改",
                "研发部",
                LocalDate.of(2026, 11, 1),
                List.of(item(1, "1", "10.00")),
                Instant.parse("2026-09-22T02:03:04Z")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("draft");
    }

    /**
     * 验证旧版本在进入持久化前就能得到明确并发冲突。
     */
    @Test
    void rejectsStaleDraftVersion()
    {
        ProcurementRequest current = restoredRequest(ProcurementRequestStatus.DRAFT, 3);

        assertThatThrownBy(() -> current.updateDraft(
                2,
                "过期版本修改",
                "过期版本修改",
                "研发部",
                LocalDate.of(2026, 11, 1),
                List.of(item(1, "1", "10.00")),
                Instant.parse("2026-09-22T02:03:04Z")))
                .isInstanceOf(ConcurrentUpdateException.class)
                .hasMessageContaining("current version is 3");
    }

    /**
     * 验证提交草稿会生成 SUBMITTED 新快照，同时保留核心字段并递增版本。
     */
    @Test
    void submitsDraftAsNewVersion()
    {
        ProcurementRequest draft = restoredRequest(ProcurementRequestStatus.DRAFT, 3);
        Instant submittedAt = Instant.parse("2026-09-22T03:04:05Z");

        ProcurementRequest submitted = draft.submit(3, submittedAt);

        assertThat(submitted.id()).isEqualTo(draft.id());
        assertThat(submitted.title()).isEqualTo(draft.title());
        assertThat(submitted.items()).isEqualTo(draft.items());
        assertThat(submitted.estimatedTotal()).isEqualTo(draft.estimatedTotal());
        assertThat(submitted.status()).isEqualTo(ProcurementRequestStatus.SUBMITTED);
        assertThat(submitted.version()).isEqualTo(4);
        assertThat(submitted.updatedAt()).isEqualTo(submittedAt);
        assertThat(draft.status()).isEqualTo(ProcurementRequestStatus.DRAFT);
        assertThat(draft.version()).isEqualTo(3);
    }

    /**
     * 验证非草稿状态和过期版本都不能执行领域提交转换。
     */
    @Test
    void rejectsInvalidSubmissionStateAndVersion()
    {
        Instant submittedAt = Instant.parse("2026-09-22T03:04:05Z");
        ProcurementRequest submitted = restoredRequest(ProcurementRequestStatus.SUBMITTED, 2);
        ProcurementRequest draft = restoredRequest(ProcurementRequestStatus.DRAFT, 2);

        assertThatThrownBy(() -> submitted.submit(2, submittedAt))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("draft");
        assertThatThrownBy(() -> draft.submit(1, submittedAt))
                .isInstanceOf(ConcurrentUpdateException.class)
                .hasMessageContaining("current version is 2");
    }

    /**
     * 验证已提交申请可以进入批准或驳回终态并递增版本。
     */
    @Test
    void marksSubmittedRequestAsApprovedOrRejected()
    {
        ProcurementRequest submitted = restoredRequest(ProcurementRequestStatus.SUBMITTED, 4);
        Instant decidedAt = Instant.parse("2026-09-23T03:04:05Z");

        ProcurementRequest approved = submitted.markApproved(decidedAt);
        ProcurementRequest rejected = submitted.markRejected(decidedAt);

        assertThat(approved.status()).isEqualTo(ProcurementRequestStatus.APPROVED);
        assertThat(rejected.status()).isEqualTo(ProcurementRequestStatus.REJECTED);
        assertThat(approved.version()).isEqualTo(5L);
        assertThat(rejected.version()).isEqualTo(5L);
        assertThat(approved.updatedAt()).isEqualTo(decidedAt);
    }

    /**
     * 验证草稿和既有终态不能通过最终审批转换改变状态。
     */
    @Test
    void rejectsDecisionOutsideSubmittedState()
    {
        Instant decidedAt = Instant.parse("2026-09-23T03:04:05Z");

        assertThatThrownBy(() -> restoredRequest(
                ProcurementRequestStatus.DRAFT, 0).markApproved(decidedAt))
                .isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> restoredRequest(
                ProcurementRequestStatus.APPROVED, 2).markRejected(decidedAt))
                .isInstanceOf(BusinessConflictException.class);
    }

    /**
     * 创建测试用采购草稿。
     *
     * @param items 采购项集合
     * @return 合法采购草稿
     */
    private ProcurementRequest request(List<ProcurementItem> items)
    {
        return ProcurementRequest.createDraft(
                UUID.randomUUID(),
                new BusinessNumber("PR-20260922-1"),
                new UserId("demo-requester"),
                "研发电脑采购",
                "补充开发设备",
                "研发部",
                LocalDate.of(2026, 10, 1),
                items,
                Instant.parse("2026-09-22T01:02:03Z"));
    }

    /**
     * 恢复指定状态和版本的测试申请，用于验证修改状态机与并发保护。
     *
     * @param status 申请状态
     * @param version 持久化版本
     * @return 已恢复的采购申请
     */
    private ProcurementRequest restoredRequest(ProcurementRequestStatus status, long version)
    {
        Instant createdAt = Instant.parse("2026-09-22T01:02:03Z");
        return ProcurementRequest.restore(
                UUID.randomUUID(),
                new BusinessNumber("PR-20260922-1"),
                new UserId("demo-requester"),
                "研发电脑采购",
                "补充开发设备",
                "研发部",
                LocalDate.of(2026, 10, 1),
                Currency.CNY,
                status,
                version,
                createdAt,
                createdAt,
                List.of(item(1, "1", "10.00")));
    }

    /**
     * 创建指定数量和单价的测试采购项。
     *
     * @param lineNumber 行号
     * @param quantity 数量文本
     * @param unitPrice 单价文本
     * @return 采购项
     */
    private ProcurementItem item(int lineNumber, String quantity, String unitPrice)
    {
        return new ProcurementItem(
                UUID.randomUUID(),
                lineNumber,
                "开发笔记本",
                CategoryCode.LAPTOP,
                "32GB 内存",
                new BigDecimal(quantity),
                "台",
                new Money(new BigDecimal(unitPrice), Currency.CNY));
    }
}
