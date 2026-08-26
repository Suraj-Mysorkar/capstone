package com.bank.digital.lending.model.entity;

import com.bank.digital.lending.model.enums.LoanStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "LOAN_AUDIT_LOGS")
public class LoanAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOG_ID")
    private Long logId;

    @Column(name = "APPLICATION_ID", nullable = false, length = 36)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "PREVIOUS_STATUS", length = 30)
    private LoanStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "NEW_STATUS", nullable = false, length = 30)
    private LoanStatus newStatus;

    @Column(name = "CHANGED_BY", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "COMMENTS", length = 1000)
    private String comments;

    @Column(name = "TIMESTAMP", nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public LoanAuditLog() {
    }

    public LoanAuditLog(String applicationId, LoanStatus previousStatus, LoanStatus newStatus,
                        String changedBy, String comments) {
        this.applicationId = applicationId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.comments = comments;
        this.timestamp = LocalDateTime.now();
    }

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public LoanStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(LoanStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public LoanStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(LoanStatus newStatus) {
        this.newStatus = newStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
