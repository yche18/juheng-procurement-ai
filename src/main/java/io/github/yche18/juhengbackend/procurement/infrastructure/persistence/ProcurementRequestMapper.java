package io.github.yche18.juhengbackend.procurement.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购申请主表的 MyBatis-Plus Mapper。
 */
@Mapper
public interface ProcurementRequestMapper extends BaseMapper<ProcurementRequestDO>
{
}
