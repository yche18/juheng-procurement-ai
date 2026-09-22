package io.github.yche18.juhengbackend.procurement.web;

import io.github.yche18.juhengbackend.common.application.PageResult;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 当前申请人的采购申请分页响应。
 *
 * @param content 当前页申请摘要
 * @param page 从零开始的页码
 * @param size 请求的单页大小
 * @param totalElements 符合条件的总记录数
 * @param totalPages 总页数
 */
public record ProcurementRequestPageResponse(
        List<SummaryResponse> content,
        int page,
        int size,
        long totalElements,
        long totalPages)
{

    /**
     * 将应用分页结果映射为 HTTP 响应。
     *
     * @param result 应用分页结果
     * @return HTTP 分页响应
     */
    public static ProcurementRequestPageResponse from(PageResult<ProcurementRequestSummary> result)
    {
        List<SummaryResponse> content = result.content().stream()
                .map(SummaryResponse::from)
                .toList();
        return new ProcurementRequestPageResponse(
                content,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    /**
     * 分页列表中的采购申请摘要。
     *
     * @param id 申请标识
     * @param businessNumber 业务编号
     * @param title 标题
     * @param department 申请部门
     * @param expectedDeliveryDate 期望交付日期
     * @param currency 币种
     * @param estimatedTotal 预计总额
     * @param status 状态
     * @param version 版本
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public record SummaryResponse(
            UUID id,
            String businessNumber,
            String title,
            String department,
            LocalDate expectedDeliveryDate,
            String currency,
            BigDecimal estimatedTotal,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt)
    {

        /**
         * 将应用摘要映射为 HTTP 摘要。
         *
         * @param summary 应用摘要
         * @return HTTP 摘要
         */
        private static SummaryResponse from(ProcurementRequestSummary summary)
        {
            return new SummaryResponse(
                    summary.id(),
                    summary.businessNumber(),
                    summary.title(),
                    summary.department(),
                    summary.expectedDeliveryDate(),
                    summary.currency(),
                    summary.estimatedTotal(),
                    summary.status(),
                    summary.version(),
                    summary.createdAt(),
                    summary.updatedAt());
        }
    }
}
