package com.bank.digital.lending.model.entity;

import com.bank.digital.lending.model.enums.EmploymentType;
import com.bank.digital.lending.model.enums.LoanStatus;
import com.bank.digital.lending.model.enums.LoanType;
import com.bank.digital.lending.model.entity.BaseAuditable;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "LOAN_APPLICATIONS")
@Audited
public class LoanApplication extends BaseAuditable {

    @Id
    @Column(name = "APPLICATION_ID", length = 36)
    private String applicationId;

    @Column(name = "CUSTOMER_ID", nullable = false, length = 36)
    private String customerId;

    @Column(name = "CUSTOMER_NAME", nullable = false, length = 100)
    private String customerName;

    @Column(name = "CUSTOMER_EMAIL", nullable = false, length = 100)
    private String customerEmail;

    @Column(name = "CUSTOMER_PHONE", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "MONTHLY_INCOME", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "EXISTING_LIABILITIES", precision = 18, scale = 2)
    private BigDecimal existingLiabilities = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "EMPLOYMENT_TYPE", nullable = false, length = 50)
    private EmploymentType employmentType;

    @ManyToOne(fetch = FetchType.EAGER)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @JoinColumn(name = "SCHEME_ID", referencedColumnName = "SCHEME_ID", nullable = false)
    private LoanScheme scheme;

    @Enumerated(EnumType.STRING)
    @Column(name = "LOAN_TYPE", nullable = false, length = 30)
    private LoanType loanType;

    @Column(name = "LOAN_AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal loanAmount;

    @Column(name = "DOCUMENT_PROVIDED", nullable = false)
    private boolean documentProvided = false;

    @Column(name = "TENURE_MONTHS", nullable = false)
    private Integer tenureMonths;

    @Column(name = "INTEREST_RATE", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "CALCULATED_EMI", nullable = false, precision = 18, scale = 2)
    private BigDecimal calculatedEMI;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private LoanStatus status = LoanStatus.SUBMITTED;

    @Column(name = "RISK_SCORE")
    private Integer riskScore;

    @Column(name = "DTI_RATIO", precision = 5, scale = 2)
    private BigDecimal dtiRatio;

    @Column(name = "ORCHESTRATION_INSTANCE_ID", length = 100)
    private String orchestrationInstanceId;

    @Column(name = "ASSIGNED_MANAGER", length = 100)
    private String assignedManager;

    @Column(name = "DECISION_REMARKS", length = 1000)
    private String decisionRemarks;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "applicationId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LoanDocument> documents = new ArrayList<>();

    public LoanApplication() {
    }

    @PrePersist
    public void onPrePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(BigDecimal monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
    }

    public BigDecimal getExistingLiabilities() {
        return existingLiabilities;
    }

    public void setExistingLiabilities(BigDecimal existingLiabilities) {
        this.existingLiabilities = existingLiabilities;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public LoanScheme getScheme() {
        return scheme;
    }

    public void setScheme(LoanScheme scheme) {
        this.scheme = scheme;
    }

    public LoanType getLoanType() {
        return loanType;
    }

    public void setLoanType(LoanType loanType) {
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

    public BigDecimal getCalculatedEMI() {
        return calculatedEMI;
    }

    public void setCalculatedEMI(BigDecimal calculatedEMI) {
        this.calculatedEMI = calculatedEMI;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public BigDecimal getDtiRatio() {
        return dtiRatio;
    }

    public void setDtiRatio(BigDecimal dtiRatio) {
        this.dtiRatio = dtiRatio;
    }

    public String getOrchestrationInstanceId() {
        return orchestrationInstanceId;
    }

    public void setOrchestrationInstanceId(String orchestrationInstanceId) {
        this.orchestrationInstanceId = orchestrationInstanceId;
    }

    public String getAssignedManager() {
        return assignedManager;
    }

    public void setAssignedManager(String assignedManager) {
        this.assignedManager = assignedManager;
    }

    public String getDecisionRemarks() {
        return decisionRemarks;
    }

    public void setDecisionRemarks(String decisionRemarks) {
        this.decisionRemarks = decisionRemarks;
    }

    // ----- Document Provided flag -----
    public boolean isDocumentProvided() {
        return documentProvided;
    }

    public void setDocumentProvided(boolean documentProvided) {
        this.documentProvided = documentProvided;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<LoanDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<LoanDocument> documents) {
        this.documents = documents;
    }
}
