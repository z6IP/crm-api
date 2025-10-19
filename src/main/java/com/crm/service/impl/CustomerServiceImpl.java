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
import com.crm.query.IdQuery;
import com.crm.security.user.SecurityUser;
import com.crm.service.CustomerService;
import com.crm.utils.ExcelUtils;
import com.crm.vo.CustomerVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.util.List;


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
            customerToSave.setCreaterId(currentManagerId); // 设置创建人ID
            customerToSave.setOwnerId(currentManagerId);   // 设置负责人ID

            // 4. 插入数据库
            baseMapper.insert(customerToSave);
        } else {
            // 场景2：修改客户 - 校验手机号是否被其他客户占用（排除自身）
            wrapper.ne(Customer::getId, customerVO.getId()); // 追加「不等于当前ID」条件
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
}