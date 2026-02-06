package com.example.ecommerce.controller;

import com.example.ecommerce.dto.LoginResponse;
import com.example.ecommerce.model.User;
import com.example.ecommerce.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.ecommerce.dto.UserRegistrationRequest;
import com.example.ecommerce.dto.LoginRequest;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody UserRegistrationRequest request) {

        User savedUser = userService.registerUser(request);
        return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            // Validate credentials
            User user = userService.loginUser(request);

            // Generate JWT token
            String token = userService.generateTokenForUser(user.getEmail());

            // Create response with token and user info (no password)
            LoginResponse response = new LoginResponse(
                    token,
                    user.getEmail(),
                    user.getName(),
                    user.getRole().name()
            );

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // Return 401 Unauthorized for authentication failures
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
