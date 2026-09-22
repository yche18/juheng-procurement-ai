package io.github.yche18.juhengbackend.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计事件表的 MyBatis-Plus Mapper。
 */
@Mapper
public interface AuditEventMapper extends BaseMapper<AuditEventDO>
{
}
