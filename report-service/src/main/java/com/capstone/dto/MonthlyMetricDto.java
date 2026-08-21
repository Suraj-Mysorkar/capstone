package com.capstone.dto;

import java.math.BigDecimal;

public class MonthlyMetricDto {
	private String month; // Format: YYYY-MM
	private Long newCustomers;
	private Long totalApplications;
	private BigDecimal totalLoanVolume;

	public MonthlyMetricDto(String month, Long newCustomers, Long totalApplications, BigDecimal totalLoanVolume) {
		this.month = month;
		this.newCustomers = newCustomers;
		this.totalApplications = totalApplications;
		this.totalLoanVolume = totalLoanVolume == null ? BigDecimal.ZERO : totalLoanVolume;
	}

	// Getters and Setters
	public String getMonth() {
		return month;
	}

	public void setMonth(String month) {
		this.month = month;
	}

	public Long getNewCustomers() {
		return newCustomers;
	}

	public void setNewCustomers(Long newCustomers) {
		this.newCustomers = newCustomers;
	}

	public Long getTotalApplications() {
		return totalApplications;
	}

	public void setTotalApplications(Long totalApplications) {
		this.totalApplications = totalApplications;
	}

	public BigDecimal getTotalLoanVolume() {
		return totalLoanVolume;
	}

	public void setTotalLoanVolume(BigDecimal totalLoanVolume) {
		this.totalLoanVolume = totalLoanVolume;
	}
}