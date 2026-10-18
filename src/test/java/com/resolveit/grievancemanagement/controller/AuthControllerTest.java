package com.resolveit.grievancemanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveit.grievancemanagement.dto.SignupRequest;
import com.resolveit.grievancemanagement.entity.User;
import com.resolveit.grievancemanagement.repository.UserRepository;
import com.resolveit.grievancemanagement.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthController authController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Public registration always assigns CITIZEN role, even if attacker submits privileged role")
    void registerUser_AlwaysEnforcesCitizenRole() throws Exception {
        // Given: Payload that attempts to inject "role": "ADMIN"
        String rawJson = """
            {
                "username": "attacker",
                "email": "attacker@example.com",
                "password": "Password@123",
                "role": "ADMIN",
                "firstName": "Attacking",
                "lastName": "User"
            }
            """;

        when(userRepository.existsByUsername("attacker")).thenReturn(false);
        when(userRepository.existsByEmail("attacker@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword123");

        // When
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rawJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));

        // Then: Capture saved user to verify role cannot be escalated to ADMIN
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole())
                .as("Public signup MUST always enforce CITIZEN role regardless of input payload")
                .isEqualTo(User.Role.CITIZEN);
        assertThat(savedUser.getUsername()).isEqualTo("attacker");
    }

    @Test
    @DisplayName("Registration rejects duplicate username with HTTP 400")
    void registerUser_DuplicateUsername_ReturnsBadRequest() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("existinguser");
        request.setEmail("newemail@example.com");
        request.setPassword("Password@123");

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: Username is already taken!"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Registration rejects duplicate email with HTTP 400")
    void registerUser_DuplicateEmail_ReturnsBadRequest() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("uniqueuser");
        request.setEmail("existing@example.com");
        request.setPassword("Password@123");

        when(userRepository.existsByUsername("uniqueuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error: Email is already in use!"));

        verify(userRepository, never()).save(any(User.class));
    }
}
