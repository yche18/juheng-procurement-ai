package io.github.yche18.juhengbackend.procurement.application;

import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequestStatus;

/**
 * 查看当前申请人采购申请列表的应用查询。
 *
 * @param page 从零开始的页码
 * @param size 单页大小
 * @param status 可选状态筛选
 */
public record ListOwnProcurementRequestsQuery(
        int page,
        int size,
        ProcurementRequestStatus status)
{

    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 在应用边界再次保护分页范围，避免非 HTTP 调用绕过协议层校验。
     */
    public ListOwnProcurementRequestsQuery
    {
        if (page < 0)
        {
            throw new IllegalArgumentException("Page must not be negative");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE)
        {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
