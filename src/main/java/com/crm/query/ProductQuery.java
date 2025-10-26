package com.crm.query;

import com.crm.common.model.Query;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhz
 */
@Data
public class ProductQuery extends Query {
    @Schema(description = "商品名称")  // 单行写法（简洁）
    private String name;

    @Schema(description = "商品状态")  // 单行更规范，避免换行问题
    private Integer status;
}