# Funcionalidad de Descanso / Almuerzo - Guía para Frontend (Web + Flutter)

Estado: **implementado en backend** (rama de trabajo, pendiente de deploy).
Base URL: la misma de siempre (`https://telco-production.up.railway.app`).
Todos los endpoints requieren `Authorization: Bearer <token>` (mismo JWT del login).

---

## 1. Resumen del flujo

```
SIN_INICIAR --(POST /api/registros/entrada)------------> EN_TURNO
EN_TURNO    --(POST /api/registrar-descanso/iniciar)---> EN_DESCANSO
EN_DESCANSO --(POST /api/registrar-descanso/finalizar)-> EN_TURNO
EN_TURNO    --(POST /api/registros/salida)-------------> FINALIZADO
```

Reglas que aplica el backend:

- Solo se puede iniciar un descanso si el usuario tiene un turno en curso (entrada sin salida).
- No se puede iniciar un descanso si ya tiene uno abierto.
- Solo se puede finalizar si hay un descanso abierto.
- Se permiten varios descansos en el mismo turno (cada uno se guarda aparte).
- Si el usuario registra salida (`/api/registros/salida`) con un descanso abierto, el backend **lo cierra automáticamente** (`horaFin = hora de salida`, `cerradoAutomaticamente = true`). Lo mismo pasa con el cierre automático de turnos de las 23:55.
- El colaborador solo maneja su propio descanso (el usuario sale del token). El administrador solo consulta.

---

## 2. Conteo de horas trabajadas (IMPORTANTE)

El tiempo de descanso **no cuenta como tiempo trabajado**:

```
minutosTrabajados = (salida - entrada) - minutos de descansos
```

- **Mientras hay un descanso abierto**, el conteo de `minutosTrabajados` / `horasTrabajadas` del turno en curso se detiene. Al finalizar el descanso vuelve a avanzar.
- Al registrar la salida, `horasTrabajadas` y `minutosTrabajados` quedan guardados **ya descontando** los descansos. Esto aplica también a los reportes, dashboard y exportaciones (PDF/Excel/Word), que usan esos campos.
- Si el front calcula el tiempo trabajado en vivo con un reloj propio, debe **pausarlo** mientras `isInBreak = true` (o mientras exista un descanso en `/actual`) o, mejor, usar `minutosTrabajados` que devuelve el backend.

Cambio en la respuesta de registros (`RegistroResponse`, usada en `/api/registros/mis-registros`, `/api/registros/{id}`, `/api/admin/registros`, `/api/admin/registros/filtrar`): se agrega un campo nuevo.

| Campo             | Tipo | Descripción                                                                                  |
| ----------------- | ---- | -------------------------------------------------------------------------------------------- |
| `minutosDescanso` | int  | Total de minutos de descanso del turno (si hay uno abierto, cuenta hasta el momento actual). |

`horasTrabajadas` y `minutosTrabajados` ya vienen descontando los descansos. Los demás campos no cambian.

---

## 3. Endpoints del colaborador (App Flutter)

### 3.1 Iniciar descanso

```
POST /api/registrar-descanso/iniciar
Content-Type: application/json
```

Body (todo opcional, se puede enviar `{}` o no enviar body):

```json
{
  "latitud": 1.2136,
  "longitud": -77.2811,
  "fechaCreacion": "2026-10-09T12:05:00.000-05:00"
}
```

| Campo                 | Tipo                       | Descripción                                                                                                                                             |
| --------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `latitud`, `longitud` | number (opcional)          | Ubicación al iniciar.                                                                                                                                   |
| `fechaCreacion`       | string ISO 8601 (opcional) | Momento real de la acción. Útil si se sincroniza sin conexión (igual que en entrada/salida). Si no se envía, el backend usa la hora actual de Colombia. |

Respuesta `200 OK`:

```json
{
  "descansoId": 55,
  "horaInicio": "2026-10-09T12:05:00-05:00"
}
```

Errores:

