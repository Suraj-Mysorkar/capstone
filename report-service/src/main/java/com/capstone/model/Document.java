package com.capstone.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "Documents")
public class Document {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "Document_ID")
	private Long documentId;

	// Many documents can belong to one single loan application
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "Application_ID", nullable = false)
	private LoanApplication loanApplication;

	@Column(name = "Doc_Type", length = 50, nullable = false)
	private String docType; // Identity, Income, Address Proof

	@Column(name = "Blob_Storage_Path", length = 500, nullable = false)
	private String blobStoragePath;

	@Column(name = "Version")
	private Integer version = 1;

	@Column(name = "Uploaded_At", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
	private LocalDateTime uploadedAt = LocalDateTime.now();

	// --- Constructors ---
	public Document() {
	}

	// --- Getters and Setters ---
	public Long getDocumentId() {
		return documentId;
	}

	public void setDocumentId(Long documentId) {
		this.documentId = documentId;
	}

	public LoanApplication getLoanApplication() {
		return loanApplication;
	}

	public void setLoanApplication(LoanApplication loanApplication) {
		this.loanApplication = loanApplication;
	}

	public String getDocType() {
		return docType;
	}

	public void setDocType(String docType) {
		this.docType = docType;
	}

	public String getBlobStoragePath() {
		return blobStoragePath;
	}

	public void setBlobStoragePath(String blobStoragePath) {
		this.blobStoragePath = blobStoragePath;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public LocalDateTime getUploadedAt() {
		return uploadedAt;
	}

	public void setUploadedAt(LocalDateTime uploadedAt) {
		this.uploadedAt = uploadedAt;
	}
}