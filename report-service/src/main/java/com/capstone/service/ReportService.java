package com.capstone.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.capstone.dto.MonthlyMetricDto;
import com.capstone.dto.OperationsSummaryDto;
import com.capstone.repository.CustomerRepository;
import com.capstone.repository.LoanApplicationRepository;

@Service
public class ReportService {

    private final LoanApplicationRepository loanRepo;
    private final CustomerRepository customerRepo;

    public ReportService(LoanApplicationRepository loanRepo, CustomerRepository customerRepo) {
        this.loanRepo = loanRepo;
        this.customerRepo = customerRepo;
    }

    public OperationsSummaryDto getOperationsSummary() {
        List<Object[]> results = loanRepo.countApplicationsByStatus();
        Map<String, Long> summaryMap = new HashMap<>();

        // Pre-populate core operational metrics with zeros
        summaryMap.put("APPROVED", 0L);
        summaryMap.put("REJECTED", 0L);
        summaryMap.put("PENDING_REVIEW", 0L);
        summaryMap.put("IN_REVIEW", 0L); // Includes matching states found in sample data

        for (Object[] result : results) {
            String status = (String) result[0];
            Long count = (Long) result[1];
            summaryMap.put(status, count);
        }

        // Combine custom statuses to match specific "PENDING_REVIEW" requests if needed
        if (summaryMap.containsKey("IN_REVIEW")) {
            summaryMap.put("PENDING_REVIEW", summaryMap.get("PENDING_REVIEW") + summaryMap.get("IN_REVIEW"));
        }

        return new OperationsSummaryDto(summaryMap);
    }

    public List<MonthlyMetricDto> getExecutiveMetrics() {
        Map<String, Long> customerTrends = new HashMap<>();
        List<Object[]> customerData = customerRepo.getMonthlyCustomerAcquisitions();
        for (Object[] row : customerData) {
            customerTrends.put((String) row[0], ((Number) row[1]).longValue());
        }

        List<MonthlyMetricDto> trends = new ArrayList<>();
        List<Object[]> loanData = loanRepo.getMonthlyLoanMetrics();

        for (Object[] row : loanData) {
            String month = (String) row[0];
            Long appCount = ((Number) row[1]).longValue();
            BigDecimal totalVolume = (BigDecimal) row[2];
            Long newCustomers = customerTrends.getOrDefault(month, 0L);

            trends.add(new MonthlyMetricDto(month, newCustomers, appCount, totalVolume));
        }

        return trends;
    }
}