| Código | Cuándo                                         | Body                                                                              |
| ------ | ---------------------------------------------- | --------------------------------------------------------------------------------- |
| `409`  | No hay turno en curso                          | `{"status":409,"mensaje":"No tienes un turno en curso para iniciar el descanso"}` |
| `409`  | Ya hay un descanso abierto (incluye doble tap) | `{"status":409,"mensaje":"Ya tienes un descanso en curso"}`                       |
| `404`  | Usuario del token no existe                    | `{"status":404,"mensaje":"Usuario no encontrado"}`                                |

> Ante un `409` por "ya tienes un descanso en curso", la app puede llamar a `/actual` para restaurar el cronómetro.

### 3.2 Finalizar descanso

```
POST /api/registrar-descanso/finalizar
Content-Type: application/json
```

Body: igual que en iniciar (todo opcional).

Respuesta `200 OK`:

```json
{
  "descansoId": 55,
  "horaInicio": "2026-10-09T12:05:00-05:00",
  "horaFin": "2026-10-09T13:40:00-05:00",
  "minutosDuracion": 95,
  "excedioTiempoLimite": false
}
```

`excedioTiempoLimite` es `true` si el descanso duró más que el límite del usuario (por defecto 120 minutos).

Errores:

| Código | Cuándo                  | Body                                                        |
| ------ | ----------------------- | ----------------------------------------------------------- |
| `409`  | No hay descanso abierto | `{"status":409,"mensaje":"No tienes un descanso en curso"}` |
| `404`  | Usuario no existe       | `{"status":404,"mensaje":"Usuario no encontrado"}`          |

### 3.3 Descanso actual (restaurar estado al abrir la app)

```
GET /api/registrar-descanso/actual
```

- Si **hay** descanso abierto: `200 OK`

```json
{
  "descansoId": 55,
  "registroId": 130,
  "horaInicio": "2026-10-09T12:05:00-05:00",
  "minutosTranscurridos": 42,
  "limiteMinutos": 120,
  "excedioTiempoLimite": false
}
```

- Si **no hay** descanso abierto (o no hay turno en curso): `204 No Content`, sin body.

Con `horaInicio` y `limiteMinutos` la app puede armar el cronómetro y el estado de alerta sin más llamadas.

### 3.4 Registrar token de notificaciones (ya existía)

El prerrequisito del push **ya existe**, no hay endpoint nuevo. Se usa el actual:

```
POST /api/notificaciones/registrar-token
Authorization: Bearer <token>
```

```json
{
  "token": "<fcm_token>",
  "tipoDispositivo": "android",
  "marca": "Samsung",
  "modelo": "A54"
}
```

Respuesta: `{"mensaje":"✅ Token registrado correctamente","exito":true}`

(La ruta `/api/dispositivo/token` del documento de propuesta **no existe**; usar la de arriba.)

---

## 4. Notificación push por exceso de tiempo

Un proceso en el backend revisa cada minuto los descansos abiertos. Cuando uno supera el límite del usuario, envía **una sola vez** un push al colaborador:

- **Título:** `Descanso de almuerzo excedido`
- **Mensaje:** `Ya llevas más de 2 horas en tu descanso de almuerzo. Recuerda finalizarlo.` (el texto usa el límite real: `2 horas`, `1 hora`, `90 minutos`, etc.)
- **Data (payload):**

```json
{
  "tipo": "DESCANSO_EXCEDIDO",
  "descansoId": "55",
  "registroId": "130"
}
```

Todos los valores de `data` llegan como string. A partir de ese momento el descanso queda marcado `excedioTiempoLimite = true` (y `breakExceeded = true` en asistencia).

---

## 5. Límite de tiempo configurable por usuario

Nuevo campo en el usuario: `tiempoLimiteAlmuerzoMinutos` (entero, opcional).

- Si es `null`, el límite es **120 minutos** (2 horas).
- Aparece en las respuestas de usuario (`/api/usuarios/mis-datos`, `/api/admin/usuarios`, `/api/admin/usuarios/{id}`, `/api/admin/usuarios/con-zonas`...) junto a `tiempoLimiteMinutos`.
- Se envía igual que `tiempoLimiteMinutos`:
  - Crear/actualizar usuario (`POST /api/usuarios`, `PUT /api/usuarios/{id}`, `PUT /api/usuarios/me`): el campo `tiempoLimiteAlmuerzoMinutos` se agrega al body.
  - Edición parcial de admin (`PUT /api/admin/usuarios/{id}`): solo se actualiza si viene en el body.

