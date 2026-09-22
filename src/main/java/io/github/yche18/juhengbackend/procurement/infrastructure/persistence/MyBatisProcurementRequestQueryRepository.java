package io.github.yche18.juhengbackend.procurement.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.yche18.juhengbackend.common.application.PageResult;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import io.github.yche18.juhengbackend.procurement.application.ListOwnProcurementRequestsQuery;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestDetails;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestQueryRepository;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestSummary;
import io.github.yche18.juhengbackend.procurement.domain.CategoryCode;
import io.github.yche18.juhengbackend.procurement.domain.Currency;
import io.github.yche18.juhengbackend.procurement.domain.Money;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementItem;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 使用 MyBatis-Plus 执行带创建者范围的采购申请只读查询。
 *
 * <p>持久化对象使用不可变 Java record，其访问器不是 JavaBean 的 getXxx 形式，
 * 因此这里在基础设施边界显式使用受控数据库列名，而不使用 LambdaQueryWrapper。</p>
 */
@Repository
@Profile("!no-database")
public class MyBatisProcurementRequestQueryRepository implements ProcurementRequestQueryRepository
{

    private static final String REQUEST_ID_COLUMN = "id";
    private static final String CREATOR_ID_COLUMN = "creator_id";
    private static final String STATUS_COLUMN = "status";
    private static final String CREATED_AT_COLUMN = "created_at";
    private static final String REQUEST_FOREIGN_KEY_COLUMN = "procurement_request_id";
    private static final String LINE_NUMBER_COLUMN = "line_no";

    private final ProcurementRequestMapper requestMapper;
    private final ProcurementItemMapper itemMapper;

    /**
     * 创建采购申请查询适配器。
     *
     * @param requestMapper 申请主表 Mapper
     * @param itemMapper 采购项 Mapper
     */
    public MyBatisProcurementRequestQueryRepository(
            ProcurementRequestMapper requestMapper,
            ProcurementItemMapper itemMapper)
    {
        this.requestMapper = requestMapper;
        this.itemMapper = itemMapper;
    }

    /**
     * 在 SQL 条件中直接加入创建者和状态范围，并按创建时间、ID 稳定倒序分页。
     *
     * @param creatorId 可信创建者标识
     * @param query 分页和状态条件
     * @return 当前用户范围内的分页结果
     */
    @Override
    public PageResult<ProcurementRequestSummary> findOwnedPage(
            UserId creatorId,
            ListOwnProcurementRequestsQuery query)
    {
        QueryWrapper<ProcurementRequestDO> conditions = new QueryWrapper<ProcurementRequestDO>()
                .eq(CREATOR_ID_COLUMN, creatorId.value())
                .eq(query.status() != null, STATUS_COLUMN,
                        query.status() == null ? null : query.status().name())
                .orderByDesc(CREATED_AT_COLUMN, REQUEST_ID_COLUMN);

        Page<ProcurementRequestDO> persistencePage = new Page<>(query.page() + 1L, query.size());
        Page<ProcurementRequestDO> selectedPage = requestMapper.selectPage(persistencePage, conditions);
        List<ProcurementRequestSummary> content = selectedPage.getRecords().stream()
                .map(this::toSummary)
                .toList();
        return new PageResult<>(
                content,
                query.page(),
                query.size(),
                selectedPage.getTotal(),
                selectedPage.getPages());
    }

    /**
     * 使用 ID 与创建者联合查询主记录，命中后再用一次有序查询加载其采购项。
     *
     * @param requestId 申请标识
     * @param creatorId 可信创建者标识
     * @return 当前创建者范围内的申请详情
     */
    @Override
    public Optional<ProcurementRequestDetails> findOwnedById(UUID requestId, UserId creatorId)
    {
        ProcurementRequestDO request = requestMapper.selectOne(
                new QueryWrapper<ProcurementRequestDO>()
                        .eq(REQUEST_ID_COLUMN, requestId)
                        .eq(CREATOR_ID_COLUMN, creatorId.value()));
        if (request == null)
        {
            return Optional.empty();
        }

        List<ProcurementRequestDetails.ItemDetails> items = itemMapper.selectList(
                        new QueryWrapper<ProcurementItemDO>()
                                .eq(REQUEST_FOREIGN_KEY_COLUMN, request.id())
                                .orderByAsc(LINE_NUMBER_COLUMN))
                .stream()
                .map(this::toItemDetails)
                .toList();
        return Optional.of(toDetails(request, items));
    }

    /**
     * 将申请持久化对象映射为列表摘要。
     *
     * @param request 持久化申请
     * @return 只读列表摘要
     */
    private ProcurementRequestSummary toSummary(ProcurementRequestDO request)
    {
        return new ProcurementRequestSummary(
                request.id(),
                request.businessNumber(),
                request.title(),
                request.department(),
                request.expectedDeliveryDate(),
                request.currency(),
                request.estimatedTotal(),
                request.status(),
                request.version(),
                request.createdAt(),
                request.updatedAt());
    }

    /**
     * 将申请主记录和已排序采购项组合为详情读模型。
     *
     * @param request 持久化申请
     * @param items 采购项详情
     * @return 只读申请详情
     */
    private ProcurementRequestDetails toDetails(
            ProcurementRequestDO request,
            List<ProcurementRequestDetails.ItemDetails> items)
    {
        return new ProcurementRequestDetails(
                request.id(),
                request.businessNumber(),
                request.creatorId(),
                request.title(),
                request.purpose(),
                request.department(),
                request.expectedDeliveryDate(),
                request.currency(),
                request.estimatedTotal(),
                request.status(),
                request.version(),
                request.createdAt(),
                request.updatedAt(),
                items);
    }

    /**
     * 将采购项持久化对象转换为详情项，并复用领域金额规则计算行金额。
     *
     * @param item 持久化采购项
     * @return 只读采购项详情
     */
    private ProcurementRequestDetails.ItemDetails toItemDetails(ProcurementItemDO item)
    {
        ProcurementItem domainItem = new ProcurementItem(
                item.id(),
                item.lineNo(),
                item.name(),
                CategoryCode.from(item.categoryCode()),
                item.specification(),
                item.quantity(),
                item.unit(),
                new Money(item.estimatedUnitPrice(), Currency.CNY));
        return new ProcurementRequestDetails.ItemDetails(
                domainItem.id(),
                domainItem.lineNumber(),
                domainItem.name(),
                domainItem.categoryCode().name(),
                domainItem.specification(),
                domainItem.quantity(),
                domainItem.unit(),
                domainItem.estimatedUnitPrice().amount(),
                domainItem.estimatedLineTotal().amount());
    }
}
