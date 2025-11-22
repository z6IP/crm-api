package com.crm.query;

import lombok.Data;

import java.util.List;

@Data
public class ApprovalTrendQuery {
    private List<String> timeRange;

    private String transactionType;

    private String timeFormat;
}
