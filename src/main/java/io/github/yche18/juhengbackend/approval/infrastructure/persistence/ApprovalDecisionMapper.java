package io.github.yche18.juhengbackend.approval.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批决定表的 MyBatis-Plus Mapper。
 */
@Mapper
public interface ApprovalDecisionMapper extends BaseMapper<ApprovalDecisionDO>
{
}
