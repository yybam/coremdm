package com.core.mdm.admin.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(private val firebaseTokenFilter: FirebaseTokenFilter) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Public — no Firebase token required
                    .requestMatchers("/actuator/health", "/api/health").permitAll()
                    .requestMatchers("/api/v1/mdm/enroll").permitAll()
                    // Device inventory — ORGANIZATION_ADMIN may manage their own tenant's devices
                    .requestMatchers("/api/admin/devices/**").hasAnyRole("SUPER_ADMIN", "ORGANIZATION_ADMIN")
                    // All other admin routes remain SUPER_ADMIN only
                    .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                    .anyRequest().authenticated()
            }
            .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun corsSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("https://coremdm.web.app", "http://localhost:*")
            allowedMethods        = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders        = listOf("*")
            allowCredentials      = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
