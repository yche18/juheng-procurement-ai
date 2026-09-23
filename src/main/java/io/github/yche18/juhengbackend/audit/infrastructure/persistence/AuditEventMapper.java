package io.github.yche18.juhengbackend.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 审计事件表的 MyBatis-Plus Mapper。
 */
@Mapper
public interface AuditEventMapper extends BaseMapper<AuditEventDO>
{

    /**
     * 判断申请是否位于可信查看者的创建者或任务受理人范围。
     *
     * @param requestId 采购申请标识
     * @param viewerId 可信查看者标识
     * @return 位于授权范围时返回 true
     */
    @Select("""
            SELECT EXISTS (
                SELECT 1
                  FROM procurement_request request
             LEFT JOIN approval_task task
                    ON task.procurement_request_id = request.id
                 WHERE request.id = #{requestId}
                   AND (request.creator_id = #{viewerId} OR task.assignee_id = #{viewerId})
            )
            """)
    boolean existsVisibleRequest(
            @Param("requestId") UUID requestId,
            @Param("viewerId") String viewerId);

    /**
     * 在 SQL 中限定查看者范围并按发生时间、事件标识稳定升序读取轨迹。
     *
     * @param requestId 采购申请标识
     * @param viewerId 可信查看者标识
     * @return 当前查看者范围内的有序审计事件
     */
    @Select("""
            SELECT event.id,
                   event.procurement_request_id,
                   event.actor_id,
                   event.action,
                   event.target_type,
                   event.target_id,
                   event.occurred_at,
                   event.result,
                   event.request_identifier
              FROM audit_event event
             WHERE event.procurement_request_id = #{requestId}
               AND EXISTS (
                   SELECT 1
                     FROM procurement_request request
                LEFT JOIN approval_task task
                       ON task.procurement_request_id = request.id
                    WHERE request.id = event.procurement_request_id
                      AND (request.creator_id = #{viewerId} OR task.assignee_id = #{viewerId})
               )
          ORDER BY event.occurred_at ASC, event.id ASC
            """)
    List<AuditEventDO> selectVisibleTrail(
            @Param("requestId") UUID requestId,
            @Param("viewerId") String viewerId);
}
