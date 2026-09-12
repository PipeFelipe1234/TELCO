package com.practica.backend.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.SendResponse;
import com.practica.backend.entity.TokenDispositivo;
import com.practica.backend.entity.Usuario;
import com.practica.backend.repository.TokenDispositivoRepository;
import com.practica.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class NotificacionService {

    private static final Logger logger = LoggerFactory.getLogger(NotificacionService.class);

    private final TokenDispositivoRepository tokenDispositivoRepository;
    private final UsuarioRepository usuarioRepository;

    public NotificacionService(TokenDispositivoRepository tokenDispositivoRepository,
            UsuarioRepository usuarioRepository) {
        this.tokenDispositivoRepository = tokenDispositivoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * 📲 Envía notificación a un dispositivo específico
     */
    public void enviarNotificacionADispositivo(
            String token,
            String titulo,
            String mensaje,
            Map<String, String> datos) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .putAllData(datos)
                    .setNotification(
                            com.google.firebase.messaging.Notification.builder()
                                    .setTitle(titulo)
                                    .setBody(mensaje)
                                    .build())
                    .build();

            String messageId = FirebaseMessaging.getInstance().send(message);
            logger.debug("✅ Notificación enviada correctamente: {}", messageId);
        } catch (Exception e) {
            logger.error("❌ Error al enviar notificación: {}", e.getMessage());
        }
    }

    /**
     * 📲 Envía notificación a múltiples dispositivos (a un usuario específico)
     */
    public void enviarNotificacionAUsuario(
            Usuario usuario,
            String titulo,
            String mensaje,
            Map<String, String> datos) {
        List<TokenDispositivo> tokens = tokenDispositivoRepository.findTokensActivosByUsuario(usuario);

        if (tokens.isEmpty()) {
            logger.debug("⚠️ El usuario {} no tiene dispositivos registrados", usuario.getNombre());
            return;
        }

        List<String> tokenList = tokens.stream()
                .map(TokenDispositivo::getToken)
                .toList();

        enviarNotificacionAMultiplesDispositivos(tokenList, titulo, mensaje, datos);
    }

    /**
     * 📲 Envía notificación a los admins que corresponden según el cargo Y ciudades
     * del
     * empleado.
     * - USER_TEC → ADMIN (super) + ADMIN_TEC con la ciudad del empleado
     * - USER_COO → ADMIN (super) + ADMIN_COO con la ciudad del empleado
     * - Cualquier otro → todos los admins
     */
    public void enviarNotificacionFiltradaPorCargo(
            String cargoEmpleado,
            List<String> ciudadesEmpleado,
            String titulo,
            String mensaje,
            Map<String, String> datos) {

        List<Usuario> admins;
        if ("USER_TEC".equals(cargoEmpleado)) {
            List<Usuario> adminsTec = usuarioRepository.findAllAdminsTecnicos().stream()
                    .filter(a -> adminCubreEmpleado(a, ciudadesEmpleado))
                    .toList();
            admins = Stream.concat(
                    usuarioRepository.findAllSuperAdmins().stream(),
                    adminsTec.stream()).toList();
        } else if ("USER_COO".equals(cargoEmpleado)) {
            List<Usuario> adminsCoo = usuarioRepository.findAllAdminsCoobradores().stream()
                    .filter(a -> adminCubreEmpleado(a, ciudadesEmpleado))
                    .toList();
            admins = Stream.concat(
                    usuarioRepository.findAllSuperAdmins().stream(),
                    adminsCoo.stream()).toList();
        } else {
            admins = usuarioRepository.findAllAdmins();
        }

        List<String> tokenList = admins.stream()
                .flatMap(a -> tokenDispositivoRepository.findTokensActivosByUsuario(a).stream())
                .map(TokenDispositivo::getToken)
                .toList();

        if (tokenList.isEmpty()) {
            logger.debug("⚠️ No hay admins con dispositivos registrados para cargo: {}", cargoEmpleado);
            return;
        }

        enviarNotificacionAMultiplesDispositivos(tokenList, titulo, mensaje, datos);
    }

    /**
     * 📲 Envía notificación a todos los ADMIN
     */
    public void enviarNotificacionAAdmins(
            String titulo,
            String mensaje,
            Map<String, String> datos) {
        List<TokenDispositivo> tokensAdmins = tokenDispositivoRepository.findTokensActivosAdmins();

        if (tokensAdmins.isEmpty()) {
            logger.debug("⚠️ No hay ADMINs con dispositivos registrados");
            return;
        }

        List<String> tokenList = tokensAdmins.stream()
                .map(TokenDispositivo::getToken)
                .toList();

        enviarNotificacionAMultiplesDispositivos(tokenList, titulo, mensaje, datos);
    }

    /**
     * 📲 Envía notificación a múltiples dispositivos (usando sendEach - API v1)
     */
    private void enviarNotificacionAMultiplesDispositivos(
            List<String> tokens,
            String titulo,
            String mensaje,
            Map<String, String> datos) {
        try {
            if (tokens.isEmpty()) {
                logger.debug("⚠️ Lista de tokens vacía, no se envían notificaciones");
                return;
            }

            // Crear lista de mensajes individuales (API HTTP v1)
            List<Message> messages = new ArrayList<>();
            for (String token : tokens) {
                Message msg = Message.builder()
                        .setToken(token)
                        .putAllData(datos)
                        .setNotification(
                                com.google.firebase.messaging.Notification.builder()
                                        .setTitle(titulo)
                                        .setBody(mensaje)
                                        .build())
                        .build();
                messages.add(msg);
            }

            // Usar sendEach en lugar de sendMulticast (API v1)
            BatchResponse response = FirebaseMessaging.getInstance().sendEach(messages);

            logger.debug("✅ Notificaciones enviadas: {} exitosas, {} fallidas",
                    response.getSuccessCount(), response.getFailureCount());

            // Procesar tokens fallidos
            if (response.getFailureCount() > 0) {
                procesarTokensFallidos(response, tokens);
            }
        } catch (Exception e) {
            logger.error("❌ Error al enviar notificaciones: {}", e.getMessage());
        }
    }

    /**
     * 🗑️ Procesa tokens fallidos y los marca como inactivos
     */
    private void procesarTokensFallidos(BatchResponse response, List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            SendResponse sendResponse = response.getResponses().get(i);
            if (!sendResponse.isSuccessful()) {
                String token = tokens.get(i);

                String errorCode = "UNKNOWN";
                if (sendResponse.getException() != null
                        && sendResponse.getException().getMessagingErrorCode() != null) {
                    errorCode = sendResponse.getException().getMessagingErrorCode().name();
                }

                final String finalErrorCode = errorCode;
                if ("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
                    tokenDispositivoRepository.findByToken(token).ifPresent(td -> {
                        td.setActivo(false);
                        tokenDispositivoRepository.save(td);
                        logger.warn("🗑️ Token inactivado por error: {}", finalErrorCode);
                    });
                } else {
                    logger.debug("⚠️ Token NO inactivado (error posiblemente temporal): {}", errorCode);
                }
            }
        }
    }

    /**
     * ✅ Registra un nuevo token de dispositivo
     */
    public void registrarTokenDispositivo(Usuario usuario, String token, String tipoDispositivo,
            String marca, String modelo) {
        try {
            if (token == null || token.isEmpty()) {
                logger.warn("❌ El token FCM está vacío o es null para usuario {}", usuario.getNombre());
                return;
            }

            // Verificar si el token ya existe
            var existente = tokenDispositivoRepository.findByToken(token);
            if (existente.isPresent()) {
                TokenDispositivo td = existente.get();
                td.setUltimaActividad(java.time.LocalDateTime.now());
                td.setActivo(true);
                tokenDispositivoRepository.save(td);
                logger.debug("ℹ️ Token ya existía para {}, actualizada última actividad", usuario.getNombre());
                return;
            }

            TokenDispositivo nuevoToken = new TokenDispositivo(usuario, token, tipoDispositivo, marca, modelo);
            tokenDispositivoRepository.save(nuevoToken);
            logger.info("✅ Token FCM registrado exitosamente para: {}", usuario.getNombre());
        } catch (Exception e) {
            logger.error("❌ Error al registrar token: {}", e.getMessage());
        }
    }

    /**
     * ❌ Desactiva un token
     */
    public void desactivarToken(String token) {
        tokenDispositivoRepository.findByToken(token).ifPresent(td -> {
            td.setActivo(false);
            tokenDispositivoRepository.save(td);
            logger.info("✅ Token desactivado correctamente");
        });
    }

    /**
     * 📲 Envía notificación a TODOS los usuarios (ADMINs y USERs)
     */
    public void enviarNotificacionATodos(String titulo, String mensaje) {
        List<TokenDispositivo> todosLosTokens = tokenDispositivoRepository.findByActivoTrue();

        if (todosLosTokens.isEmpty()) {
            logger.debug("⚠️ No hay dispositivos registrados para enviar notificación a todos");
            return;
        }

        List<String> tokenList = todosLosTokens.stream()
                .map(TokenDispositivo::getToken)
                .toList();

        Map<String, String> datos = Map.of(
                "tipo", "advertencia_limpieza",
                "titulo", titulo,
                "mensaje", mensaje);

        enviarNotificacionAMultiplesDispositivos(tokenList, titulo, mensaje, datos);
    }

    /**
     * Determina si un admin cubre al empleado según las ciudades asignadas al
     * admin.
     * Si el admin no tiene ciudades asignadas, cubre a todos los empleados de su
     * cargo.
     */
    private boolean adminCubreEmpleado(Usuario admin, List<String> ciudadesEmpleado) {
        List<String> ciudadesAdmin = admin.getCiudades();
        if (ciudadesAdmin == null || ciudadesAdmin.isEmpty()) {
            return true; // Admin sin ciudades ve todo
        }
        if (ciudadesEmpleado == null || ciudadesEmpleado.isEmpty()) {
            return false; // Empleado sin ciudad no es cubierto por admin con ciudad
        }
        return ciudadesAdmin.stream().anyMatch(ciudadesEmpleado::contains);
    }
}
