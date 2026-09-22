package io.github.yche18.juhengbackend.procurement.application;

import io.github.yche18.juhengbackend.audit.application.AuditEventStore;
import io.github.yche18.juhengbackend.audit.domain.AuditEvent;
import io.github.yche18.juhengbackend.common.error.ConcurrentUpdateException;
import io.github.yche18.juhengbackend.common.error.ResourceNotFoundException;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.RoleAuthorizer;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.procurement.domain.CategoryCode;
import io.github.yche18.juhengbackend.procurement.domain.Currency;
import io.github.yche18.juhengbackend.procurement.domain.Money;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementItem;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 编排修改本人采购草稿、条件持久化和成功审计的应用用例。
 */
@Service
@Profile("!no-database")
public class UpdateProcurementRequestService
{

    private final RoleAuthorizer roleAuthorizer;
    private final ProcurementRequestRepository requestRepository;
    private final AuditEventStore auditEventStore;
    private final Clock clock;

    /**
     * 创建采购草稿修改服务。
     *
     * @param roleAuthorizer 角色授权器
     * @param requestRepository 申请聚合持久化端口
     * @param auditEventStore 审计追加端口
     * @param clock 服务端时钟
     */
    public UpdateProcurementRequestService(
            RoleAuthorizer roleAuthorizer,
            ProcurementRequestRepository requestRepository,
            AuditEventStore auditEventStore,
            Clock clock)
    {
        this.roleAuthorizer = roleAuthorizer;
        this.requestRepository = requestRepository;
        this.auditEventStore = auditEventStore;
        this.clock = clock;
    }

    /**
     * 在同一数据库事务中保存新草稿快照、采购项、版本和修改审计。
     *
     * @param requestId 待修改申请标识
     * @param command 客户端提交的版本与完整可编辑快照
     * @param currentUser 可信当前用户
     * @return 已保存的新版本详情
     */
    @Transactional
    public ProcurementRequestDetails update(
            UUID requestId,
            UpdateProcurementRequestCommand command,
            CurrentUser currentUser)
    {
        Objects.requireNonNull(requestId, "Procurement request ID must not be null");
        Objects.requireNonNull(command, "Update command must not be null");
        Objects.requireNonNull(currentUser, "Current user must not be null");
        roleAuthorizer.requireRole(currentUser, Role.REQUESTER);

        ProcurementRequest current = requestRepository
                .findOwnedById(requestId, currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procurement request is not visible in the current user scope"));
        Instant now = clock.instant();
        ProcurementRequest updated = current.updateDraft(
                command.version(),
                command.title(),
                command.purpose(),
                command.department(),
                command.expectedDeliveryDate(),
                createItems(command.items()),
                now);

        if (!requestRepository.updateDraftConditionally(updated, command.version()))
        {
            throw new ConcurrentUpdateException(
                    "Procurement request changed after it was loaded for update");
        }
        auditEventStore.append(AuditEvent.procurementRequestUpdated(
                requestId,
                currentUser.userId(),
                now,
                newRequestIdentifier()));
        return ProcurementRequestDetails.from(updated);
    }

    /**
     * 将完整采购项命令转换为带服务端 ID 和连续行号的新领域快照。
     *
     * @param itemCommands 客户端提供的采购项命令
     * @return 领域采购项集合
     */
    private List<ProcurementItem> createItems(
            List<UpdateProcurementRequestCommand.ItemCommand> itemCommands)
    {
        if (itemCommands == null || itemCommands.isEmpty())
        {
            throw new IllegalArgumentException("Procurement request must contain at least one item");
        }
        List<ProcurementItem> items = new ArrayList<>(itemCommands.size());
        for (int index = 0; index < itemCommands.size(); index++)
        {
            UpdateProcurementRequestCommand.ItemCommand item = itemCommands.get(index);
            items.add(new ProcurementItem(
                    UUID.randomUUID(),
                    index + 1,
                    item.name(),
                    CategoryCode.from(item.categoryCode()),
                    item.specification(),
                    item.quantity(),
                    item.unit(),
                    new Money(item.estimatedUnitPrice(), Currency.CNY)));
        }
        return List.copyOf(items);
    }

    /**
     * 生成只由服务端控制的修改审计请求标识。
     *
     * @return UUID 文本请求标识
     */
    private String newRequestIdentifier()
    {
        return UUID.randomUUID().toString();
    }
}
