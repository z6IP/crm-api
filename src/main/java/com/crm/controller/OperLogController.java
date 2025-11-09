package com.crm.controller;

import com.crm.common.result.PageResult;
import com.crm.common.result.Result;
import com.crm.entity.OperLog;
import com.crm.query.OperLogQuery;
import com.crm.service.OperLogService;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "操作日志")
@RestController
@AllArgsConstructor
@RequestMapping("/operLog")
public class OperLogController {

    // 注入操作日志服务层（final 修饰，通过全参构造器注入，无需 @Autowired）
    private final OperLogService operLogService;

    @PostMapping("page")
    @Operation(summary = "分页查询")
    public Result<PageResult<OperLog>> page(@RequestBody OperLogQuery query) {
        return Result.ok(operLogService.page(query));
    }
}