package com.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.common.exception.ServerException;
import com.crm.common.result.PageResult;
import com.crm.convert.CustomerConvert;
import com.crm.entity.Customer;
import com.crm.entity.SysManager;
import com.crm.mapper.CustomerMapper;
import com.crm.query.CustomerQuery;
import com.crm.query.CustomerTrendQuery;
import com.crm.query.IdQuery;
import com.crm.security.user.SecurityUser;
import com.crm.service.CustomerService;
import com.crm.utils.DateUtils;
import com.crm.utils.ExcelUtils;
import com.crm.vo.CustomerTrendVO;
import com.crm.vo.CustomerVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.crm.utils.DateUtils.*;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author crm
 * @since 2025-10-12
 */
@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    @Override
    public PageResult<CustomerVO> getPage(CustomerQuery query) {
        Page<CustomerVO> page = new Page<>(query.getPage(), query.getLimit());
        MPJLambdaWrapper<Customer> wrapper = selection(query);
        Page<CustomerVO> result = baseMapper.selectJoinPage(page, CustomerVO.class, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void exportCustomer(CustomerQuery query, HttpServletResponse httpResponse) {
        MPJLambdaWrapper<Customer> wrapper = selection(query);
        List<Customer> customerList = baseMapper.selectJoinList(wrapper);
        ExcelUtils.writeExcel(httpResponse, customerList, "客户信息", "客户信息", CustomerVO.class);
    }

    private MPJLambdaWrapper<Customer> selection(CustomerQuery query) {
        MPJLambdaWrapper<Customer> wrapper = new MPJLambdaWrapper<>();
        wrapper.selectAll(Customer.class)
                .selectAs("o", SysManager::getAccount, CustomerVO::getOwnerName)
                .selectAs("c", SysManager::getAccount, CustomerVO::getCreaterName)
                .leftJoin(SysManager.class, "o", SysManager::getId, Customer::getOwnerId)
                .leftJoin(SysManager.class, "c", SysManager::getId, Customer::getCreaterId);
        if (StringUtils.isNotBlank(query.getName())) {
            wrapper.like(Customer::getName, query.getName());
        }
        if (StringUtils.isNotBlank(query.getPhone())) {
            wrapper.like(Customer::getPhone, query.getPhone());
        }
        if (query.getLevel() != null) {
            wrapper.eq(Customer::getLevel, query.getLevel());
        }
        if (query.getSource() != null) {
            wrapper.eq(Customer::getSource, query.getSource());
        }
        if (query.getFollowStatus() != null) {
            wrapper.eq(Customer::getFollowStatus, query.getFollowStatus());
        }
        if (query.getIsPublic() != null) {
            wrapper.eq(Customer::getIsPublic, query.getIsPublic());
        }
        wrapper.orderByDesc(Customer::getCreateTime);
        return wrapper;
    }

    @Override
    public void saveOrUpdate(CustomerVO customerVO) {
        // 1. 构建手机号查询条件（校验手机号唯一性）
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getPhone, customerVO.getPhone());

        // 2. 区分「新增客户」和「修改客户」场景
        if (customerVO.getId() == null) {
            // 场景1：新增客户 - 校验手机号是否已存在
            Customer existingCustomer = baseMapper.selectOne(wrapper);
            if (existingCustomer != null) {
                throw new ServerException("该手机号客户已经存在，请勿重复添加客户信息");
            }

            // 3. VO 转实体 + 填充新增必要字段（创建人、负责人ID）
            Customer customerToSave = CustomerConvert.INSTANCE.convert(customerVO);
            Integer currentManagerId = SecurityUser.getManagerId();
            customerToSave.setCreaterId(currentManagerId);
            customerToSave.setOwnerId(currentManagerId);

            // 4. 插入数据库
            baseMapper.insert(customerToSave);
        } else {
            // 场景2：修改客户 - 校验手机号是否被其他客户占用（排除自身）
            wrapper.ne(Customer::getId, customerVO.getId());
            Customer existingCustomer = baseMapper.selectOne(wrapper);
            if (existingCustomer != null) {
                throw new ServerException("该手机号客户已经存在，请勿重复添加客户信息");
            }

            // 5. VO 转实体 + 执行更新
            Customer customerToUpdate = CustomerConvert.INSTANCE.convert(customerVO);
            baseMapper.updateById(customerToUpdate);
        }
    }

    @Override
    public void removeCustomer(List<Integer> ids) {
        removeByIds(ids);
    }

    @Override
    public void customerToPublicPool(IdQuery idQuery) {
        // 根据ID查询客户信息
        Customer customer = baseMapper.selectById(idQuery.getId());

        // 校验客户是否存在
        if (customer == null) {
            throw new ServerException("客户不存在,无法转入公海");
        }

        // 设置客户为"公海客户"（1表示公海），清空负责人ID
        customer.setIsPublic(1);
        customer.setOwnerId(null);

        // 更新客户信息
        baseMapper.updateById(customer);
    }

    @Override
    public void publicPoolToPrivate(IdQuery idQuery) {
        Customer customer = baseMapper.selectById(idQuery.getId());
        if (customer == null) {
            throw new ServerException("客户不存在,无法转入公海");
        }
        customer.setIsPublic(0);
        Integer ownerId = SecurityUser.getManagerId();
        customer.setOwnerId(ownerId);
        baseMapper.updateById(customer);
    }

    @Override
    public Map<String, List> getCustomerTrendData(CustomerTrendQuery query) {
        // 处理不同请求类型的时间
        // x轴时间数据
        List<String> timeList = new ArrayList<>();
        // 统计客户变化数据
        List<Integer> countList = new ArrayList<>();
        List<CustomerTrendVO> tradeStatistics;

        if ("day".equals(query.getTransactionType())) {
            LocalDateTime now = LocalDateTime.now();
            // 截断毫秒和纳秒部分影响sql 查询结果
            LocalDateTime truncatedNow = now.truncatedTo(ChronoUnit.SECONDS);
            LocalDateTime startTime = now.withHour(0).withMinute(0).withSecond(0).truncatedTo(ChronoUnit.SECONDS);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            List<String> timeRange = new ArrayList<>();
            timeRange.add(formatter.format(startTime));
            timeRange.add(formatter.format(truncatedNow));
            query.setTimeRange(timeRange);
            timeList = getHourData(timeList);
            tradeStatistics = baseMapper.getTradeStatistics(query);
        } else if ("monthrange".equals(query.getTransactionType())) {
            query.setTimeFormat("'%Y-%m'");
            timeList = getMonthInRange(query.getTimeRange().get(0), query.getTimeRange().get(1));
            tradeStatistics = baseMapper.getTradeStatisticsByDay(query);
        } else if ("week".equals(query.getTransactionType())) {
            timeList = getWeekInRange(query.getTimeRange().get(0), query.getTimeRange().get(1));
            tradeStatistics = baseMapper.getTradeStatisticsByWeek(query);
        } else {
            query.setTimeFormat("'%Y-%m-%d'");
            timeList = DateUtils.getDatesInRange(query.getTimeRange().get(0), query.getTimeRange().get(1));
            tradeStatistics = baseMapper.getTradeStatisticsByDay(query);
        }

        // 匹配时间点查询到的数据，没有值的默认为0
        List<CustomerTrendVO> finalTradeStatistics = tradeStatistics;
        timeList.forEach(item -> {
            CustomerTrendVO statisticsVO = finalTradeStatistics.stream()
                    .filter(vo -> {
                        if ("day".equals(query.getTransactionType())) {
                            // 比较小时段
                            return item.substring(0, 2).equals(vo.getTradeTime().substring(0, 2));
                        } else {
                            return item.equals(vo.getTradeTime());
                        }
                    })
                    .findFirst()
                    .orElse(null); // 找不到则为 null

            if (statisticsVO != null) {
                countList.add(statisticsVO.getTradeCount());
            } else {
                countList.add(0);
            }
        });

        // 注：原代码缺少 Map 组装和返回逻辑，补充完整示例（根据业务场景调整）
        Map<String, List> resultMap = new HashMap<>();
        resultMap.put("timeList", timeList);
        resultMap.put("countList", countList);
        return resultMap;
    }
}