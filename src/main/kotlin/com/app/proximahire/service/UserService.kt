package com.app.proximahire.service

import com.app.proximahire.entity.Role
import com.app.proximahire.entity.User
import com.app.proximahire.repository.UserRepository
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${jwt.secret}") private val jwtSecret: String
) {

    private val jwtEncoder: JwtEncoder by lazy {
        val secretKey = jwtSecret.toByteArray()
        val immutableSecret = ImmutableSecret<com.nimbusds.jose.proc.SecurityContext>(secretKey)
        NimbusJwtEncoder(immutableSecret)
    }

    fun registerUser(email: String, password: String, role: Role): User {
        if (userRepository.findByEmail(email) != null) {
            throw IllegalArgumentException("User with email $email already exists")
        }

        val encodedPassword = passwordEncoder.encode(password)
        val user = User(
            email = email,
            password = encodedPassword,
            role = role
        )
        return userRepository.save(user)
    }

    fun loginUser(email: String, password: String): String {
        val user = userRepository.findByEmail(email) 
            ?: throw IllegalArgumentException("Invalid email or password")

        if (!passwordEncoder.matches(password, user.password)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("proximahire")
            .issuedAt(now)
            .expiresAt(now.plus(24, ChronoUnit.HOURS))
            .subject(user.id.toString())
            .claim("id", user.id.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .build()

        val jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).tokenValue
    }
}
