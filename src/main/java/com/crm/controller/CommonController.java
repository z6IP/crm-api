package com.crm.controller;

import com.crm.common.result.Result;
import com.crm.service.CommonService;
import com.crm.vo.FileUrlVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author zhz
 */
@Tag(name = "通用模块", description = "文件上传、下载等通用接口")
@RestController
@RequestMapping("common")
@AllArgsConstructor
public class CommonController {
    private final CommonService commonService;

    @PostMapping("upload/file")
    @Operation(summary = "文件上传至OSS", description = "支持图片、文档等常见文件格式")
    public Result<FileUrlVO> upload(
            @Parameter(description = "待上传的文件", required = true)
            @RequestBody MultipartFile file) {  // 关键修正：用@RequestParam接收文件
        return Result.ok(commonService.upload(file));
    }
}
