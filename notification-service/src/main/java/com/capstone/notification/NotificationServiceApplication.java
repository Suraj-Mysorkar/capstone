package com.capstone.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.capstone.notification.model.CustomerRegisterNotificationDTO;

@SpringBootApplication
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
		
		NotificationFunction funciton = new NotificationFunction();
		
		String custNotificationEventContent = """
				[
				  {
				    "id": "1001",
				    "subject": "/banking/customers",
				    "eventType": "Bank.CustomerRegistered",
				    "eventTime": "2026-08-24T12:00:00Z",
				    "data": {
				      "customerName": "John Doe",
				      "email": "johndoe@example.com"
				    },
				    "dataVersion": "1.0"
				  }
				]
				""";
		
	}

}
