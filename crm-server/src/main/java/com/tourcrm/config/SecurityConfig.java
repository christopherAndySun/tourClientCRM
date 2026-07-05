package com.tourcrm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ApiAuthenticationFilter apiAuthenticationFilter;
    private final ApiCsrfProtectionFilter apiCsrfProtectionFilter;

    public SecurityConfig(ApiAuthenticationFilter apiAuthenticationFilter, ApiCsrfProtectionFilter apiCsrfProtectionFilter) {
        this.apiAuthenticationFilter = apiAuthenticationFilter;
        this.apiCsrfProtectionFilter = apiCsrfProtectionFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/health/**", "/uploads/**", "/ws/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(apiCsrfProtectionFilter, ApiAuthenticationFilter.class)
                .addFilterBefore(apiAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
