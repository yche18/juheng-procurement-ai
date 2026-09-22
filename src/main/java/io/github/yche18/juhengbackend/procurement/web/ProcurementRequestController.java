package io.github.yche18.juhengbackend.procurement.web;

import io.github.yche18.juhengbackend.identity.application.CurrentUser;
import io.github.yche18.juhengbackend.identity.application.CurrentUserProvider;
import io.github.yche18.juhengbackend.procurement.application.CreateProcurementRequestResult;
import io.github.yche18.juhengbackend.procurement.application.CreateProcurementRequestService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供采购申请写入命令的 REST 入口。
 */
@RestController
@RequestMapping("/api/procurement-requests")
@Profile("!no-database")
public class ProcurementRequestController
{

    private final CurrentUserProvider currentUserProvider;
    private final CreateProcurementRequestService createService;

    /**
     * 创建采购申请 Controller。
     *
     * @param currentUserProvider 可信当前用户提供端口
     * @param createService 创建草稿应用服务
     */
    public ProcurementRequestController(
            CurrentUserProvider currentUserProvider,
            CreateProcurementRequestService createService)
    {
        this.currentUserProvider = currentUserProvider;
        this.createService = createService;
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
}
