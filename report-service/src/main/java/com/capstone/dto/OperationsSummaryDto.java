package com.capstone.dto;

import java.util.Map;

public class OperationsSummaryDto {
	private Map<String, Long> statusCounts;

	public OperationsSummaryDto(Map<String, Long> statusCounts) {
		this.statusCounts = statusCounts;
	}

	public Map<String, Long> getStatusCounts() {
		return statusCounts;
	}

	public void setStatusCounts(Map<String, Long> statusCounts) {
		this.statusCounts = statusCounts;
	}
}