package com.practica.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

public class JwtFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                // Validar que el token no esté expirado primero
                String identificacion = JwtUtil.extraerIdentificacion(token);
                String rol = JwtUtil.extraerRol(token);

                if (identificacion != null) {
                    // Crear lista de autoridades
                    List<SimpleGrantedAuthority> autoridades = new ArrayList<>();
                    if (rol != null && !rol.isEmpty()) {
                        autoridades.add(new SimpleGrantedAuthority("ROLE_" + rol));
                    }

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            identificacion,
                            null,
                            autoridades);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ExpiredJwtException e) {
                System.out.println("⛔ Token expirado para request: " + request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Token expirado\"}");
                return; // No continuar el filtro si el token está expirado
            } catch (JwtException e) {
                System.out.println(
                        "⛔ Token JWT inválido: " + e.getMessage() + " para request: " + request.getRequestURI());
            } catch (Exception e) {
                System.out.println(
                        "⛔ Error procesando token: " + e.getMessage() + " para request: " + request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}
