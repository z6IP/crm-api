package com.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.common.result.PageResult;
import com.crm.entity.OperLog;
import com.crm.mapper.OperLogMapper;
import com.crm.query.OperLogQuery;
import com.crm.service.OperLogService;
import com.crm.utils.AddressUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 * 操作日志记录 服务实现类
 * </p>
 *
 * @author vact
 * @since 2025-10-12
 */
@Service
public class OperLogServiceImpl extends ServiceImpl<OperLogMapper, OperLog> implements OperLogService {

    @Override
    public void recordOperLog(OperLog operLog) {
        operLog.setOperLocation(AddressUtils.getRealAddressByIP(operLog.getOperIp()));
        operLog.setOperTime(LocalDateTime.now());
        baseMapper.insert(operLog);
    }
    @Override
    public PageResult<OperLog> page(OperLogQuery query) {
        // 1. 构建分页对象（从查询参数中获取页码和每页条数）
        Page<OperLog> page = new Page<>(query.getPage(), query.getLimit());

        // 2. 构建 Lambda 条件查询构造器（避免硬编码字段名，更安全）
        LambdaQueryWrapper<OperLog> wrapper = new LambdaQueryWrapper<>();

        // 3. 处理查询条件：动态拼接 where 子句（非空才添加条件）
        // 操作人账号：非空则精确匹配
        wrapper.eq(StringUtils.isNotBlank(query.getOperName()), OperLog::getOperName, query.getOperName());
        // 接口URL：非空则精确匹配
        wrapper.eq(StringUtils.isNotBlank(query.getOperUrl()), OperLog::getOperUrl, query.getOperUrl());
        // 操作时间段：非空且有2个元素（开始时间、结束时间），则范围查询（between）
        if (query.getOperTime() != null && !query.getOperTime().isEmpty()) {
            wrapper.between(OperLog::getOperTime, query.getOperTime().get(0), query.getOperTime().get(1));
        }

        // 4. 排序：按操作时间降序（最新的日志排在前面）
        wrapper.orderByDesc(OperLog::getOperTime);

        // 5. 执行分页查询（调用 MyBatis-Plus 的 baseMapper 实现分页查询）
        Page<OperLog> result = baseMapper.selectPage(page, wrapper);

        // 6. 封装分页结果（适配自定义的 PageResult 响应格式）
        return new PageResult<>(result.getRecords(), result.getTotal());
    }
}
