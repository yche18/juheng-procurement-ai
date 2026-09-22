package io.github.yche18.juhengbackend.procurement.application;

import io.github.yche18.juhengbackend.audit.application.AuditEventStore;
import io.github.yche18.juhengbackend.audit.domain.AuditEvent;
import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.RoleAuthorizer;
import io.github.yche18.juhengbackend.identity.domain.Role;
import io.github.yche18.juhengbackend.procurement.domain.BusinessNumber;
import io.github.yche18.juhengbackend.procurement.domain.CategoryCode;
import io.github.yche18.juhengbackend.procurement.domain.Currency;
import io.github.yche18.juhengbackend.procurement.domain.Money;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementItem;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequest;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 编排创建采购草稿、采购项和创建审计的应用用例。
 */
@Service
@Profile("!no-database")
public class CreateProcurementRequestService
{

    private final RoleAuthorizer roleAuthorizer;
    private final BusinessNumberGenerator businessNumberGenerator;
    private final ProcurementRequestRepository requestRepository;
    private final AuditEventStore auditEventStore;
    private final Clock clock;

    /**
     * 创建草稿应用服务。
     *
     * @param roleAuthorizer 角色授权器
     * @param businessNumberGenerator 业务编号生成器
     * @param requestRepository 申请持久化端口
     * @param auditEventStore 审计追加端口
     * @param clock 服务端时钟
     */
    public CreateProcurementRequestService(
            RoleAuthorizer roleAuthorizer,
            BusinessNumberGenerator businessNumberGenerator,
            ProcurementRequestRepository requestRepository,
            AuditEventStore auditEventStore,
            Clock clock)
    {
        this.roleAuthorizer = roleAuthorizer;
        this.businessNumberGenerator = businessNumberGenerator;
        this.requestRepository = requestRepository;
        this.auditEventStore = auditEventStore;
        this.clock = clock;
    }

    /**
     * 在同一个数据库事务中创建申请聚合并追加成功审计。
     *
     * @param command 客户端可控业务字段
     * @param currentUser 可信当前用户
     * @return 已创建草稿
     */
    @Transactional
    public CreateProcurementRequestResult create(
            CreateProcurementRequestCommand command,
            CurrentUser currentUser)
    {
        Objects.requireNonNull(command, "Create command must not be null");
        Objects.requireNonNull(currentUser, "Current user must not be null");
        roleAuthorizer.requireRole(currentUser, Role.REQUESTER);

        UUID requestId = UUID.randomUUID();
        Instant now = clock.instant();
        BusinessNumber businessNumber = businessNumberGenerator.nextBusinessNumber();
        List<ProcurementItem> items = createItems(command.items());
        ProcurementRequest request = ProcurementRequest.createDraft(
                requestId,
                businessNumber,
                currentUser.userId(),
                command.title(),
                command.purpose(),
                command.department(),
                command.expectedDeliveryDate(),
                items,
                now);

        requestRepository.save(request);
        auditEventStore.append(AuditEvent.procurementRequestCreated(
                requestId,
                currentUser.userId(),
                now,
                newRequestIdentifier()));
        return CreateProcurementRequestResult.from(request);
    }

    /**
     * 将应用命令转换为具有服务端 ID 和行号的领域采购项。
     *
     * @param itemCommands 采购项命令
     * @return 领域采购项集合
     */
    private List<ProcurementItem> createItems(List<CreateProcurementRequestCommand.ItemCommand> itemCommands)
    {
        if (itemCommands == null || itemCommands.isEmpty())
        {
            throw new IllegalArgumentException("Procurement request must contain at least one item");
        }
        List<ProcurementItem> items = new ArrayList<>(itemCommands.size());
        for (int index = 0; index < itemCommands.size(); index++)
        {
            CreateProcurementRequestCommand.ItemCommand item = itemCommands.get(index);
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
     * 生成只由服务端控制的请求审计标识。
     *
     * @return UUID 文本请求标识
     */
    private String newRequestIdentifier()
    {
        return UUID.randomUUID().toString();
    }
}
