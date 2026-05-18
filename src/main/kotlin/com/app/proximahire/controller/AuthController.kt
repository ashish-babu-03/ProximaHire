package com.app.proximahire.controller

import com.app.proximahire.entity.Role
import com.app.proximahire.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RegisterRequest(
    val email: String,
    val password: String,
    val role: Role
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String?,
    val message: String
)

@RestController
@RequestMapping("/api/auth")
class AuthController(private val userService: UserService) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        userService.registerUser(request.email, request.password, request.role)
        return ResponseEntity.ok(AuthResponse(token = null, message = "User registered successfully"))
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val token = userService.loginUser(request.email, request.password)
        return ResponseEntity.ok(AuthResponse(token = token, message = "Login successful"))
    }
}
