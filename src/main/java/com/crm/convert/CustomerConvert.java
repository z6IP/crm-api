package com.crm.convert;

import com.crm.entity.Customer;
import com.crm.vo.CustomerVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * CustomerVO 与 Customer 实体的属性映射接口
 * 基于 MapStruct 自动生成实现类，避免手动编写 set/get 转换代码
 */
@Mapper
public interface CustomerConvert {

    // 单例实例：通过 MapStruct 工厂获取，全局复用
    CustomerConvert INSTANCE = Mappers.getMapper(CustomerConvert.class);

    /**
     * 将 CustomerVO 转换为 Customer 实体
     * @param customerVO 前端传入的客户视图对象
     * @return 转换后的客户数据库实体
     */
    Customer convert(CustomerVO customerVO);

}