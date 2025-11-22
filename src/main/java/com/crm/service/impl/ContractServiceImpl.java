package com.crm.service.impl;

import com.alibaba.excel.util.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.common.exception.ServerException;
import com.crm.common.result.PageResult;
import com.crm.convert.ContractConvert;
import com.crm.entity.*;
import com.crm.mapper.*;
import com.crm.query.ApprovalQuery;
import com.crm.query.ApprovalTrendQuery;
import com.crm.query.ContractQuery;
import com.crm.query.IdQuery;
import com.crm.security.user.SecurityUser;
import com.crm.service.ContractService;
import com.crm.utils.DateUtils;
import com.crm.utils.MailUtils;
import com.crm.vo.ApprovalTrendVO;
import com.crm.vo.ContractVO;
import com.crm.vo.ProductVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.crm.utils.DateUtils.*;
import static com.crm.utils.NumberUtils.generateContractNumber;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author crm
 * @since 2025-10-12
 */
@Service
@AllArgsConstructor
@Slf4j
public class ContractServiceImpl extends ServiceImpl<ContractMapper, Contract> implements ContractService {

    private final ContractProductMapper contractProductMapper;

    private final ProductMapper productMapper;

    private final ApprovalMapper approvalMapper;

    private final MailUtils mailUtil;

    private final ManagerMapper managerMapper;

