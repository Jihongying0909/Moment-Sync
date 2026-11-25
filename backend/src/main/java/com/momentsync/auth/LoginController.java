package com.momentsync.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.momentsync.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LoginController {
    private final LoginService login;
    private final CurrentUserProvider currentUser;

    LoginController(LoginService login, CurrentUserProvider currentUser) {
        this.login = login;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/auth/login")
    ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(login.login(request.usernameText(), request.passwordText()));
    }

    @PostMapping("/api/admin/auth/login")
    ApiResponse<LoginResponse> adminLogin(@RequestBody LoginRequest request) {
        return ApiResponse.success(login.adminLogin(request.usernameText(), request.passwordText()));
    }

    @GetMapping("/api/users/me")
    ApiResponse<AccountResponse> currentUser() {
        return ApiResponse.success(login.currentUser(currentUser.requireUserId()));
    }

    record LoginRequest(JsonNode username, JsonNode password) {
        String usernameText() {
            return text(username);
        }

        String passwordText() {
            return text(password);
        }

        private static String text(JsonNode value) {
            return value == null ? null : value.textValue();
        }
    }
}
