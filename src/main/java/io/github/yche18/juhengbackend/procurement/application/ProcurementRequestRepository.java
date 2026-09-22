package io.github.yche18.juhengbackend.procurement.application;

import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequest;
import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.util.Optional;
import java.util.UUID;

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

    /**
     * 使用申请标识和可信创建者共同加载可修改聚合，避免先读取他人数据再授权。
     *
     * @param requestId 申请标识
     * @param creatorId 可信创建者标识
     * @return 当前创建者范围内的聚合；不可见时为空
     */
    Optional<ProcurementRequest> findOwnedById(UUID requestId, UserId creatorId);

    /**
     * 仅当数据库中的创建者、状态和版本仍符合预期时保存新草稿快照。
     *
     * @param request 已生成新版本的草稿聚合
     * @param expectedVersion 条件更新使用的旧版本
     * @return 主记录恰好更新一行时为 {@code true}，否则表示并发冲突
     */
    boolean updateDraftConditionally(ProcurementRequest request, long expectedVersion);

    /**
     * 仅当数据库中的创建者、状态和版本仍符合预期时保存提交状态。
     *
     * @param request 已转换为 SUBMITTED 的新版本聚合
     * @param expectedVersion 条件更新使用的草稿版本
     * @return 主记录恰好更新一行时为 {@code true}
     */
    boolean submitConditionally(ProcurementRequest request, long expectedVersion);
}
