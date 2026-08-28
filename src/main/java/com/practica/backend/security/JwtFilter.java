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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

public class JwtFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        // Log útil para sincronización offline y endpoints sensibles
        if (requestURI.startsWith("/api/registros") || requestURI.startsWith("/api/ubicacion")) {
            logger.info("🔐 [JwtFilter] {} {} authHeaderPresent={}", method, requestURI, authHeader != null);
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                String identificacion = JwtUtil.extraerIdentificacion(token);
                String rol = JwtUtil.extraerRol(token);

                if (identificacion != null) {
                    List<SimpleGrantedAuthority> autoridades = new ArrayList<>();
                    if (rol != null && !rol.isEmpty()) {
                        autoridades.add(new SimpleGrantedAuthority("ROLE_" + rol));
                    }

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            identificacion,
                            null,
                            autoridades);

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    if (requestURI.startsWith("/api/registros") || requestURI.startsWith("/api/ubicacion")) {
                        logger.info("✅ [JwtFilter] Usuario autenticado: {} rol={} endpoint={}", identificacion, rol,
                                requestURI);
                    }
                } else {
                    logger.warn("⚠️ [JwtFilter] Token sin identificación para: {}", requestURI);
                }
            } catch (ExpiredJwtException e) {
                logger.warn("⛔ [JwtFilter] Token EXPIRADO para: {}", requestURI);
            } catch (JwtException e) {
                logger.warn("⛔ [JwtFilter] Token inválido para {}: {}", requestURI, e.getMessage());
            } catch (Exception e) {
                logger.error("⛔ [JwtFilter] Error en validación JWT para {}: {}", requestURI, e.getMessage());
            }
        } else if (requestURI.startsWith("/api/registros") || requestURI.startsWith("/api/ubicacion")) {
            logger.warn("⚠️ [JwtFilter] Sin header Authorization para: {}", requestURI);
        }

        filterChain.doFilter(request, response);
    }
}
