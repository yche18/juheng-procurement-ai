package io.github.yche18.juhengbackend.approval.web;

import io.github.yche18.juhengbackend.approval.application.ApprovalTaskSummary;
import io.github.yche18.juhengbackend.common.application.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 当前审批人的审批任务分页响应。
 *
 * @param content 当前页任务摘要
 * @param page 从零开始的页码
 * @param size 请求的单页大小
 * @param totalElements 符合条件的总记录数
 * @param totalPages 总页数
 */
public record ApprovalTaskPageResponse(
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
    public static ApprovalTaskPageResponse from(PageResult<ApprovalTaskSummary> result)
    {
        List<SummaryResponse> content = result.content().stream()
                .map(SummaryResponse::from)
                .toList();
        return new ApprovalTaskPageResponse(
                content,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    /**
     * 分页列表中的审批任务摘要。
     *
     * @param id 任务标识
     * @param status 任务状态
     * @param version 任务版本
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @param procurementRequest 关联采购申请摘要
     */
    public record SummaryResponse(
            UUID id,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt,
            ProcurementRequestSummaryResponse procurementRequest)
    {

        /**
         * 将应用任务摘要映射为 HTTP 摘要。
         *
         * @param summary 应用任务摘要
         * @return HTTP 任务摘要
         */
        private static SummaryResponse from(ApprovalTaskSummary summary)
        {
            return new SummaryResponse(
                    summary.id(),
                    summary.status(),
                    summary.version(),
                    summary.createdAt(),
                    summary.updatedAt(),
                    ProcurementRequestSummaryResponse.from(summary.procurementRequest()));
        }
    }

    /**
     * 审批任务列表中的采购申请摘要。
     *
     * @param id 申请标识
     * @param businessNumber 业务编号
     * @param creatorId 创建者
     * @param title 标题
     * @param department 申请部门
     * @param expectedDeliveryDate 期望交付日期
     * @param currency 币种
     * @param estimatedTotal 预计总额
     * @param status 申请状态
     * @param version 申请版本
     */
    public record ProcurementRequestSummaryResponse(
            UUID id,
            String businessNumber,
            String creatorId,
            String title,
            String department,
            LocalDate expectedDeliveryDate,
            String currency,
            BigDecimal estimatedTotal,
            String status,
            long version)
    {

        /**
         * 将应用申请摘要映射为 HTTP 摘要。
         *
         * @param summary 应用申请摘要
         * @return HTTP 申请摘要
         */
        private static ProcurementRequestSummaryResponse from(
                ApprovalTaskSummary.ProcurementRequestSummary summary)
        {
            return new ProcurementRequestSummaryResponse(
                    summary.id(),
                    summary.businessNumber(),
                    summary.creatorId(),
                    summary.title(),
                    summary.department(),
                    summary.expectedDeliveryDate(),
                    summary.currency(),
                    summary.estimatedTotal(),
                    summary.status(),
                    summary.version());
        }
    }
}