> En los `PUT /api/usuarios/{id}` y `PUT /api/usuarios/me` (que reemplazan todos los campos) hay que reenviar el valor actual para no borrarlo (queda en `null` si se omite).

---

## 6. Cambios en `GET /api/asistencia`

Cada elemento de `detalles.enTurno` y `detalles.finalizados` trae 4 campos nuevos:

| Campo                  | Tipo              | Descripción                                                       |
| ---------------------- | ----------------- | ----------------------------------------------------------------- |
| `isInBreak`            | boolean           | `true` si tiene un descanso abierto ahora mismo.                  |
| `breakStartTime`       | string ISO / null | `horaInicio` del descanso abierto. `null` si `isInBreak = false`. |
| `breakExceeded`        | boolean           | `true` si el descanso abierto ya superó el límite.                |
| `cantidadDescansosHoy` | int               | Total de descansos (abiertos o cerrados) del turno de hoy.        |

En `finalizados`, `isInBreak` y `breakExceeded` siempre son `false` y `breakStartTime` es `null`; `cantidadDescansosHoy` indica cuántos descansos tuvo. `noIniciados` no cambia.

Ejemplo (`enTurno`):

```json
{
  "registroId": 130,
  "identificacion": "1111111111",
  "nombre": "Carlos Rodríguez",
  "cargo": "USER_TEC",
  "horaEntrada": "08:30:00",
  "ciudad": "PASTO",
  "picture": "https://res.cloudinary.com/.../foto.jpg",
  "cantidadReportes": 3,
  "isInBreak": true,
  "breakStartTime": "2026-10-09T12:05:00-05:00",
  "breakExceeded": false,
  "cantidadDescansosHoy": 1
}
```

El tiempo transcurrido de descanso se calcula en el cliente: `ahora - breakStartTime`. El filtrado por cargo y ciudades del administrador sigue igual.

---

## 7. Histórico de descansos (Admin Web)

```
GET /api/admin/descansos
```

Solo para usuarios con rol `ADMIN`. Respeta la visibilidad del administrador, igual que asistencia:

- `ADMIN` (cargo `ADMIN`): ve todo y puede filtrar por `tipoUsuario`.
- `ADMIN_TEC`: solo `USER_TEC` de sus ciudades.
- `ADMIN_COO`: solo `USER_COO` de sus ciudades.

Query params (todos opcionales):

| Parámetro                  | Tipo                      | Descripción                                                            |
| -------------------------- | ------------------------- | ---------------------------------------------------------------------- |
| `identificacion`           | string                    | Cédula exacta del colaborador.                                         |
| `tipoUsuario`              | `USER_TEC` \| `USER_COO`  | Solo lo aplica el super `ADMIN`; los demás administradores lo ignoran. |
| `fechaDesde`, `fechaHasta` | `YYYY-MM-DD`              | Rango (ambos incluidos) según la fecha de inicio del descanso.         |
| `soloExcedidos`            | boolean (default `false`) | Solo descansos que excedieron el límite.                               |
| `page`                     | int (default `0`)         | Página, base 0.                                                        |
| `size`                     | int (default `20`)        | Tamaño de página.                                                      |

Respuesta `200 OK` (paginada estilo Spring, orden: más recientes primero):

