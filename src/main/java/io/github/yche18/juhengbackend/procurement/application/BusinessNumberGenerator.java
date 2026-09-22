package io.github.yche18.juhengbackend.procurement.application;

import io.github.yche18.juhengbackend.procurement.domain.BusinessNumber;

/**
 * 生成稳定且唯一采购申请业务编号的端口。
 */
public interface BusinessNumberGenerator
{

    /**
     * 取得下一个唯一业务编号。
     *
     * @return 新业务编号
     */
    BusinessNumber nextBusinessNumber();
}
