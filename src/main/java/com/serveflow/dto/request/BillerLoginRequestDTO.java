package com.serveflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * BillerLoginRequestDTO — carries the biller's login credentials to the AuthController.
 *
 * This DTO is the request body for POST /api/auth/biller/login.
 * After successful validation, AuthController passes these to the AuthenticationManager.
 *
 * NEVER include role, id, or any other field here.
 * Role is determined by the database — not accepted from the client.
 */
@Data
public class BillerLoginRequestDTO {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
