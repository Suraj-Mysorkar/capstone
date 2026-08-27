package com.capstone.notification.service;

import java.net.URI;
import java.util.StringJoiner;

import org.springframework.web.client.RestClient;

import com.capstone.notification.dto.EmailDto;
import com.capstone.notification.model.CustomerRegisterNotificationDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;

public class NotificationService {
	
	public void sendCustomerRegistrationNotification(CustomerRegisterNotificationDTO customerRegEvent, ExecutionContext context) {
        // Read the Logic App Workflow URL directly from environmental configurations
//        String logicAppUrl = System.getenv("LogicAppEndpoint");
        RestClient restClient = RestClient.create();

        context.getLogger().info("Forwarding message to Logic App...");
        URI staticUri = URI.create("https://prod-17.southindia.logic.azure.com:443/workflows/a4b29c1d5e814824900b41a17fa24844/triggers/When_a_HTTP_request_is_received/paths/invoke?api-version=2016-10-01&sp=%2Ftriggers%2FWhen_a_HTTP_request_is_received%2Frun&sv=1.0&sig=F--JabvW3Uwr-JsZU76HgaWWTcekahkC6HBwTEImtys");
        
        ObjectMapper objectMapper = new ObjectMapper();
        String payload = null;
        var email = buildEmail(customerRegEvent.getEmail(), buildBody(customerRegEvent.getCustomerName(), customerRegEvent.getStatus()), "Your account registration status has changed");
        try {
			payload = objectMapper.writeValueAsString(email);
		} catch (JsonProcessingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        context.getLogger().info(">>>" + payload);
        try {
            // Execute HTTP POST to trigger the Logic App
            restClient.post()
                    .uri(staticUri)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            context.getLogger().info("Successfully forwarded message to Logic App.");
        } catch (Exception e) {
        	context.getLogger().info("Failed to route message to Logic App: " + e.getMessage());
            throw e; // Throwing exception triggers automated Service Bus retry mechanics
        }
    }
	
	private String buildBody(String name, String status) {
		StringJoiner sj = new StringJoiner(" ");
		sj.add("Dear").add(name).add(",\nYour customer account registration status has been updated to")
		.add(status)
		.add("and you can view the changes by logging into your profile.");
		return sj.toString();
	}

	private EmailDto buildEmail(String email, String body, String subject ) {
		
		return new EmailDto (email, subject, body);
		
	}

}
