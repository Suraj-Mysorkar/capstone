package com.bank.digital.lending.model.entity;

import com.bank.digital.lending.model.enums.LoanType;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "LOAN_SCHEMES")
public class LoanScheme {

    @Id
    @Column(name = "SCHEME_ID", length = 36)
    private String schemeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "LOAN_TYPE", nullable = false, length = 30)
    private LoanType loanType;

    @Column(name = "SCHEME_NAME", nullable = false, length = 100)
    private String schemeName;

    @Column(name = "MIN_AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "MAX_AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "MIN_TENURE_MONTHS", nullable = false)
    private Integer minTenureMonths;

    @Column(name = "MAX_TENURE_MONTHS", nullable = false)
    private Integer maxTenureMonths;

    @Column(name = "BASE_INTEREST_RATE", nullable = false, precision = 5, scale = 2)
    private BigDecimal baseInterestRate;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    public LoanScheme() {
    }

    public LoanScheme(String schemeId, LoanType loanType, String schemeName, BigDecimal minAmount,
                      BigDecimal maxAmount, Integer minTenureMonths, Integer maxTenureMonths,
                      BigDecimal baseInterestRate, Boolean isActive) {
        this.schemeId = schemeId;
        this.loanType = loanType;
        this.schemeName = schemeName;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.minTenureMonths = minTenureMonths;
        this.maxTenureMonths = maxTenureMonths;
        this.baseInterestRate = baseInterestRate;
        this.isActive = isActive;
    }

    public String getSchemeId() {
        return schemeId;
    }

    public void setSchemeId(String schemeId) {
        this.schemeId = schemeId;
    }

    public LoanType getLoanType() {
        return loanType;
    }

    public void setLoanType(LoanType loanType) {
        this.loanType = loanType;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public Integer getMinTenureMonths() {
        return minTenureMonths;
    }

    public void setMinTenureMonths(Integer minTenureMonths) {
        this.minTenureMonths = minTenureMonths;
    }

    public Integer getMaxTenureMonths() {
        return maxTenureMonths;
    }

    public void setMaxTenureMonths(Integer maxTenureMonths) {
        this.maxTenureMonths = maxTenureMonths;
    }

    public BigDecimal getBaseInterestRate() {
        return baseInterestRate;
    }

    public void setBaseInterestRate(BigDecimal baseInterestRate) {
        this.baseInterestRate = baseInterestRate;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}
