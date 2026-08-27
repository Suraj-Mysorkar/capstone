package com.capstone.notification;

import com.capstone.notification.model.CustomerRegisterNotificationDTO;
import com.capstone.notification.model.LoanStatusNotificationDTO;
import com.capstone.notification.service.NotificationService;
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
            JsonNode rootArray = objectMapper.readTree(eventContent);
            context.getLogger().info(rootArray.toString());
//            JsonNode eventNode = rootArray.get(0);

            String eventType = rootArray.get("eventType").asText();
            String subject = rootArray.get("subject").asText();
            
            // FIX: Use treeToValue instead of .asText() to read the inner JSON object data safely
            CustomerRegisterNotificationDTO custRegNotification = objectMapper.treeToValue(
            		rootArray.get("data"), 
                CustomerRegisterNotificationDTO.class
            );
            
            String customerName = custRegNotification.getCustomerName();
            String email = custRegNotification.getEmail();

            context.getLogger().info("Successfully registered customer: " + customerName + " (" + email + ")");
            context.getLogger().info("Event Subject: " + subject + " | Type: " + eventType);
            
            NotificationService notificationService = new NotificationService();
            notificationService.sendCustomerRegistrationNotification(custRegNotification, context);

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
            JsonNode rootArray = objectMapper.readTree(eventContent);
//            JsonNode eventNode = rootArray.get(0);

            // FIX: Use treeToValue instead of .asText() to read the inner JSON object data safely
            LoanStatusNotificationDTO loanNotificationDto = objectMapper.treeToValue(
            		rootArray.get("data"), 
                LoanStatusNotificationDTO.class
            );
            
            String loanId = loanNotificationDto.getLoanId();
            String status = loanNotificationDto.getStatus(); 
            double amount = loanNotificationDto.getAmount();

            context.getLogger().info("Loan ID " + loanId + " status updated to: " + status + " for amount: $" + amount);
            
            NotificationService notificationService = new NotificationService();
            notificationService.sendLoanApplicationNotification(loanNotificationDto, context);

        } catch (Exception e) {
            context.getLogger().severe("Failed to process loan status update: " + e.getMessage());
        }
    }

}
