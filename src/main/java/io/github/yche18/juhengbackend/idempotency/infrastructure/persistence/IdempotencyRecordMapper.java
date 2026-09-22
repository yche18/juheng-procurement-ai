package io.github.yche18.juhengbackend.idempotency.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.UUID;

/**
 * 幂等记录表的 MyBatis-Plus Mapper。
 */
@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordDO>
{

    /**
     * 依靠组合唯一约束原子竞争幂等执行权。
     *
     * @param record 新的处理中记录
     * @return 插入成功为 1，作用域已存在为 0
     */
    @Insert("""
            INSERT INTO idempotency_record (
                id, caller_id, operation, target_type, target_id, idempotency_key,
                request_fingerprint, status, result_reference_type,
                result_reference_id, result_target_version, created_at, updated_at
            ) VALUES (
                #{record.id}, #{record.callerId}, #{record.operation},
                #{record.targetType}, #{record.targetId}, #{record.idempotencyKey},
                #{record.requestFingerprint}, #{record.status},
                #{record.resultReferenceType}, #{record.resultReferenceId},
                #{record.resultTargetVersion}, #{record.createdAt}, #{record.updatedAt}
            )
            ON CONFLICT (caller_id, operation, target_type, target_id, idempotency_key)
            DO NOTHING
            """)
    int insertIfAbsent(@Param("record") IdempotencyRecordDO record);

    /**
     * 仅允许拥有执行权的处理中记录完成一次。
     *
     * @param recordId 幂等记录标识
     * @param resultType 结果类型
     * @param resultId 结果标识
     * @param targetVersion 操作完成后的目标版本
     * @param completedAt 完成时间
     * @return 更新行数
     */
    @Update("""
            UPDATE idempotency_record
               SET status = 'COMPLETED',
                   result_reference_type = #{resultType},
                   result_reference_id = #{resultId},
                   result_target_version = #{targetVersion},
                   updated_at = #{completedAt}
             WHERE id = #{recordId}
               AND status = 'IN_PROGRESS'
            """)
    int complete(
            @Param("recordId") UUID recordId,
            @Param("resultType") String resultType,
            @Param("resultId") UUID resultId,
            @Param("targetVersion") long targetVersion,
            @Param("completedAt") Instant completedAt);
}
