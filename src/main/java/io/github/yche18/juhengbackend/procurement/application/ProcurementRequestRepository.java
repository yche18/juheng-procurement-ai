package io.github.yche18.juhengbackend.procurement.application;

import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequest;

/**
 * 保存采购申请聚合的持久化端口。
 */
public interface ProcurementRequestRepository
{

    /**
     * 原子持久化申请主记录及其全部采购项。
     *
     * @param request 待保存聚合
     */
    void save(ProcurementRequest request);
}
