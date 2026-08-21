package com.capstone.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "Loan_Applications")
public class LoanApplication {

	@Id
	@Column(name = "Application_ID", length = 36)
	private String applicationId;

	// Many loan applications can belong to one single customer
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "Customer_ID", nullable = false)
	private Customer customer;

	@Column(name = "Loan_Type", length = 30, nullable = false)
	private String loanType;

	@Column(name = "Loan_Amount", precision = 18, scale = 2, nullable = false)
	private BigDecimal loanAmount;

	@Column(name = "Tenure_Months", nullable = false)
	private Integer tenureMonths;

	@Column(name = "Interest_Rate", precision = 5, scale = 2)
	private BigDecimal interestRate;

	@Column(name = "Status", length = 30, nullable = false)
	private String status; // SUBMITTED, IN_REVIEW, APPROVED, REJECTED

	@Column(name = "Assigned_To_Manager", length = 50)
	private String assignedToManager;

	@Column(name = "Created_At", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
	private LocalDateTime createdAt = LocalDateTime.now();

	// One loan application can have multiple documents attached to it
	@OneToMany(mappedBy = "loanApplication", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Document> documents;

	// --- Constructors ---
	public LoanApplication() {
	}

	// --- Getters and Setters ---
	public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public String getLoanType() {
		return loanType;
	}

	public void setLoanType(String loanType) {
		this.loanType = loanType;
	}

	public BigDecimal getLoanAmount() {
		return loanAmount;
	}

	public void setLoanAmount(BigDecimal loanAmount) {
		this.loanAmount = loanAmount;
	}

	public Integer getTenureMonths() {
		return tenureMonths;
	}

	public void setTenureMonths(Integer tenureMonths) {
		this.tenureMonths = tenureMonths;
	}

	public BigDecimal getInterestRate() {
		return interestRate;
	}

	public void setInterestRate(BigDecimal interestRate) {
		this.interestRate = interestRate;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getAssignedToManager() {
		return assignedToManager;
	}

	public void setAssignedToManager(String assignedToManager) {
		this.assignedToManager = assignedToManager;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public List<Document> getDocuments() {
		return documents;
	}

	public void setDocuments(List<Document> documents) {
		this.documents = documents;
	}
}