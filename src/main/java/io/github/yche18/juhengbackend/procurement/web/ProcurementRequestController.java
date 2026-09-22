package io.github.yche18.juhengbackend.procurement.web;

import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.CurrentUserProvider;
import io.github.yche18.juhengbackend.procurement.application.CreateProcurementRequestResult;
import io.github.yche18.juhengbackend.procurement.application.CreateProcurementRequestService;
import io.github.yche18.juhengbackend.procurement.application.ListOwnProcurementRequestsQuery;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestDetails;
import io.github.yche18.juhengbackend.procurement.application.ProcurementRequestQueryService;
import io.github.yche18.juhengbackend.procurement.domain.ProcurementRequestStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 提供采购申请创建与本人范围查询的 REST 入口。
 */
@RestController
@RequestMapping("/api/procurement-requests")
@Profile("!no-database")
public class ProcurementRequestController
{

    private final CurrentUserProvider currentUserProvider;
    private final CreateProcurementRequestService createService;
    private final ProcurementRequestQueryService queryService;

    /**
     * 创建采购申请 Controller。
     *
     * @param currentUserProvider 可信当前用户提供端口
     * @param createService 创建草稿应用服务
     * @param queryService 查看本人申请的查询服务
     */
    public ProcurementRequestController(
            CurrentUserProvider currentUserProvider,
            CreateProcurementRequestService createService,
            ProcurementRequestQueryService queryService)
    {
        this.currentUserProvider = currentUserProvider;
        this.createService = createService;
        this.queryService = queryService;
    }

    /**
     * 使用认证上下文中的创建者建立采购申请草稿。
     *
     * @param request 客户端允许填写的申请字段
     * @return 服务端控制字段完整的创建结果
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateProcurementRequestResponse create(
            @Valid @RequestBody CreateProcurementRequestRequest request)
    {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        CreateProcurementRequestResult result = createService.create(request.toCommand(), currentUser);
        return CreateProcurementRequestResponse.from(result);
    }

    /**
     * 分页查看可信当前用户创建的采购申请，可按状态精确筛选。
     *
     * @param page 从零开始的页码
     * @param size 单页大小
     * @param status 可选 R1 申请状态
     * @return 当前用户申请的分页响应
     */
    @GetMapping
    public ProcurementRequestPageResponse listOwn(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(ListOwnProcurementRequestsQuery.MAX_PAGE_SIZE) int size,
            @RequestParam(required = false)
            @Pattern(regexp = ProcurementRequestStatus.SUPPORTED_PATTERN) String status)
    {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        ProcurementRequestStatus statusFilter = status == null
                ? null
                : ProcurementRequestStatus.valueOf(status);
        ListOwnProcurementRequestsQuery query = new ListOwnProcurementRequestsQuery(
                page,
                size,
                statusFilter);
        return ProcurementRequestPageResponse.from(queryService.listOwn(query, currentUser));
    }

    /**
     * 查看可信当前用户拥有的单个采购申请详情。
     *
     * @param requestId 申请标识
     * @return 申请详情
     */
    @GetMapping("/{requestId}")
    public ProcurementRequestDetailResponse getOwn(@PathVariable UUID requestId)
    {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        ProcurementRequestDetails details = queryService.getOwn(requestId, currentUser);
        return ProcurementRequestDetailResponse.from(details);
    }
}
