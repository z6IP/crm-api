package com.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.common.exception.ServerException;
import com.crm.common.result.PageResult;
import com.crm.convert.CustomerConvert;
import com.crm.entity.Customer;
import com.crm.entity.FollowUp;
import com.crm.entity.Lead;
import com.crm.mapper.CustomerMapper;
import com.crm.mapper.FollowUpMapper;
import com.crm.mapper.LeadMapper;
import com.crm.query.IdQuery;
import com.crm.query.LeadQuery;
import com.crm.security.user.SecurityUser;
import com.crm.service.LeadService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author crm
 * @since 2025-10-12
 */
@Service
public class LeadServiceImpl extends ServiceImpl<LeadMapper, Lead> implements LeadService {

    private final CustomerMapper customerMapper;

    private final FollowUpMapper followUpMapper;

    public LeadServiceImpl(CustomerMapper customerMapper, FollowUpMapper followUpMapper) {
        this.customerMapper = customerMapper;
        this.followUpMapper = followUpMapper;
    }

    @Override
    public PageResult<Lead> getPage(LeadQuery query) {
        Page<Lead> page = new Page<>(query.getPage(), query.getLimit());
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(query.getName())) {
            wrapper.like(Lead::getName, query.getName());
        }

        if (query.getFollowStatus() != null) {
            wrapper.eq(Lead::getFollowStatus, query.getFollowStatus());
        }

        if (query.getStatus() != null) {
            wrapper.eq(Lead::getStatus, query.getStatus());
        }

        wrapper.orderByDesc(Lead::getCreateTime);
        Page<Lead> leadPage = baseMapper.selectPage(page, wrapper);

        return new PageResult<>(leadPage.getRecords(), leadPage.getTotal());
    }

    @Override
    public void saveOrEdit(Lead lead) {
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<Lead>()
                .eq(Lead::getName, lead.getName());

        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getPhone, lead.getPhone())
        );

        if (customer != null && lead.getStatus() == 1) {
            throw new ServerException("该手机号客户已经存在，请勿重复添加线索信息");
        }

        if (lead.getId() == null) {
            // 新增逻辑：校验线索名称唯一性
            Lead selectLead = baseMapper.selectOne(wrapper);
            if (selectLead != null) {
                throw new ServerException("该线索名称已经存在，请勿重复添加线索信息");
            }
            lead.setOwnerId(SecurityUser.getManagerId());
            baseMapper.insert(lead);
        } else {
            // 编辑逻辑：排除自身ID后校验线索名称唯一性
            wrapper.ne(Lead::getId, lead.getId());
            Lead selectLead = baseMapper.selectOne(wrapper);
            if (selectLead != null) {
                throw new ServerException("该线索名称已经存在，请勿重复添加线索信息");
            }
            if (lead.getStatus() == 1) {
                // 线索已转化为客户，无需操作（保留原逻辑空实现）
            } else {
                // 线索未转化，删除关联手机号的客户记录
                customerMapper.delete(
                        new LambdaQueryWrapper<Customer>()
                                .eq(Customer::getPhone, lead.getPhone())
                );
            }
            baseMapper.updateById(lead);
        }
    }

    @Override
    public void followLead(FollowUp followUp) {
        // 根据跟进信息中的ID查询对应的线索
        Lead lead = baseMapper.selectById(followUp.getId());

        // 校验线索是否存在
        if (lead == null) {
            throw new ServerException("线索不存在,跟进失败");
        }

        // 更新线索的下次跟进状态
        lead.setNextFollowStatus(followUp.getNextFollowType());
        baseMapper.updateById(lead);

        // 重置跟进记录ID（作为新增记录），设置关联线索ID和目标类型
        followUp.setId(null);
        followUp.setCustomerId(lead.getId());
        followUp.setTargetType(1);

        // 新增线索跟进记录
        followUpMapper.insert(followUp);
    }

    @Override
    public void convertToCustomer(IdQuery idQuery) {
        // 查询待转化的线索
        Lead lead = baseMapper.selectById(idQuery.getId());

        // 校验线索是否存在
        if (lead == null) {
            throw new ServerException("该线索不存在,客户转化失败");
        }

        // 1、将线索信息转化为客户（ID置空，创建人设为当前销售）
        Customer customer = CustomerConvert.INSTANCE.leadConvert(lead);
        customer.setId(null);
        customer.setCreaterId(SecurityUser.getManagerId());
        customerMapper.insert(customer);

        // 2、修改线索状态为「已转化」（值为1，对应 LeadStatus.CONVERTED）
        lead.setStatus(1);
        baseMapper.updateById(lead);
    }
}
