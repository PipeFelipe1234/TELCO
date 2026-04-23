# 📋 Cambios en Estructura de Roles - Sistema de Administración Multi-Tipo

## ✅ Implementado

Se ha implementado una nueva estructura de roles que permite separar la administración entre Técnicos y Coobradores, manteniendo un SUPER ADMIN que ve todo.

---

## 📊 Nueva Estructura (Rol + Cargo)

### Campo `rol` (permisos / token)

| Valor   | Descripción       |
| ------- | ----------------- |
| `ADMIN` | Administrador     |
| `USER`  | Usuario operativo |

### Campo `cargo` (segmentación)

### Roles de Usuario

| Rol        | Descripción        |
| ---------- | ------------------ |
| `USER_TEC` | Empleado Técnico   |
| `USER_COO` | Empleado Coobrador |

### Roles de Administrador

| Rol         | Descripción     | Permisos                                                                    |
| ----------- | --------------- | --------------------------------------------------------------------------- |
| `ADMIN`     | SUPER ADMIN     | Ve todos los usuarios (USER_TEC + USER_COO), recibe notificaciones de ambos |
| `ADMIN_TEC` | Admin Técnico   | Ve solo usuarios USER_TEC, recibe notificaciones de técnicos                |
| `ADMIN_COO` | Admin Coobrador | Ve solo usuarios USER_COO, recibe notificaciones de coobradores             |

---

## 🔧 Cambios Realizados

### 1. SecurityConfig.java

- ✅ `rol` se mantiene en **ADMIN/USER** para permisos y JWT
- ✅ Acceso admin protegido por `hasRole("ADMIN")`

### 2. UsuarioRepository.java

- ✅ Agregado `findAllTecnicos()` - Busca `rol=USER` y `cargo=USER_TEC`
- ✅ Agregado `findAllCoobradores()` - Busca `rol=USER` y `cargo=USER_COO`
- ✅ Agregado `findAllAdmins()` - Busca `rol=ADMIN`
- ✅ Agregado `findAllSuperAdmins()` - Busca `rol=ADMIN` y `cargo=ADMIN` (o null)
- ✅ Agregado `findAllAdminsTecnicos()` - Busca `rol=ADMIN` y `cargo=ADMIN_TEC`
- ✅ Agregado `findAllAdminsCoobradores()` - Busca `rol=ADMIN` y `cargo=ADMIN_COO`

### 3. UsuarioService.java

- ✅ Agregado `obtenerTodosTecnicos()` - DTO de técnicos con zonas
- ✅ Agregado `obtenerTodosCoobradores()` - DTO de coobradores con zonas
- ✅ Agregado `obtenerUsuariosFiltrados(rolAdmin)` - Filtra usuarios según rol del admin

**Lógica de filtrado:**

- `ADMIN` → ve todos los usuarios
- `ADMIN_TEC` → ve solo USER_TEC
- `ADMIN_COO` → ve solo USER_COO

### 4. AdminController.java

- ✅ Actualizado `obtenerUsuariosConZonas()` para filtrar según rol del admin
- ✅ Obtiene el rol del admin del contexto de seguridad
- ✅ Log: `📍 Admin con cargo {cargo} consultando usuarios`

### 5. RastreoZonaService.java

- ✅ Agregado método `obtenerAdminsParaNotificacion(usuario)` - Filtra admins según tipo de usuario
  - Si empleado es `USER_TEC` → notifica a `ADMIN` + `ADMIN_TEC`
  - Si empleado es `USER_COO` → notifica a `ADMIN` + `ADMIN_COO`
- ✅ Actualizado `enviarNotificacionFueraDeZonasAsignadas()` - Usa filtrado de admins
- ✅ Actualizado `enviarNotificacionSalioDeZona()` - Usa filtrado de admins
- ✅ Actualizado `enviarAlertaPreocupanteResidencia()` - Usa filtrado de admins
- ✅ Agregado `obtenerTodosLosRastreosFiltrados(rolAdmin)` - Filtra rastreos por admin
- ✅ Agregado `obtenerRastreosPreocupantesFiltrados(rolAdmin)` - Filtra preocupantes por admin

### 6. ZonaController.java

- ✅ Agregada importación de `SecurityContextHolder`
- ✅ Actualizado `obtenerTodosLosRastreos()` - Obtiene cargo del admin y filtra
- ✅ Actualizado `obtenerRastreosPreocupantes()` - Obtiene cargo del admin y filtra
- ✅ Log: `📊 Admin con cargo {cargo} consultando rastreo de empleados`

### 7. RastreoZonaResponse.java

- ✅ Agregado campo `empleadoRol` para filtrar rastreos por tipo de usuario
- ✅ Actualizado `fromEntity()` para incluir el rol del empleado

---

## 🎯 Flujo de Funcionamiento

### Visualización de Usuarios

```
Admin accede a: GET /api/admin/usuarios/con-zonas
↓
AdminController obtiene cargo del admin
↓
Si cargo es ADMIN_TEC → ve solo USER_TEC
Si cargo es ADMIN_COO → ve solo USER_COO
Si cargo es ADMIN o null → ve todos
```

### Visualización de Rastreos (Mapa)

```
Admin accede a: GET /api/zonas/rastreo
↓
ZonaController obtiene cargo del admin
↓
Si cargo es ADMIN_TEC → ve solo rastreos de USER_TEC
Si cargo es ADMIN_COO → ve solo rastreos de USER_COO
Si cargo es ADMIN o null → ve todos los rastreos
```

### Envío de Notificaciones

```
Empleado USER_TEC se mueve / entra en PREOCUPANTE
↓
RastreoZonaService.obtenerAdminsParaNotificacion(empleado)
↓
Retorna: [ADMIN, ADMIN_TEC]
↓
Notificación enviada solo a estos admins
```

---

## 📝 Ejemplo de Uso

### Crear un Admin Técnico

```json
POST /api/admin/usuarios

{
  "nombre": "Carlos Admin Técnico",
  "identificacion": "1234567890",
  "email": "carlos@empresa.com",
  "rol": "ADMIN",
  "cargo": "ADMIN_TEC",
  "password": "segura123"
}
```

### Crear un Usuario Técnico

```json
POST /api/admin/usuarios

{
  "nombre": "Felipe Técnico",
  "identificacion": "9876543210",
  "email": "felipe@empresa.com",
  "rol": "USER",
  "cargo": "USER_TEC",
  "password": "segura123"
}
```

### Resultado en el Dashboard

- **ADMIN**: Ve a Felix (USER_TEC) y todos los coobradores
- **ADMIN_TEC**: Ve solo a Felipe (USER_TEC)
- **ADMIN_COO**: No ve a Felipe (USER_TEC)

---

## ♻️ Compatibilidad

- ✅ Todas las notificaciones siguen funcionando igual
- ✅ Todos los endpoints de zonas funcionan igual
- ✅ El sistema es retrocompatible: usuarios existentes mantienen sus roles

---

## 🚀 Próximos Pasos Opcionales

1. **Crear Relación Admin-Usuario**: Vincular jefe directo con sus empleados
2. **Dashboard por Tipo**: Personalizar vistas según admin
3. **Reportes Separados**: Exportar datos solo de sus usuarios
4. **Asignación de Personal**: Admin_TEC asigna técnicos a zonas
