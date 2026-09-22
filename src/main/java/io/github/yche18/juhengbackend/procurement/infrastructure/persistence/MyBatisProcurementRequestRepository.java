package io.github.yche18.juhengbackend.procurement.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestRepository;
import io.github.yche18.juhengbackend.procurement.domain.BusinessNumber;
import io.github.yche18.juhengbackend.procurement.domain.CategoryCode;
import io.github.yche18.juhengbackend.procurement.domain.Currency;
import io.github.yche18.juhengbackend.procurement.domain.Money;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementItem;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequest;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequestStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 使用 MyBatis-Plus 持久化采购申请聚合。
 */
@Repository
@Profile("!no-database")
public class MyBatisProcurementRequestRepository implements ProcurementRequestRepository
{

    private static final String REQUEST_ID_COLUMN = "id";
    private static final String CREATOR_ID_COLUMN = "creator_id";
    private static final String REQUEST_FOREIGN_KEY_COLUMN = "procurement_request_id";
    private static final String LINE_NUMBER_COLUMN = "line_no";

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
     * 在 SQL 中同时限定申请 ID 和可信创建者，并加载完整采购项以恢复写模型。
     *
     * @param requestId 申请标识
     * @param creatorId 可信创建者标识
     * @return 当前创建者可访问的聚合
     */
    @Override
    public Optional<ProcurementRequest> findOwnedById(UUID requestId, UserId creatorId)
    {
        ProcurementRequestDO request = requestMapper.selectOne(
                new QueryWrapper<ProcurementRequestDO>()
                        .eq(REQUEST_ID_COLUMN, requestId)
                        .eq(CREATOR_ID_COLUMN, creatorId.value()));
        if (request == null)
        {
            return Optional.empty();
        }
        List<ProcurementItem> items = itemMapper.selectList(
                        new QueryWrapper<ProcurementItemDO>()
                                .eq(REQUEST_FOREIGN_KEY_COLUMN, request.id())
                                .orderByAsc(LINE_NUMBER_COLUMN))
                .stream()
                .map(this::toDomainItem)
                .toList();
        return Optional.of(toDomain(request, items));
    }

    /**
     * 先竞争性更新聚合根；只有主表条件更新成功后才替换全部采购项。
     *
     * <p>调用方的应用事务保证主表、明细和审计任一步失败时整体回滚。</p>
     *
     * @param request 已生成新版本的草稿聚合
     * @param expectedVersion 数据库应仍持有的旧版本
     * @return 是否成功更新恰好一条主记录
     */
    @Override
    public boolean updateDraftConditionally(ProcurementRequest request, long expectedVersion)
    {
        int updatedRequests = requestMapper.updateDraftConditionally(
                toRequestDO(request),
                request.creatorId().value(),
                expectedVersion);
        if (updatedRequests == 0)
        {
            return false;
        }
        if (updatedRequests != 1)
        {
            throw new IllegalStateException(
                    "Expected exactly one procurement request row to be updated");
        }

        itemMapper.delete(new QueryWrapper<ProcurementItemDO>()
                .eq(REQUEST_FOREIGN_KEY_COLUMN, request.id()));
        for (ProcurementItem item : request.items())
        {
            int insertedItems = itemMapper.insert(toItemDO(request, item));
            if (insertedItems != 1)
            {
                throw new IllegalStateException("Expected one procurement item row to be inserted");
            }
        }
        return true;
    }

    /**
     * 将持久化主记录和领域采购项恢复为聚合。
     *
     * @param request 申请持久化对象
     * @param items 已按行号排序的领域采购项
     * @return 采购申请聚合
     */
    private ProcurementRequest toDomain(
            ProcurementRequestDO request,
            List<ProcurementItem> items)
    {
        return ProcurementRequest.restore(
                request.id(),
                new BusinessNumber(request.businessNumber()),
                new UserId(request.creatorId()),
                request.title(),
                request.purpose(),
                request.department(),
                request.expectedDeliveryDate(),
                Currency.valueOf(request.currency()),
                ProcurementRequestStatus.valueOf(request.status()),
                request.version(),
                request.createdAt(),
                request.updatedAt(),
                items);
    }

    /**
     * 将采购项持久化对象恢复为领域实体。
     *
     * @param item 采购项持久化对象
     * @return 领域采购项
     */
    private ProcurementItem toDomainItem(ProcurementItemDO item)
    {
        return new ProcurementItem(
                item.id(),
                item.lineNo(),
                item.name(),
                CategoryCode.from(item.categoryCode()),
                item.specification(),
                item.quantity(),
                item.unit(),
                new Money(item.estimatedUnitPrice(), Currency.CNY));
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
