package com.osporo.engine.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class OsporoSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf( csrf -> csrf.disable() )
                .sessionManagement( session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .formLogin( formLogin -> formLogin.disable() )
                .httpBasic( httpBasic -> httpBasic.disable() )

                .anonymous( anonymousUser -> anonymousUser.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/webhooks/stripe/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/listings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/listings/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/listings/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/categories/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/users/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/users/{id}/listings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/ping").permitAll()

                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }
}
