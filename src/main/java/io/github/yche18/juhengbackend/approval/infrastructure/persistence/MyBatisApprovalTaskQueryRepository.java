package io.github.yche18.juhengbackend.approval.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.yche18.juhengbackend.approval.application.ApprovalTaskDetails;
import io.github.yche18.juhengbackend.approval.application.ApprovalTaskQueryRepository;
import io.github.yche18.juhengbackend.approval.application.ApprovalTaskSummary;
import io.github.yche18.juhengbackend.approval.application.ListAssignedApprovalTasksQuery;
import io.github.yche18.juhengbackend.common.application.PageResult;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestDetails;
import io.github.yche18.juhengbackend.procurement.domain.CategoryCode;
import io.github.yche18.juhengbackend.procurement.domain.Currency;
import io.github.yche18.juhengbackend.procurement.domain.Money;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementItem;
import io.github.yche18.juhengbackend.procurement.infrastructure.persistence.ProcurementItemDO;
import io.github.yche18.juhengbackend.procurement.infrastructure.persistence.ProcurementItemMapper;
import io.github.yche18.juhengbackend.procurement.infrastructure.persistence.ProcurementRequestDO;
import io.github.yche18.juhengbackend.procurement.infrastructure.persistence.ProcurementRequestMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 使用 MyBatis-Plus 执行带审批人范围的审批任务只读查询。
 *
 * <p>列表先分页查询任务，再用一次批量查询加载本页申请摘要，查询次数不随任务数量增长。
 * 详情先用任务标识和审批人完成授权，再使用任务中的申请标识加载申请与采购项。</p>
 */
@Repository
@Profile("!no-database")
public class MyBatisApprovalTaskQueryRepository implements ApprovalTaskQueryRepository
{

    private static final String TASK_ID_COLUMN = "id";
    private static final String ASSIGNEE_ID_COLUMN = "assignee_id";
    private static final String STATUS_COLUMN = "status";
    private static final String CREATED_AT_COLUMN = "created_at";
    private static final String REQUEST_ID_COLUMN = "id";
    private static final String REQUEST_FOREIGN_KEY_COLUMN = "procurement_request_id";
    private static final String LINE_NUMBER_COLUMN = "line_no";

    private final ApprovalTaskMapper taskMapper;
    private final ProcurementRequestMapper requestMapper;
    private final ProcurementItemMapper itemMapper;

    /**
     * 创建审批任务查询适配器。
     *
     * @param taskMapper 审批任务 Mapper
     * @param requestMapper 采购申请 Mapper
     * @param itemMapper 采购项 Mapper
     */
    public MyBatisApprovalTaskQueryRepository(
            ApprovalTaskMapper taskMapper,
            ProcurementRequestMapper requestMapper,
            ProcurementItemMapper itemMapper)
    {
        this.taskMapper = taskMapper;
        this.requestMapper = requestMapper;
        this.itemMapper = itemMapper;
    }

    /**
     * 在审批任务 SQL 中直接限定受理人和可选状态，并按创建时间、ID 稳定倒序分页。
     *
     * @param assigneeId 可信审批人标识
     * @param query 分页和状态条件
     * @return 当前审批人范围内的任务分页结果
     */
    @Override
    public PageResult<ApprovalTaskSummary> findAssignedPage(
            UserId assigneeId,
            ListAssignedApprovalTasksQuery query)
    {
        QueryWrapper<ApprovalTaskDO> conditions = new QueryWrapper<ApprovalTaskDO>()
                .eq(ASSIGNEE_ID_COLUMN, assigneeId.value())
                .eq(query.status() != null, STATUS_COLUMN,
                        query.status() == null ? null : query.status().name())
                .orderByDesc(CREATED_AT_COLUMN, TASK_ID_COLUMN);

        Page<ApprovalTaskDO> persistencePage = new Page<>(query.page() + 1L, query.size());
        Page<ApprovalTaskDO> selectedPage = taskMapper.selectPage(persistencePage, conditions);
        Map<UUID, ProcurementRequestDO> requestsById = findRequestsById(selectedPage.getRecords());
        List<ApprovalTaskSummary> content = selectedPage.getRecords().stream()
                .map(task -> toSummary(task, requireRequest(requestsById, task.procurementRequestId())))
                .toList();
        return new PageResult<>(
                content,
                query.page(),
                query.size(),
                selectedPage.getTotal(),
                selectedPage.getPages());
    }

