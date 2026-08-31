package com.capstone.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LoanStatusNotificationDTO {

    private String applicationId;
    private String loanId;
    private String customerId;
    private String customerName;
    private String customerEmail;
    private String email;
    private String loanType;
    private String status;
    private String finalStatus;
    private double amount;
    private double loanAmount;
    private Integer tenureMonths;
    private Double interestRate;
    private Double calculatedEMI;
    private Integer riskScore;
    private Double dtiRatio;
    private String reviewedBy;
    private String decisionRemarks;

    public LoanStatusNotificationDTO() {}

    public String getApplicationId() {
        return applicationId != null ? applicationId : loanId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
        if (this.loanId == null) {
            this.loanId = applicationId;
        }
    }

    public String getLoanId() {
        return loanId != null ? loanId : applicationId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
        if (this.applicationId == null) {
            this.applicationId = loanId;
        }
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName != null ? customerName : "Valued Customer";
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail != null ? customerEmail : email;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
        if (this.email == null) {
            this.email = customerEmail;
        }
    }

    public String getEmail() {
        return email != null ? email : customerEmail;
    }

    public void setEmail(String email) {
        this.email = email;
        if (this.customerEmail == null) {
            this.customerEmail = email;
        }
    }

    public String getLoanType() {
        return loanType != null ? loanType : "PERSONAL";
    }

    public void setLoanType(String loanType) {
        this.loanType = loanType;
    }

    public String getStatus() {
        if (status != null) return status;
        if (finalStatus != null) return finalStatus;
        return "PROCESSING";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFinalStatus() {
        return finalStatus != null ? finalStatus : status;
    }

    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
        if (this.status == null) {
            this.status = finalStatus;
        }
    }

    public double getAmount() {
        return amount > 0 ? amount : loanAmount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
        if (this.loanAmount == 0) {
            this.loanAmount = amount;
        }
    }

    public double getLoanAmount() {
        return loanAmount > 0 ? loanAmount : amount;
    }

    public void setLoanAmount(double loanAmount) {
        this.loanAmount = loanAmount;
        if (this.amount == 0) {
            this.amount = loanAmount;
        }
    }

    public Integer getTenureMonths() {
        return tenureMonths != null ? tenureMonths : 12;
    }

    public void setTenureMonths(Integer tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public Double getInterestRate() {
        return interestRate != null ? interestRate : 10.5;
    }

    public void setInterestRate(Double interestRate) {
        this.interestRate = interestRate;
    }

    public Double getCalculatedEMI() {
        return calculatedEMI != null ? calculatedEMI : 0.0;
    }

    public void setCalculatedEMI(Double calculatedEMI) {
        this.calculatedEMI = calculatedEMI;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public Double getDtiRatio() {
        return dtiRatio;
    }

    public void setDtiRatio(Double dtiRatio) {
        this.dtiRatio = dtiRatio;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getDecisionRemarks() {
        return decisionRemarks;
    }

    public void setDecisionRemarks(String decisionRemarks) {
        this.decisionRemarks = decisionRemarks;
    }
}
