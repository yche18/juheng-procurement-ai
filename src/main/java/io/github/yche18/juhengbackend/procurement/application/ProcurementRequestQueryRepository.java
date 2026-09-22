package io.github.yche18.juhengbackend.procurement.application;

import io.github.yche18.juhengbackend.common.application.PageResult;
import io.github.yche18.juhengbackend.identity.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * 按可信创建者范围读取采购申请的查询端口。
 */
public interface ProcurementRequestQueryRepository
{

    /**
     * 在数据库查询中直接限定创建者、可选状态和分页范围。
     *
     * @param creatorId 可信创建者标识
     * @param query 分页和状态条件
     * @return 当前创建者范围内的稳定排序分页结果
     */
    PageResult<ProcurementRequestSummary> findOwnedPage(
            UserId creatorId,
            ListOwnProcurementRequestsQuery query);

    /**
     * 使用申请标识和可信创建者共同定位详情。
     *
     * @param requestId 申请标识
     * @param creatorId 可信创建者标识
     * @return 当前范围内存在时返回详情
     */
    Optional<ProcurementRequestDetails> findOwnedById(UUID requestId, UserId creatorId);
}
