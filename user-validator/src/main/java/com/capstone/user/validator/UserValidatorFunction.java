package com.capstone.user.validator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.capstone.user.validator.dto.UserLoginResponseDto;
import com.capstone.user.validator.model.User;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

public class UserValidatorFunction {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (Throwable t) {
            System.err.println("SQLServerDriver class loading: " + t.getMessage());
        }
    }

    @FunctionName("check-password")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION) 
            HttpRequestMessage<String> request,
            final ExecutionContext context) {

        String body = request.getBody();
        context.getLogger().info("Received check-password request body: " + body);

        if (body == null || body.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Missing request body\"}")
                    .build();
        }

        String username = "";
        String pwd = "";

        try {
            JsonNode root = mapper.readTree(body);
            if (root.has("username")) username = root.get("username").asText().trim();
            if (root.has("password")) pwd = root.get("password").asText().trim();
        } catch (Exception e) {
            context.getLogger().severe("Failed to parse JSON body: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Invalid JSON body\"}")
                    .build();
        }

        if (username.isEmpty() || pwd.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Missing credentials\"}")
                    .build();
        }

        context.getLogger().info("Validating credentials for username: " + username);

        User userDb = fetchUserFromDatabase(username, context); 
        
        if (userDb != null && userDb.getLoginPassword() != null && pwd.equals(userDb.getLoginPassword().trim())) {
            context.getLogger().info("User " + username + " verified successfully with role: " + userDb.getRole());
            UserLoginResponseDto userResponse = new UserLoginResponseDto();
            userResponse.setUsername(userDb.getName() != null ? userDb.getName() : userDb.getLoginId());
            userResponse.setUserId(userDb.getUserId());
            userResponse.setUserRole(userDb.getRole());
            userResponse.setStatus("valid");
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(userResponse)
                    .build();
        } else {
            context.getLogger().warning("Invalid credentials or user not found for: " + username);
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Invalid credentials\"}")
                    .build();
        }
    }

    private User fetchUserFromDatabase(String username, ExecutionContext context) {
        String connectionString = System.getenv("SqlConnectionString");
        if (connectionString == null || connectionString.isBlank()) {
            connectionString = "jdbc:sqlserver://smzen-capstone.database.windows.net:1433;database=smzen-capstone-db;user=cs_admin;password=Capstone@;encrypt=true;trustServerCertificate=false;loginTimeout=30;";
        }
        
        String query = "SELECT * FROM users WHERE LOWER(loginid) = LOWER(?)";
        
        try (Connection connection = DriverManager.getConnection(connectionString);
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, username);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    User user = new User();
                    user.setEmail(resultSet.getString("email"));
                    user.setLoginId(resultSet.getString("loginid"));
                    user.setLoginPassword(resultSet.getString("login_password"));
                    user.setName(resultSet.getString("name"));
                    user.setRole(resultSet.getString("user_role"));
                    try {
                        user.setUserId(resultSet.getLong("User_ID"));
                    } catch (Exception e) {
                        user.setUserId(1L);
                    }
                    return user;
                }
            }
        } catch (Exception e) {
            context.getLogger().severe("Database query error: " + e.getMessage());
        }
        return null;
    }
}
