package io.github.yche18.juhengbackend.procurement.domain;

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
