package io.github.yche18.juhengbackend.procurement.infrastructure.persistence;

import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestRepository;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementItem;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 持久化采购申请聚合。
 */
@Repository
@Profile("!no-database")
public class MyBatisProcurementRequestRepository implements ProcurementRequestRepository
{

    private final ProcurementRequestMapper requestMapper;
    private final ProcurementItemMapper itemMapper;

    /**
     * 创建采购申请 Repository 适配器。
     *
     * @param requestMapper 申请主表 Mapper
     * @param itemMapper 采购项 Mapper
     */
    public MyBatisProcurementRequestRepository(
            ProcurementRequestMapper requestMapper,
            ProcurementItemMapper itemMapper)
    {
        this.requestMapper = requestMapper;
        this.itemMapper = itemMapper;
    }

    /**
     * 先保存聚合根再按稳定行号保存全部采购项，任一步失败均由应用事务回滚。
     *
     * @param request 待保存聚合
     */
    @Override
    public void save(ProcurementRequest request)
    {
        int insertedRequests = requestMapper.insert(toRequestDO(request));
        if (insertedRequests != 1)
        {
            throw new IllegalStateException("Expected one procurement request row to be inserted");
        }
        for (ProcurementItem item : request.items())
        {
            int insertedItems = itemMapper.insert(toItemDO(request, item));
            if (insertedItems != 1)
            {
                throw new IllegalStateException("Expected one procurement item row to be inserted");
            }
        }
    }

    /**
     * 将领域聚合根映射为主表数据对象。
     *
     * @param request 领域申请
     * @return 申请持久化对象
     */
    private ProcurementRequestDO toRequestDO(ProcurementRequest request)
    {
        return new ProcurementRequestDO(
                request.id(),
                request.businessNumber().value(),
                request.creatorId().value(),
                request.title(),
                request.purpose(),
                request.department(),
                request.expectedDeliveryDate(),
                request.currency().name(),
                request.estimatedTotal().amount(),
                request.status().name(),
                request.version(),
                request.createdAt(),
                request.updatedAt());
    }

    /**
     * 将聚合内采购项映射为明细表数据对象。
     *
     * @param request 所属申请
     * @param item 领域采购项
     * @return 采购项持久化对象
     */
    private ProcurementItemDO toItemDO(ProcurementRequest request, ProcurementItem item)
    {
        return new ProcurementItemDO(
                item.id(),
                request.id(),
                item.lineNumber(),
                item.name(),
                item.categoryCode().name(),
                item.specification(),
                item.quantity(),
                item.unit(),
                item.estimatedUnitPrice().amount());
    }
}
