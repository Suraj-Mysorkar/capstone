package com.capstone.notification.dto;

public class EmailDto {
	
	private String emailTo;
	
	private String emailSubject;
	
	private String emailBody;
	
	public EmailDto() {
		
	}

	public EmailDto(String toEmail, String subject, String body) {
		super();
		this.emailTo = toEmail;
		this.emailSubject = subject;
		this.emailBody = body;
	}

	public String getEmailTo() {
		return emailTo;
	}

	public void setEmailTo(String emailTo) {
		this.emailTo = emailTo;
	}

	public String getEmailSubject() {
		return emailSubject;
	}

	public void setEmailSubject(String emailSubject) {
		this.emailSubject = emailSubject;
	}

	public String getEmailBody() {
		return emailBody;
	}

	public void setEmailBody(String emailBody) {
		this.emailBody = emailBody;
	}

}
