package com.practica.backend.service;

import com.practica.backend.dto.LoginRequest;
import com.practica.backend.dto.LoginResponse;
import com.practica.backend.entity.Usuario;
import com.practica.backend.repository.UsuarioRepository;
import com.practica.backend.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;

    public AuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public LoginResponse login(LoginRequest request) {

        Usuario usuario = usuarioRepository.findByIdentificacion(request.identificacion())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        String token = JwtUtil.generarToken(
                usuario.getIdentificacion(),
                usuario.getRol(),
                usuario.getNombre(),
                usuario.getFoto(),
                usuario.getCargo());

        return new LoginResponse(
                token,
                usuario.getIdentificacion(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getFoto(),
                usuario.getCargo());
    }

    public LoginResponse refrescarToken(String authHeader) {
        // Validar que el header tenga el formato correcto
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Header de autorización inválido");
        }

        String token = authHeader.substring(7);

        // Extraer claims (funciona incluso si el token está expirado)
        Claims claims = JwtUtil.extraerClaimsIgnorandoExpiracion(token);
        String identificacion = claims.getSubject();
        String rol = claims.get("rol", String.class);
        String nombre = claims.get("nombre", String.class);
        String foto = claims.get("foto", String.class);
        String cargo = claims.get("cargo", String.class);

        // Verificar si el token es válido (no está expirado)
        if (JwtUtil.esTokenValido(token)) {
            // Token aún válido: devolver el mismo token con los datos
            return new LoginResponse(token, identificacion, nombre, rol, foto, cargo);
        } else {
            // Token expirado: generar uno nuevo
            String nuevoToken = JwtUtil.generarToken(identificacion, rol, nombre, foto, cargo);
            return new LoginResponse(nuevoToken, identificacion, nombre, rol, foto, cargo);
        }
    }
}
