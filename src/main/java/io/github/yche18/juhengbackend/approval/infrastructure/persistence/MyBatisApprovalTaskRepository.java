package io.github.yche18.juhengbackend.approval.infrastructure.persistence;

import io.github.yche18.juhengbackend.approval.application.ApprovalTaskRepository;
import io.github.yche18.juhengbackend.approval.domain.ApprovalTask;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 持久化审批任务聚合。
 */
@Repository
@Profile("!no-database")
public class MyBatisApprovalTaskRepository implements ApprovalTaskRepository
{

    private final ApprovalTaskMapper mapper;

    /**
     * 创建审批任务 Repository 适配器。
     *
     * @param mapper 审批任务 Mapper
     */
    public MyBatisApprovalTaskRepository(ApprovalTaskMapper mapper)
    {
        this.mapper = mapper;
    }

    /**
     * 插入唯一审批任务，并要求数据库恰好写入一行。
     *
     * @param task 待保存任务
     */
    @Override
    public void save(ApprovalTask task)
    {
        int insertedRows = mapper.insert(new ApprovalTaskDO(
                task.id(),
                task.procurementRequestId(),
                task.assigneeId().value(),
                task.status().name(),
                task.version(),
                task.createdAt(),
                task.updatedAt()));
        if (insertedRows != 1)
        {
            throw new IllegalStateException("Expected one approval task row to be inserted");
        }
    }
}
