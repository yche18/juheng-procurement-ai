package io.github.yche18.juhengbackend.procurement.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 读取 PostgreSQL 业务编号序列的 Mapper。
 */
@Mapper
public interface BusinessNumberSequenceMapper
{

    /**
     * 消耗并返回下一个全局申请序列值。
     *
     * @return 正序列值
     */
    @Select("SELECT nextval('procurement_request_business_number_seq')")
    Long nextValue();
}
