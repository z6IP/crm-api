package com.crm.mapper;

import com.crm.entity.Contract;
import com.crm.query.ApprovalTrendQuery;
import com.crm.vo.ApprovalTrendVO;
import com.github.yulichang.base.MPJBaseMapper;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author crm
 * @since 2025-10-12
 */
public interface ContractMapper extends MPJBaseMapper<Contract> {

    List<ApprovalTrendVO> getTradeStatistics(@Param("query") ApprovalTrendQuery query);

    List<ApprovalTrendVO> getTradeStatisticsByDay(@Param("query") ApprovalTrendQuery query);

    List<ApprovalTrendVO> getTradeStatisticsByWeek(@Param("query") ApprovalTrendQuery query);
}
