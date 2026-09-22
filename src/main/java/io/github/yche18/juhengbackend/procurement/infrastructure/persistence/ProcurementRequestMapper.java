package io.github.yche18.juhengbackend.procurement.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 采购申请主表的 MyBatis-Plus Mapper。
 */
@Mapper
public interface ProcurementRequestMapper extends BaseMapper<ProcurementRequestDO>
{

    /**
     * 使用创建者、草稿状态和旧版本作为数据库最终并发保护条件。
     *
     * @param request 已生成新版本的申请持久化快照
     * @param expectedCreatorId 可信创建者标识
     * @param expectedVersion 客户端依据的旧版本
     * @return 更新行数；只有 {@code 1} 表示成功取得写入权
     */
    @Update("""
            UPDATE procurement_request
               SET title = #{request.title},
                   purpose = #{request.purpose},
                   department = #{request.department},
                   expected_delivery_date = #{request.expectedDeliveryDate},
                   estimated_total = #{request.estimatedTotal},
                   version = #{request.version},
                   updated_at = #{request.updatedAt}
             WHERE id = #{request.id}
               AND creator_id = #{expectedCreatorId}
               AND status = 'DRAFT'
               AND version = #{expectedVersion}
            """)
    int updateDraftConditionally(
            @Param("request") ProcurementRequestDO request,
            @Param("expectedCreatorId") String expectedCreatorId,
            @Param("expectedVersion") long expectedVersion);
}
