package io.github.yche18.juhengbackend.approval.infrastructure.persistence;

import io.github.yche18.juhengbackend.approval.application.FinalApprovalDecisionDetails;
import io.github.yche18.juhengbackend.approval.application.FinalApprovalDecisionQueryRepository;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecision;
import io.github.yche18.juhengbackend.approval.domain.ApprovalDecisionType;
import io.github.yche18.juhengbackend.identity.domain.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 使用 MyBatis 执行带申请创建者或任务受理人范围的最终审批决定查询。
 */
@Repository
@Profile("!no-database")
public class MyBatisFinalApprovalDecisionQueryRepository implements FinalApprovalDecisionQueryRepository
{

    private final ApprovalDecisionMapper approvalDecisionMapper;

    /**
     * 创建最终审批决定查询适配器。
     *
     * @param approvalDecisionMapper 审批决定 Mapper
     */
    public MyBatisFinalApprovalDecisionQueryRepository(ApprovalDecisionMapper approvalDecisionMapper)
    {
        this.approvalDecisionMapper = approvalDecisionMapper;
    }

    /**
     * 通过带查看者范围的单条 SQL 查找最终审批决定。
     *
     * @param requestId 采购申请标识
     * @param viewerId 可信当前用户标识
     * @return 当前用户可见且已经保存的最终决定
     */
    @Override
    public Optional<FinalApprovalDecisionDetails> findVisibleByRequestId(
            UUID requestId,
            UserId viewerId)
    {
        return Optional.ofNullable(
                        approvalDecisionMapper.selectVisibleByRequestId(requestId, viewerId.value()))
                .map(decision -> toDetails(requestId, decision));
    }

    /**
     * 先通过领域对象复用最终决定不变量校验，再生成只读查询投影。
     *
     * @param requestId 采购申请标识
     * @param storedDecision 持久化审批决定
     * @return 最终审批决定只读投影
     */
    private FinalApprovalDecisionDetails toDetails(
            UUID requestId,
            ApprovalDecisionDO storedDecision)
    {
        ApprovalDecision decision = new ApprovalDecision(
                storedDecision.id(),
                storedDecision.approvalTaskId(),
                ApprovalDecisionType.valueOf(storedDecision.decision()),
                new UserId(storedDecision.actorId()),
                storedDecision.decidedAt(),
                storedDecision.comment());
        return new FinalApprovalDecisionDetails(
                requestId,
                decision.approvalTaskId(),
                decision.id(),
                decision.decision(),
                decision.actorId().value(),
                decision.decidedAt(),
                decision.comment());
    }
}
