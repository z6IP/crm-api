package com.crm.convert;

import com.crm.entity.Contract;
import com.crm.entity.ContractProduct;
import com.crm.vo.ContractVO;
import com.crm.vo.ProductVO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-11-06T14:35:47+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.12 (Oracle Corporation)"
)
public class ContractConvertImpl implements ContractConvert {

    @Override
    public Contract convert(ContractVO contractVO) {
        if ( contractVO == null ) {
            return null;
        }

        Contract contract = new Contract();

        contract.setId( contractVO.getId() );
        contract.setNumber( contractVO.getNumber() );
        contract.setName( contractVO.getName() );
        contract.setAmount( contractVO.getAmount() );
        contract.setReceivedAmount( contractVO.getReceivedAmount() );
        contract.setSignTime( contractVO.getSignTime() );
        contract.setCustomerId( contractVO.getCustomerId() );
        contract.setOpportunityId( contractVO.getOpportunityId() );
        contract.setStatus( contractVO.getStatus() );
        contract.setRemark( contractVO.getRemark() );
        contract.setCreateTime( contractVO.getCreateTime() );
        contract.setUpdateTime( contractVO.getUpdateTime() );
        contract.setCreaterId( contractVO.getCreaterId() );
        contract.setOwnerId( contractVO.getOwnerId() );
        contract.setStartTime( contractVO.getStartTime() );
        contract.setEndTime( contractVO.getEndTime() );

        return contract;
    }

    @Override
    public ProductVO convertToProductVO(ContractProduct contractProduct) {
        if ( contractProduct == null ) {
            return null;
        }

        ProductVO productVO = new ProductVO();

        productVO.setId( contractProduct.getId() );
        productVO.setPId( contractProduct.getPId() );
        productVO.setPName( contractProduct.getPName() );
        productVO.setTotalPrice( contractProduct.getTotalPrice() );
        productVO.setCount( contractProduct.getCount() );
        productVO.setPrice( contractProduct.getPrice() );

        return productVO;
    }

    @Override
    public List<ProductVO> convertToProductVOList(List<ContractProduct> contractProductList) {
        if ( contractProductList == null ) {
            return null;
        }

        List<ProductVO> list = new ArrayList<ProductVO>( contractProductList.size() );
        for ( ContractProduct contractProduct : contractProductList ) {
            list.add( convertToProductVO( contractProduct ) );
        }

        return list;
    }
}
