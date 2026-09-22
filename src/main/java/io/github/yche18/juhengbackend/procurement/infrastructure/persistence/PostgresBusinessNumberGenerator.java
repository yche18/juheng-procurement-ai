package io.github.yche18.juhengbackend.procurement.infrastructure.persistence;

import io.github.yche18.juhengbackend.procurement.application.BusinessNumberGenerator;
import io.github.yche18.juhengbackend.procurement.domain.BusinessNumber;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 使用 PostgreSQL 序列生成并发安全的采购申请业务编号。
 */
@Component
@Profile("!no-database")
public class PostgresBusinessNumberGenerator implements BusinessNumberGenerator
{

    private final BusinessNumberSequenceMapper sequenceMapper;
    private final Clock clock;

    /**
     * 创建 PostgreSQL 业务编号生成器。
     *
     * @param sequenceMapper 序列 Mapper
     * @param clock 服务端时钟
     */
    public PostgresBusinessNumberGenerator(BusinessNumberSequenceMapper sequenceMapper, Clock clock)
    {
        this.sequenceMapper = sequenceMapper;
        this.clock = clock;
    }

    /**
     * 组合 UTC 日期和数据库序列形成稳定唯一编号。
     *
     * @return 新业务编号
     */
    @Override
    public BusinessNumber nextBusinessNumber()
    {
        Long sequence = sequenceMapper.nextValue();
        if (sequence == null || sequence <= 0)
        {
            throw new IllegalStateException("PostgreSQL returned an invalid business number sequence");
        }
        String date = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
        return new BusinessNumber(String.format(Locale.ROOT, "PR-%s-%06d", date, sequence));
    }
}
