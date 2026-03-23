package com.practica.backend.config;

import com.practica.backend.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // Deshabilitar CSRF (API REST)
                .csrf(csrf -> csrf.disable())

                // Configuración de autorización
                .authorizeHttpRequests(auth -> auth

                        // PUBLICOS
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios").permitAll()

                        // USUARIOS AUTENTICADOS
                        .requestMatchers(HttpMethod.PUT, "/api/usuarios/**").authenticated()

                        // ZONAS - Lectura para todos los autenticados
                        .requestMatchers(HttpMethod.GET, "/api/zonas").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/zonas/{id}").authenticated()

                        // ZONAS - Administración solo para ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/zonas/todas").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/zonas/rastreo/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/zonas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/zonas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/zonas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/zonas/**").hasRole("ADMIN")

                        // ADMIN
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        .anyRequest().authenticated())

                // Filtro JWT antes del login de Spring Security
                .addFilterBefore(
                        new JwtFilter(),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
