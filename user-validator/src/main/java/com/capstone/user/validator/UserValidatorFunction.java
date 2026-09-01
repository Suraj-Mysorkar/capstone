package com.capstone.user.validator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

import com.capstone.user.validator.dto.UserCredentialsDto;
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

	 @FunctionName("check-password")
	    public HttpResponseMessage run(
	            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION) 
	            HttpRequestMessage<Optional<UserCredentialsDto>> request,
	            final ExecutionContext context) {

	        UserCredentialsDto creds = request.getBody().orElse(null);
	        if (creds == null || creds.getUsername() == null || creds.getPassword() == null) {
	            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Missing credentials").build();
	        }

			String pwd = creds.getPassword();
			
			context.getLogger().info(">>>>>>" + creds.getUsername() + ">>>>>"+ pwd);

	        // 1. Run the hashing math on the typed password
	        // String computedHash = hashPassword(creds.getPassword());

	        // 2. Mock Database Check (Replace this line with your actual JPA/JDBC database call)
	        User userDb = fetchHashFromYourDatabase(creds.getUsername(),  context); 
	        context.getLogger().info(">>>>>>" +  userDb.getLoginPassword());
	        // 3. Compare hashes
	        if (userDb != null && pwd.equals(userDb.getLoginPassword())) {
	            context.getLogger().info("User verified successfully!");
	            UserLoginResponseDto userResponse = buildResponse(userDb);
	            return request.createResponseBuilder(HttpStatus.OK).body(userResponse).build();
	        } else {
	            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED).body("Invalid credentials").build();
	        }
	    }

		private UserLoginResponseDto buildResponse(User userDb) {
			UserLoginResponseDto userResponse = new UserLoginResponseDto();
			userResponse.setUsername(userDb.getName());
			userResponse.setUserId(userDb.getUserId());
			userResponse.setUserRole(userDb.getRole());
			userResponse.setStatus("valid");
			return userResponse;
		}

		private String hashPassword(String password) {
	        try {
	            MessageDigest digest = MessageDigest.getInstance("SHA-256");
	            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
	            StringBuilder hexString = new StringBuilder();
	            for (byte b : hash) {
	                String hex = Integer.toHexString(0xff & b);
	                if (hex.length() == 1) hexString.append('0');
	                hexString.append(hex);
	            }
	            return hexString.toString();
	        } catch (Exception e) {
	            throw new RuntimeException(e);
	        }
	    }

	    // Dummy method representing your database lookup
//	    private String fetchHashFromYourDatabase(String username) {
//	        if ("employee123".equals(username)) return "2bb80dc437a3b3a6c9cf1c614b62db5244..."; 
//	        return null;
//	    }
	    
	    /**
	     * Connects to Azure SQL and looks up the password hash for a specific username
	     */
	    private User fetchHashFromYourDatabase(String username, ExecutionContext context) {
	        // Read the connection string safely from environment variables
	        String connectionString = System.getenv("SqlConnectionString");
	        
	        // Secure Parameterized SQL Query (Prevents SQL Injection attacks)
	        String query = "SELECT * FROM Users WHERE loginid = ?";
	        
	        User user = null;

	        // Automatically manages and closes database resources using try-with-resources
	        try (Connection connection = DriverManager.getConnection(connectionString);
	             PreparedStatement statement = connection.prepareStatement(query)) {
	            
	            statement.setString(1, username);
	            
	            try (ResultSet resultSet = statement.executeQuery()) {
	                if (resultSet.next()) {
	                	user = new User();
	                	user.setEmail(resultSet.getString("email"));
	                	user.setLoginId(resultSet.getString("loginid"));
	                	user.setLoginPassword(resultSet.getString("login_password"));
	                	user.setName(resultSet.getString("name"));
	                	user.setRole(resultSet.getString("user_role"));
	                	System.out.println("User Id " + resultSet.getLong("User_ID"));
	                	user.setUserId(resultSet.getLong("User_ID"));
	                    // Extract the text string from your database column
	                    return user;
	                }
	            }
	        } catch (Exception e) {
	            context.getLogger().severe("Database connection error: " + e.getMessage());
	        }
	        return null; // Return null if the user doesn't exist or database fails
	    }
}
