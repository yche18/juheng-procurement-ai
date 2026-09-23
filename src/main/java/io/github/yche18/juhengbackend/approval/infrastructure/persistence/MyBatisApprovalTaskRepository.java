package io.github.yche18.juhengbackend.approval.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.yche18.juhengbackend.approval.application.ApprovalTaskRepository;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecision;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;
import io.github.yche18.juhengbackend.approval.domain.ApprovalTask;
import io.github.yche18.juhengbackend.approval.domain.ApprovalTaskStatus;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 使用 MyBatis-Plus 持久化审批任务聚合。
 */
@Repository
@Profile("!no-database")
public class MyBatisApprovalTaskRepository implements ApprovalTaskRepository
{

    private static final String TASK_ID_COLUMN = "id";
    private static final String ASSIGNEE_ID_COLUMN = "assignee_id";
    private static final String DECISION_TASK_ID_COLUMN = "approval_task_id";

    private final ApprovalTaskMapper taskMapper;
    private final ApprovalDecisionMapper decisionMapper;

    /**
     * 创建审批任务 Repository 适配器。
     *
     * @param taskMapper 审批任务 Mapper
     * @param decisionMapper 审批决定 Mapper
     */
    public MyBatisApprovalTaskRepository(
            ApprovalTaskMapper taskMapper,
            ApprovalDecisionMapper decisionMapper)
    {
        this.taskMapper = taskMapper;
        this.decisionMapper = decisionMapper;
    }

    /**
     * 插入唯一审批任务，并要求数据库恰好写入一行。
     *
     * @param task 待保存任务
     */
    @Override
    public void save(ApprovalTask task)
    {
        int insertedRows = taskMapper.insert(new ApprovalTaskDO(
                task.id(),
                task.procurementRequestId(),
                task.assigneeId().value(),
                task.status().name(),
                task.version(),
                task.createdAt(),
                task.updatedAt()));
        if (insertedRows != 1)
        {
            throw new IllegalStateException("Expected one approval task row to be inserted");
        }
    }

    /**
     * 使用任务标识和可信受理人共同加载任务，并为终态任务恢复唯一决定。
     *
     * @param taskId 任务标识
     * @param assigneeId 可信当前审批人
     * @return 当前审批人范围内的任务聚合
     */
    @Override
    public Optional<ApprovalTask> findAssignedById(UUID taskId, UserId assigneeId)
    {
        ApprovalTaskDO task = taskMapper.selectOne(new QueryWrapper<ApprovalTaskDO>()
                .eq(TASK_ID_COLUMN, taskId)
                .eq(ASSIGNEE_ID_COLUMN, assigneeId.value()));
        if (task == null)
        {
            return Optional.empty();
        }
        ApprovalDecision decision = findByTaskId(task.id()).orElse(null);
        return Optional.of(toDomain(task, decision));
    }

    /**
     * 先以受理人、待处理状态和旧版本竞争任务写入权，再保存唯一最终决定。
     *
     * @param task 已生成最终决定的新任务快照
     * @param expectedVersion 客户端读取到的旧版本
     * @return 条件更新恰好命中一行时为 {@code true}
     */
    @Override
    public boolean saveDecisionConditionally(ApprovalTask task, long expectedVersion)
    {
        ApprovalDecision decision = task.decision().orElseThrow(
                () -> new IllegalArgumentException("Decided approval task must contain a decision"));
        int updatedRows = taskMapper.decideConditionally(
                task.id(),
                task.assigneeId().value(),
                expectedVersion,
                task.status().name(),
                task.version(),
                task.updatedAt());
        if (updatedRows == 0)
        {
            return false;
        }
        if (updatedRows != 1)
        {
            throw new IllegalStateException("Expected exactly one approval task row to be decided");
        }
        int insertedRows = decisionMapper.insert(toDecisionDO(decision));
        if (insertedRows != 1)
        {
            throw new IllegalStateException("Expected one approval decision row to be inserted");
        }
        return true;
    }

    /**
     * 按决定标识加载幂等重放需要的不可变决定。
     *
     * @param decisionId 决定标识
     * @return 已持久化决定
     */
    @Override
    public Optional<ApprovalDecision> findDecisionById(UUID decisionId)
    {
        return Optional.ofNullable(decisionMapper.selectById(decisionId))
                .map(this::toDomainDecision);
    }

    /**
     * 按任务标识加载其至多一个最终决定。
     *
     * @param taskId 任务标识
     * @return 已持久化决定
     */
    private Optional<ApprovalDecision> findByTaskId(UUID taskId)
    {
        ApprovalDecisionDO decision = decisionMapper.selectOne(
                new QueryWrapper<ApprovalDecisionDO>()
                        .eq(DECISION_TASK_ID_COLUMN, taskId));
        return Optional.ofNullable(decision).map(this::toDomainDecision);
    }

    /**
     * 将任务持久化快照与可选决定恢复为聚合。
     *
     * @param task 任务持久化记录
     * @param decision 可选最终决定
     * @return 审批任务聚合
     */
    private ApprovalTask toDomain(ApprovalTaskDO task, ApprovalDecision decision)
    {
        return ApprovalTask.restore(
                task.id(),
                task.procurementRequestId(),
                new UserId(task.assigneeId()),
                ApprovalTaskStatus.valueOf(task.status()),
                task.version(),
                task.createdAt(),
                task.updatedAt(),
                decision);
    }

    /**
     * 将决定持久化记录恢复为不可变领域实体。
     *
     * @param decision 决定持久化记录
     * @return 审批决定实体
     */
    private ApprovalDecision toDomainDecision(ApprovalDecisionDO decision)
    {
        return new ApprovalDecision(
                decision.id(),
                decision.approvalTaskId(),
                ApprovalDecisionType.valueOf(decision.decision()),
                new UserId(decision.actorId()),
                decision.decidedAt(),
                decision.comment());
    }

    /**
     * 将不可变领域决定映射为数据库记录。
     *
     * @param decision 审批决定实体
     * @return 决定持久化对象
     */
    private ApprovalDecisionDO toDecisionDO(ApprovalDecision decision)
    {
        return new ApprovalDecisionDO(
                decision.id(),
                decision.approvalTaskId(),
                decision.decision().name(),
                decision.actorId().value(),
                decision.decidedAt(),
                decision.comment());
    }
}