    @Override
    public PageResult<ContractVO> getPage(ContractQuery query) {
        Page<ContractVO> page = new Page<>();
        MPJLambdaWrapper<Contract> wrapper = new MPJLambdaWrapper<>();
        if (StringUtils.isNotBlank(query.getName())) {
            wrapper.like(Contract::getName, query.getName());
        }
        if (query.getCustomerId() != null) {
            wrapper.eq(Contract::getCustomerId, query.getCustomerId());
        }
        if (StringUtils.isNotBlank(query.getNumber())) {
            wrapper.like(Contract::getNumber, query.getNumber());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Contract::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Contract::getCreateTime);
        // 只查询目前登录的员工签署的合同列表
        Integer managerId = SecurityUser.getManagerId();
        log.info("查询目前登录的员工：{}的合同列表", managerId);
        wrapper.selectAll(Contract.class)
                .selectAs(Customer::getName, ContractVO::getCustomerName)
                .selectAs(Manager::getEmail, ContractVO::getOwnerEmail)
                .leftJoin(Customer.class, Customer::getId, Contract::getCustomerId)
                .leftJoin(Manager.class, Manager::getId, Contract::getOwnerId)
                .eq(Contract::getOwnerId, managerId);
        Page<ContractVO> result = baseMapper.selectJoinPage(page, ContractVO.class, wrapper);

        if (!result.getRecords().isEmpty()) {
            result.getRecords().forEach(contractVO -> {
                // 修改此处：使用正确的LambdaQueryWrapper语法
                List<ContractProduct> contractProducts = contractProductMapper.selectList(
                        new LambdaQueryWrapper<ContractProduct>()
                                .eq(ContractProduct::getCId, contractVO.getId())
                );
                contractVO.setProducts(ContractConvert.INSTANCE.convertToProductVOList(contractProducts));
            });
        }
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(ContractVO contractVO) {
        boolean isNew = contractVO.getId() == null;
        if (isNew && baseMapper.exists(new LambdaQueryWrapper<Contract>().eq(Contract::getName, contractVO.getName()))) {
            throw new ServerException("合同名称已存在，请勿重复添加");
        }

//      转换并保存合同关系
        Contract contract = ContractConvert.INSTANCE.convert(contractVO);
        contract.setCreaterId(SecurityUser.getManagerId());
        contract.setOwnerId(SecurityUser.getManagerId());

        // 处理合同金额（设置默认值）
        if (contract.getReceivedAmount() == null) {
            contract.setReceivedAmount(BigDecimal.ZERO);
        }

        // 处理合同状态（设置默认值）
        if (contract.getStatus() == null) {
            contract.setStatus(0);
        }

        if (isNew) {
            contract.setNumber(generateContractNumber());

            baseMapper.insert(contract);
            log.info("新增合同ID：{}", contract.getId());
        } else {
            Contract oldContract = baseMapper.selectById(contractVO.getId());
            if (oldContract == null) {
                throw new ServerException("合同不存在");
            }
            if (oldContract.getStatus() == 1) {
                throw new ServerException("合同正在审核中，请勿执行修改操作");
            }
            baseMapper.updateById(contract);
        }
//        处理商品和合同的关联关系
        handleContractProducts(contract.getId(), contractVO.getProducts());
    }

    @Override
    public void startApproval(IdQuery idQuery) {
        Contract contract = baseMapper.selectById(idQuery.getId());
        if (contract == null) {
            throw new ServerException("合同不存在");
        }
        if (contract.getStatus() != 0) {
            throw new ServerException("该合同已审核通过，请勿重复提交");
        }
        contract.setStatus(1);
        baseMapper.updateById(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approvalContract(ApprovalQuery query) {
        Contract contract = baseMapper.selectById(query.getId());
        if (contract == null) {
            throw new ServerException("合同不存在");
        }
        if (contract.getStatus() != 1) {
            throw new ServerException("合同还未发起审核或已审核，请勿重复提交");
        }
        // 添加审核内容，判断审核状态
        String approvalContent = query.getType() == 0 ? "合同审核通过" : "合同审核未通过";
        Integer contractStatus = query.getType() == 0 ? 2 : 3;
        Approval approval = new Approval();
        approval.setType(0);
        approval.setStatus(query.getType());
        approval.setCreaterId(SecurityUser.getManagerId());
        approval.setContractId(contract.getId());
        approval.setComment(approvalContent);
        approvalMapper.insert(approval);
        contract.setStatus(contractStatus);
        baseMapper.updateById(contract);

        // 核心新增：审核通过时发送邮件
        if (query.getType() == 0) {
            // 1. 获取创建合同的销售（ownerId对应管理员ID）
            Integer ownerId = contract.getOwnerId();
            Manager ownerManager = managerMapper.selectById(ownerId);
            if (ownerManager == null || StringUtils.isBlank(ownerManager.getEmail())) {
                log.warn("合同{}的创建者（ID：{}）未配置邮箱，无法发送通知", contract.getNumber(), ownerId);
                return;
            }
            // 2. 发送邮件
            mailUtil.sendContractPassMail(
                    ownerManager.getEmail(),
                    contract.getName(),
                    contract.getNumber()
            );
        }
    }


    private void handleContractProducts(Integer contractId, List<ProductVO> newProductList) {
        if (newProductList == null) {
            return;
        }

        List<ContractProduct> oldProducts = contractProductMapper.selectList(
                new LambdaQueryWrapper<ContractProduct>().eq(ContractProduct::getCId, contractId)
        );

        // === 1. 新增商品 ===
        List<ProductVO> added = newProductList.stream()
                .filter(np -> oldProducts.stream().noneMatch(op -> op.getPId().equals(np.getPId())))
                .toList();

        for (ProductVO p : added) {
            Product product = checkProduct(p.getPId(), p.getCount());
            decreaseStock(product, p.getCount());
            ContractProduct cp = builderContractProduct(contractId, product, p.getCount());
            contractProductMapper.insert(cp);
        }

        // === 2. 修改数量 ===
        List<ProductVO> changed = newProductList.stream()
                .filter(np -> oldProducts.stream()
                        .anyMatch(op -> op.getPId().equals(np.getPId()) && !op.getCount().equals(np.getCount())))
                .toList();

        for (ProductVO p : changed) {
            ContractProduct old = oldProducts.stream()
                    .filter(op -> op.getPId().equals(p.getPId()))
                    .findFirst().orElseThrow();

            Product product = checkProduct(p.getPId(), 0);
            int diff = p.getCount() - old.getCount();

            // 库存调整
            if (diff > 0) {
                decreaseStock(product, diff);
            } else if (diff < 0) {
                increaseStock(product, -diff);
            }

            // 更新合同商品
            old.setCount(p.getCount());
            old.setPrice(product.getPrice());
            old.setTotalPrice(product.getPrice().multiply(BigDecimal.valueOf(p.getCount())));
            contractProductMapper.updateById(old);
        }

        // === 3. 删除商品 ===
        List<ContractProduct> removed = oldProducts.stream()
                .filter(op -> newProductList.stream().noneMatch(np -> np.getPId().equals(op.getPId())))
                .toList();

        for (ContractProduct rm : removed) {
            Product product = productMapper.selectById(rm.getPId());
            if (product != null) {
                increaseStock(product, rm.getCount());
            }
            contractProductMapper.deleteById(rm.getId());
        }
    }

    @Override
    public Map<String, List> getApprovalTrendData(ApprovalTrendQuery query) {
        List<String> timeList = new ArrayList<>();
        // 统计客户变化数据
        List<Integer> countList = new ArrayList<>();
        List<ApprovalTrendVO> tradeStatistics;

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
        List<ApprovalTrendVO> finalTradeStatistics = tradeStatistics;
        timeList.forEach(item -> {
            ApprovalTrendVO statisticsVO = finalTradeStatistics.stream()
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


    // 创建关联关系
    private ContractProduct builderContractProduct(Integer contractId, Product product, int count) {
        ContractProduct contractProduct = new ContractProduct();
        contractProduct.setCId(contractId);
        contractProduct.setPId(product.getId());
        contractProduct.setPName(product.getName());
        contractProduct.setPrice(product.getPrice());
        contractProduct.setCount(count);
        contractProduct.setTotalPrice(product.getPrice().multiply(new BigDecimal(count)));
        return contractProduct;
    }

    //    检查商品数量
    private Product checkProduct(Integer productId, int count) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new ServerException("商品不存在");
        }
        if (product.getStock() < count) {
            throw new ServerException("商品库存不足");
        }
        return product;
    }

    //    增加库存
    private void increaseStock(Product product, int count) {
        product.setStock(product.getStock() + count);
        product.setSales(product.getSales() - count);
        productMapper.updateById(product);
    }

    //    减少库存
    private void decreaseStock(Product product, int count) {
        product.setStock(product.getStock() - count);
        product.setSales(product.getSales() + count);
        productMapper.updateById(product);
    }
}