    /**
     * 先按任务 ID 与受理人联合查询，只有授权命中后才加载关联申请和采购项。
     *
     * @param taskId 审批任务标识
     * @param assigneeId 可信审批人标识
     * @return 当前审批人范围内存在时返回任务详情
     */
    @Override
    public Optional<ApprovalTaskDetails> findAssignedById(UUID taskId, UserId assigneeId)
    {
        ApprovalTaskDO task = taskMapper.selectOne(new QueryWrapper<ApprovalTaskDO>()
                .eq(TASK_ID_COLUMN, taskId)
                .eq(ASSIGNEE_ID_COLUMN, assigneeId.value()));
        if (task == null)
        {
            return Optional.empty();
        }

        ProcurementRequestDO request = requestMapper.selectById(task.procurementRequestId());
        if (request == null)
        {
            throw new IllegalStateException("Approval task references a missing procurement request");
        }
        List<ProcurementRequestDetails.ItemDetails> items = itemMapper.selectList(
                        new QueryWrapper<ProcurementItemDO>()
                                .eq(REQUEST_FOREIGN_KEY_COLUMN, request.id())
                                .orderByAsc(LINE_NUMBER_COLUMN))
                .stream()
                .map(this::toItemDetails)
                .toList();
        return Optional.of(new ApprovalTaskDetails(
                task.id(),
                task.assigneeId(),
                task.status(),
                task.version(),
                task.createdAt(),
                task.updatedAt(),
                toRequestDetails(request, items)));
    }

    /**
     * 使用本页任务派生出的申请标识进行一次批量查询，避免逐条加载申请。
     *
     * @param tasks 已按审批人授权并完成分页的任务
     * @return 以申请标识索引的持久化申请
     */
    private Map<UUID, ProcurementRequestDO> findRequestsById(List<ApprovalTaskDO> tasks)
    {
        if (tasks.isEmpty())
        {
            return Map.of();
        }
        LinkedHashSet<UUID> requestIds = tasks.stream()
                .map(ApprovalTaskDO::procurementRequestId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return requestMapper.selectList(
                        new QueryWrapper<ProcurementRequestDO>().in(REQUEST_ID_COLUMN, requestIds))
                .stream()
                .collect(Collectors.toUnmodifiableMap(ProcurementRequestDO::id, Function.identity()));
    }

    /**
     * 取得任务对应申请；外键已经保证存在，缺失表示数据库事实损坏。
     *
     * @param requestsById 本页申请索引
     * @param requestId 任务关联申请标识
     * @return 对应申请
     */
    private ProcurementRequestDO requireRequest(
            Map<UUID, ProcurementRequestDO> requestsById,
            UUID requestId)
    {
        ProcurementRequestDO request = requestsById.get(requestId);
        if (request == null)
        {
            throw new IllegalStateException("Approval task references a missing procurement request");
        }
        return request;
    }

    /**
     * 将审批任务与申请持久化对象映射为列表摘要。
     *
     * @param task 审批任务持久化对象
     * @param request 关联申请持久化对象
     * @return 任务列表摘要
     */
    private ApprovalTaskSummary toSummary(ApprovalTaskDO task, ProcurementRequestDO request)
    {
        return new ApprovalTaskSummary(
                task.id(),
                task.status(),
                task.version(),
                task.createdAt(),
                task.updatedAt(),
                new ApprovalTaskSummary.ProcurementRequestSummary(
                        request.id(),
                        request.businessNumber(),
                        request.creatorId(),
                        request.title(),
                        request.department(),
                        request.expectedDeliveryDate(),
                        request.currency(),
                        request.estimatedTotal(),
                        request.status(),
                        request.version()));
    }

    /**
     * 将申请主记录和已排序采购项组合为审批任务使用的完整申请详情。
     *
     * @param request 申请持久化对象
     * @param items 采购项详情
     * @return 只读申请详情
     */
    private ProcurementRequestDetails toRequestDetails(
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
     * @param item 采购项持久化对象
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