```json
{
  "content": [
    {
      "descansoId": 55,
      "registroId": 130,
      "identificacion": "1111111111",
      "nombre": "Carlos Rodríguez",
      "cargo": "USER_TEC",
      "fecha": "2026-10-09",
      "horaInicio": "2026-10-09T12:05:00-05:00",
      "horaFin": "2026-10-09T13:40:00-05:00",
      "minutosDuracion": 95,
      "enCurso": false,
      "excedioTiempoLimite": false,
      "cerradoAutomaticamente": false
    }
  ],
  "pageable": { "pageNumber": 0, "pageSize": 20 },
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20,
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

Notas:

- Si el descanso sigue abierto: `horaFin = null`, `enCurso = true` y `minutosDuracion` son los minutos transcurridos hasta ese momento.
- `cerradoAutomaticamente = true` indica que el usuario no finalizó el descanso y se cerró al registrar la salida (o por el cierre automático de 23:55).
- Para el detalle de un registro (`/dashboard/registros/[id]`) se puede llamar con `identificacion` y el rango de fechas del día y filtrar por `registroId`.

---

## 8. Formato de fechas y zona horaria

- Todas las fechas/horas de descanso se devuelven en ISO 8601 con offset de Colombia: `2026-10-09T12:05:00-05:00`.
- Para `fechaCreacion` (entrada del front) se acepta ISO con offset (`...-05:00`, `Z`) o sin offset (se asume hora de Colombia).
- `fecha` en el histórico es `YYYY-MM-DD`.

---

## 9. Formato de errores del módulo

Los errores de negocio de descanso devuelven:

```json
{ "status": 409, "mensaje": "Texto legible para mostrar al usuario" }
```

Se puede mostrar `mensaje` directamente en un snackbar/diálogo.

---

## 10. Resumen de endpoints

| Método | Ruta                                                       | Quién               | Descripción                                                                    |
| ------ | ---------------------------------------------------------- | ------------------- | ------------------------------------------------------------------------------ |
| POST   | `/api/registrar-descanso/iniciar`                          | App (colaborador)   | Abre un descanso.                                                              |
| POST   | `/api/registrar-descanso/finalizar`                        | App (colaborador)   | Cierra el descanso abierto.                                                    |
| GET    | `/api/registrar-descanso/actual`                           | App (colaborador)   | Descanso abierto (`200`) o `204` si no hay.                                    |
| GET    | `/api/admin/descansos`                                     | Web/Flutter (admin) | Histórico paginado con filtros.                                                |
| POST   | `/api/notificaciones/registrar-token`                      | App                 | Ya existía; registra el token FCM.                                             |
| GET    | `/api/asistencia` _(modificado)_                           | Web/Flutter (admin) | Agrega `isInBreak`, `breakStartTime`, `breakExceeded`, `cantidadDescansosHoy`. |
| GET    | `/api/registros/*`, `/api/admin/registros*` _(modificado)_ | Web/App             | Agregan `minutosDescanso`; las horas trabajadas ya descuentan descansos.       |
| \*     | Usuarios _(modificado)_                                    | Web                 | Nuevo campo `tiempoLimiteAlmuerzoMinutos`.                                     |

---

## 11. Recomendaciones de UI (App Flutter)

1. Al abrir la app/pantalla de turno: llamar `GET /api/registrar-descanso/actual`.
   - `200` → estado `EN_DESCANSO`: mostrar botón **Finalizar almuerzo** + cronómetro desde `horaInicio` (alerta si `minutosTranscurridos > limiteMinutos` o `excedioTiempoLimite`).
   - `204` → estado `EN_TURNO`: mostrar **Iniciar almuerzo** junto a **Registrar salida**.
2. Mientras `EN_DESCANSO`, pausar cualquier contador local de "tiempo trabajado".
3. Si el usuario registra salida en descanso, el backend cierra el descanso solo; conviene mostrar la advertencia: "Tu almuerzo se cerrará automáticamente al registrar salida".
4. Manejar el push con `data.tipo == "DESCANSO_EXCEDIDO"` (diálogo/snackbar si la app está abierta).

## 12. Recomendaciones de UI (Web Admin)

1. En `/dashboard/registros`, bloque "En turno": si `isInBreak` mostrar el ícono de comida en el avatar; si `breakExceeded` usar color de alerta.
2. Tooltip/chip: `"En almuerzo desde las {breakStartTime} · {ahora - breakStartTime}"`, recalculado en el cliente cada minuto.
3. Filtro "Solo en almuerzo": filtrar en cliente por `isInBreak == true` sobre `detalles.enTurno`.
4. Historial en el detalle del registro / analíticas: `GET /api/admin/descansos`.
5. Edición de usuario: nuevo input `tiempoLimiteAlmuerzoMinutos` (vacío = 120 min por defecto).
