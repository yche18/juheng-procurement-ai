package io.github.yche18.juhengbackend.approval.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * 审批决定表的 MyBatis-Plus Mapper。
 */
@Mapper
public interface ApprovalDecisionMapper extends BaseMapper<ApprovalDecisionDO>
{

    /**
     * 在数据库查询中同时限定申请标识和可信查看者范围，只返回已经保存的最终决定。
     *
     * @param requestId 采购申请标识
     * @param viewerId 可信当前用户标识
     * @return 当前查看者可见的最终决定；不满足任一条件时返回空
     */
    @Select("""
            SELECT decision.id,
                   decision.approval_task_id,
                   decision.decision,
                   decision.actor_id,
                   decision.decided_at,
                   decision.comment
            FROM procurement_request request
            JOIN approval_task task
              ON task.procurement_request_id = request.id
            JOIN approval_decision decision
              ON decision.approval_task_id = task.id
            WHERE request.id = #{requestId}
              AND (request.creator_id = #{viewerId}
                   OR task.assignee_id = #{viewerId})
            """)
    ApprovalDecisionDO selectVisibleByRequestId(
            @Param("requestId") UUID requestId,
            @Param("viewerId") String viewerId);
}
