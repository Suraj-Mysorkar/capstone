package com.capstone.notification;

import com.capstone.notification.model.CustomerRegisterNotificationDTO;
import com.capstone.notification.model.LoanStatusNotificationDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

public class NotificationFunction {
	
	private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Function 1: Handles New Customer Registrations
     */
    @FunctionName("NotifyCustomerRegistration")
    public void processCustomerRegistration(
        @EventGridTrigger(name = "event") String eventContent,
        final ExecutionContext context
    ) {
        context.getLogger().info("Processing Customer Registration Event...");

        try {
            // Event Grid sends events inside a JSON array, so we read the first element [0]
            JsonNode rootArray = objectMapper.readTree(eventContent);
            JsonNode eventNode = rootArray.get(0);

            // Extract core event fields
            String eventType = eventNode.get("eventType").asText();
            String subject = eventNode.get("subject").asText();
            
            // Extract custom data object
            String dataNode = eventNode.get("data").asText();
            CustomerRegisterNotificationDTO custRegNotification = objectMapper.readValue(dataNode, CustomerRegisterNotificationDTO.class);
            String customerName = custRegNotification.getCustomerName();
            String email = custRegNotification.getEmail();

            // Your Business Logic (e.g., sending a welcome email)
            context.getLogger().info("Successfully registered customer: " + customerName + " (" + email + ")");
            context.getLogger().info("Event Subject: " + subject + " | Type: " + eventType);

        } catch (Exception e) {
            context.getLogger().severe("Failed to process customer registration: " + e.getMessage());
        }
    }
    
    /**
     * Function 2: Handles Loan Application Status Changes
     */
    @FunctionName("NotifyLoanStatus")
    public void processLoanStatus(
        @EventGridTrigger(name = "event") String eventContent,
        final ExecutionContext context
    ) {
        context.getLogger().info("Processing Loan Application Status Update...");

        try {
            // Read the first element from the Event Grid array
            JsonNode rootArray = objectMapper.readTree(eventContent);
            JsonNode eventNode = rootArray.get(0);

            // Extract custom data object
            String dataNode = eventNode.get("data").asText();
            LoanStatusNotificationDTO loanNotificationDto = objectMapper.readValue(dataNode, LoanStatusNotificationDTO.class);
            String loanId = loanNotificationDto.getLoanId();
            String status = loanNotificationDto.getStatus(); // e.g., "APPROVED", "REJECTED"
            double amount = loanNotificationDto.getAmount();

            // Your Business Logic (e.g., updating a core banking system or notifying the customer)
            context.getLogger().info("Loan ID " + loanId + " status updated to: " + status + " for amount: $" + amount);

        } catch (Exception e) {
            context.getLogger().severe("Failed to process loan status update: " + e.getMessage());
        }
    }

}
