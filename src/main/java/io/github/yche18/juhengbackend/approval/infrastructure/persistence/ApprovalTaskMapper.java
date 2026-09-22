package io.github.yche18.juhengbackend.approval.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.UUID;

/**
 * 审批任务表的 MyBatis-Plus Mapper。
 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTaskDO>
{

    /**
     * 仅在任务仍由同一受理人持有、处于待处理状态且版本匹配时写入终态。
     *
     * @param taskId 任务标识
     * @param assigneeId 可信受理人标识
     * @param expectedVersion 客户端读取到的旧版本
     * @param status 新终态
     * @param nextVersion 新版本
     * @param updatedAt 服务端决定时间
     * @return 成功更新的行数
     */
    @Update("""
            UPDATE approval_task
               SET status = #{status},
                   version = #{nextVersion},
                   updated_at = #{updatedAt}
             WHERE id = #{taskId}
               AND assignee_id = #{assigneeId}
               AND status = 'PENDING'
               AND version = #{expectedVersion}
            """)
    int decideConditionally(
            @Param("taskId") UUID taskId,
            @Param("assigneeId") String assigneeId,
            @Param("expectedVersion") long expectedVersion,
            @Param("status") String status,
            @Param("nextVersion") long nextVersion,
            @Param("updatedAt") Instant updatedAt);
}
