# 📊 Documentación Endpoint: GET /api/reportes

## 🎯 Objetivo

Obtener todos los reportes realizados por técnicos y cobradores con filtros y paginación. Esta API está optimizada para mostrar una vista de reportes organizados con fines de análisis y toma de decisiones.

---

## 📋 URL del Endpoint

```
GET /api/reportes
```

**Base URL:** `https://tu-backend.com/api/reportes`

---

## 🔍 Parámetros de Query (Filtros)

Todos los parámetros son **opcionales**. Puedes combinar varios filtros.

| Parámetro | Tipo | Descripción | Ejemplo |
|-----------|------|-------------|---------|
| `ccCliente` | String | Cédula del cliente visitado (búsqueda parcial) | `?ccCliente=1098765432` |
| `nombreCliente` | String | Nombre del cliente visitado (búsqueda parcial) | `?nombreCliente=Juan` |
| `tipoUsuario` | String | Tipo de usuario: `USER_TEC` o `USER_COO` | `?tipoUsuario=USER_COO` |
| `estadoVisita` | String | Estado de la visita (ver valores válidos abajo) | `?estadoVisita=PAGO_COMPLETO` |
| `novedadId` | Long | ID de la novedad/ticket | `?novedadId=1234` |
| `fechaDesde` | Date | Fecha inicio del rango (formato: `yyyy-MM-dd`) | `?fechaDesde=2026-09-20` |
| `fechaHasta` | Date | Fecha fin del rango (formato: `yyyy-MM-dd`) | `?fechaHasta=2026-09-23` |
| `page` | int | Número de página (0-indexed, empieza en 0) | `?page=0` |
| `size` | int | Cantidad de registros por página (máximo 100) | `?size=20` |

### **Valores Válidos para `estadoVisita`** (Solo para cobradores)

```
PAGO_COMPLETO
PAGO_PARCIAL
NO_PAGO
PROMETE_PAGAR_DESPUÉS
PROMETE_PAGAR_CUANDO_REPAREN
USUARIO_NO_ESTA
NO_CONTESTA_LLAMADA
USUARIO_ENOJADO
AMENAZA_RETIRARSE
```

---

## 📝 Ejemplos de Requests

### Ejemplo 1: Obtener todos los reportes (primera página, 20 registros)

```bash
GET /api/reportes?page=0&size=20
```

### Ejemplo 2: Reportes de cobradores que pagaron completo

```bash
GET /api/reportes?tipoUsuario=USER_COO&estadoVisita=PAGO_COMPLETO&page=0&size=20
```

### Ejemplo 3: Reportes de un cliente específico por CC

```bash
GET /api/reportes?ccCliente=1098765432&page=0&size=20
```

### Ejemplo 4: Reportes de un cliente por nombre (búsqueda parcial)

```bash
GET /api/reportes?nombreCliente=Juan&page=0&size=20
```

### Ejemplo 5: Reportes de técnicos en un rango de fechas

```bash
GET /api/reportes?tipoUsuario=USER_TEC&fechaDesde=2026-09-20&fechaHasta=2026-09-23&page=0&size=20
```

### Ejemplo 6: Reportes sin pagar de cobradores (últimos 7 días)

```bash
GET /api/reportes?tipoUsuario=USER_COO&estadoVisita=NO_PAGO&fechaDesde=2026-09-16&fechaHasta=2026-09-23&page=0&size=20
```

### Ejemplo 7: Búsqueda compleja (múltiples filtros)

```bash
GET /api/reportes?tipoUsuario=USER_COO&ccCliente=1098&estadoVisita=PROMETE_PAGAR_DESPUÉS&fechaDesde=2026-09-01&page=0&size=50
```

### Ejemplo 8: Reportes de una novedad específica

```bash
GET /api/reportes?novedadId=4521&page=0&size=20
```

---

## ✅ Response (Respuesta Exitosa)

### Status Code: `200 OK`

```json
{
  "content": [
    {
      "id": 10809,
      "tipoUsuario": "USER_COO",
      "usuarioId": 1,
      "usuarioNombre": "Tyrone Josè",
      "usuarioIdentificacion": "007",
      "reporte": "Alles gut",
      "picture": "https://res.cloudinary.com/dpmkdgej5/image/upload/v1790125784/telco-folder/x7k8rpbv3uceu9aimlhx.jpg",
      "ubicacion": "Cra 26 A#32, Bogotá",
      "fechaCreacion": "2026-09-22T20:09:44",
      "novedadId": 4521,
      "cliente": "Juan Pérez García",
      "ccCliente": "1098765432",
      "estadoVisita": "PAGO_COMPLETO",
      "esSalida": false
    },
    {
      "id": 10810,
      "tipoUsuario": "USER_TEC",
      "usuarioId": 2,
      "usuarioNombre": "Carlos Gómez",
      "usuarioIdentificacion": "456789",
      "reporte": "Instalación completada",
      "picture": "https://res.cloudinary.com/dpmkdgej5/image/upload/v1790125790/telco-folder/abc123def456.jpg",
      "ubicacion": "Cra 45 #23-12 Apt 502",
      "fechaCreacion": "2026-09-22T15:30:00",
      "novedadId": 4520,
      "cliente": "María López Martínez",
      "ccCliente": "987654321",
      "estadoVisita": null,
      "esSalida": false
    }
  ],
  "totalElements": 245,
  "totalPages": 13,
  "currentPage": 0,
  "pageSize": 20
}
```

### Descripción de campos en `content`:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | Long | ID único del reporte |
| `tipoUsuario` | String | Tipo de colaborador: `USER_TEC` (Técnico) o `USER_COO` (Cobrador) |
| `usuarioId` | Long | ID del colaborador que realizó el reporte |
| `usuarioNombre` | String | Nombre completo del colaborador |
| `usuarioIdentificacion` | String | Cédula del colaborador |
| `reporte` | String | Descripción/texto del reporte |
| `picture` | String | URL de la imagen en Cloudinary |
| `ubicacion` | String | Dirección donde se realizó la visita |
| `fechaCreacion` | LocalDateTime | Fecha y hora del reporte (ISO 8601) |
| `novedadId` | Long | ID de la novedad/ticket asociado |
| `cliente` | String | Nombre completo del cliente visitado |
| `ccCliente` | String | Cédula del cliente visitado |
| `estadoVisita` | String | Estado de la visita (solo para cobradores, null para técnicos) |
| `esSalida` | Boolean | Indica si es un reporte de salida (normalmente false) |

### Descripción de campos de paginación:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `content` | Array | Arreglo con los reportes de la página actual |
| `totalElements` | int | Total de reportes que coinciden con los filtros |
| `totalPages` | int | Total de páginas disponibles |
| `currentPage` | int | Número de página actual (0-indexed) |
| `pageSize` | int | Cantidad de registros por página |

---

## ❌ Errores Posibles

### Error 400 - Bad Request

**Causa:** Parámetro de fecha en formato incorrecto

```json
{
  "error": "Invalid date format. Use yyyy-MM-dd",
  "timestamp": "2026-09-23T10:30:00"
}
```

**Solución:** Usa el formato exacto `yyyy-MM-dd` para las fechas.

### Error 401 - Unauthorized

**Causa:** Token JWT ausente o inválido

```json
{
  "error": "Unauthorized",
  "message": "Token not found or expired"
}
```

**Solución:** Incluye el token en el header `Authorization: Bearer <token>`

### Error 404 - Not Found

**Causa:** Recurso no encontrado

```json
{
  "error": "Not Found",
  "message": "No reports found with the specified filters"
}
```

**Solución:** Verifica los parámetros de filtro.

---

## 🔐 Headers Requeridos

Todos los requests deben incluir:

```bash
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### Ejemplo completo con curl:

```bash
curl -X GET "https://tu-backend.com/api/reportes?tipoUsuario=USER_COO&page=0&size=20" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json"
```

---

## 💻 Ejemplos en JavaScript/Frontend

### Ejemplo 1: Obtener todos los reportes

```javascript
const token = localStorage.getItem('token');

fetch('https://tu-backend.com/api/reportes?page=0&size=20', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
})
.then(response => {
  if (!response.ok) throw new Error('Error al obtener reportes');
  return response.json();
})
.then(data => {
  console.log('Reportes:', data.content);
  console.log('Total de reportes:', data.totalElements);
  console.log('Total de páginas:', data.totalPages);
})
.catch(error => console.error('Error:', error));
```

### Ejemplo 2: Filtrar por tipo de usuario (Cobradores)

```javascript
const token = localStorage.getItem('token');
const params = new URLSearchParams({
  tipoUsuario: 'USER_COO',
  page: 0,
  size: 20
});

fetch(`https://tu-backend.com/api/reportes?${params}`, {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
})
.then(response => response.json())
.then(data => {
  console.log('Reportes de cobradores:', data.content);
})
.catch(error => console.error('Error:', error));
```

### Ejemplo 3: Búsqueda avanzada con múltiples filtros

```javascript
const token = localStorage.getItem('token');

const filtros = {
  tipoUsuario: 'USER_COO',
  estadoVisita: 'PAGO_COMPLETO',
  fechaDesde: '2026-09-20',
  fechaHasta: '2026-09-23',
  page: 0,
  size: 50
};

const params = new URLSearchParams(filtros);

fetch(`https://tu-backend.com/api/reportes?${params}`, {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
})
.then(response => response.json())
.then(data => {
  console.log('Reportes filtrados:', data.content);
  
  // Renderizar tabla
  data.content.forEach(reporte => {
    console.log(`${reporte.usuarioNombre} - ${reporte.cliente}: ${reporte.estadoVisita}`);
  });
})
.catch(error => console.error('Error:', error));
```

### Ejemplo 4: Paginación

```javascript
const token = localStorage.getItem('token');
let paginaActual = 0;
const registrosPorPagina = 20;

function cargarPagina(numero) {
  const params = new URLSearchParams({
    page: numero,
    size: registrosPorPagina
  });

  fetch(`https://tu-backend.com/api/reportes?${params}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  })
  .then(response => response.json())
  .then(data => {
    paginaActual = numero;
    console.log(`Página ${numero + 1} de ${data.totalPages}`);
    console.log(`Mostrando ${data.content.length} de ${data.totalElements} reportes`);
    
    // Renderizar reportes
    renderizarTabla(data.content);
    
    // Renderizar controles de paginación
    renderizarPaginacion(data.totalPages, numero);
  })
  .catch(error => console.error('Error:', error));
}

// Llamar la función
cargarPagina(0); // Cargar primera página
```

### Ejemplo 5: Búsqueda en tiempo real por cliente

```javascript
const token = localStorage.getItem('token');

function buscarPorCliente(nombreCliente) {
  if (!nombreCliente || nombreCliente.length < 2) {
    return; // No buscar con menos de 2 caracteres
  }

  const params = new URLSearchParams({
    nombreCliente: nombreCliente,
    page: 0,
    size: 20
  });

  fetch(`https://tu-backend.com/api/reportes?${params}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  })
  .then(response => response.json())
  .then(data => {
    console.log(`Encontrados ${data.totalElements} reportes para "${nombreCliente}"`);
    renderizarResultados(data.content);
  })
  .catch(error => console.error('Error:', error));
}

// Usar en input con event listener
document.getElementById('buscarCliente').addEventListener('input', (e) => {
  buscarPorCliente(e.target.value);
});
```

---

## 📊 Casos de Uso Comunes

### Caso 1: Dashboard de Cobradores - Ver todos los NO PAGOS del mes

```
GET /api/reportes?tipoUsuario=USER_COO&estadoVisita=NO_PAGO&fechaDesde=2026-09-01&fechaHasta=2026-09-30&page=0&size=50
```

**Lógica:** Muestra todos los reportes de cobradores que indicaron "NO_PAGO" para hacer seguimiento.

### Caso 2: Seguimiento de Cliente - Ver todas las visitas a un cliente

```
GET /api/reportes?ccCliente=1098765432&page=0&size=50
```

**Lógica:** Historial completo de todas las visitas (técnicos y cobradores) a ese cliente.

### Caso 3: Reportes enojados/amenaza - Prioridad alta

```
GET /api/reportes?estadoVisita=USUARIO_ENOJADO&page=0&size=20
```

O también:

```
GET /api/reportes?estadoVisita=AMENAZA_RETIRARSE&page=0&size=20
```

**Lógica:** Ver qué clientes están enojados o amenazando retirarse para priorizar contacto.

### Caso 4: Reportes técnicos del día

```
GET /api/reportes?tipoUsuario=USER_TEC&fechaDesde=2026-09-23&fechaHasta=2026-09-23&page=0&size=100
```

**Lógica:** Ver toda la producción técnica del día.

### Caso 5: Promesas de pago - Seguimiento

```
GET /api/reportes?estadoVisita=PROMETE_PAGAR_DESPUÉS&page=0&size=50
```

**Lógica:** Ver clientes que prometieron pagar para hacer seguimiento posterior.

---

## 🎨 Recomendaciones para el Frontend

### Para Tabla de Reportes:

1. **Columnas sugeridas:**
   - Colaborador (nombre + identificación)
   - Tipo (Técnico / Cobrador)
   - Cliente (nombre + CC)
   - Ubicación
   - Estado de Visita (con colores: Verde=Pagó, Rojo=No pagó, Amarillo=Promete, etc.)
   - Reporte (resumen primeras 50 caracteres)
   - Fecha
   - Imagen (clickeable)

2. **Filtros sugeridos en UI:**
   - Input de búsqueda por Cliente (nombre o CC)
   - Dropdown: Tipo Usuario (Técnico / Cobrador)
   - Dropdown: Estado de Visita
   - Date Range: Desde - Hasta
   - Input: Novedad ID
   - Selector de registros por página (20, 50, 100)

3. **Paginación:**
   - Mostrar "Página X de Y"
   - Botones: Anterior, Siguiente
   - Input para ir a página específica

4. **Colores por Estado:**
   - 🟢 PAGO_COMPLETO
   - 🟡 PAGO_PARCIAL, PROMETE_PAGAR_*
   - 🔴 NO_PAGO, USUARIO_ENOJADO, AMENAZA_RETIRARSE
   - ⚪ USUARIO_NO_ESTA, NO_CONTESTA_LLAMADA

---

## 🚀 Performance Tips

- **Usa paginación:** No cargues todo de una vez, usa `size=20` o `size=50`
- **Cachea resultados:** Guarda en localStorage con timestamp si es seguro
- **Debounce en búsqueda:** En búsqueda en tiempo real, espera 300ms después de escribir
- **Lazy load imágenes:** Carga las imágenes cuando scroll
- **Usa filtros específicos:** Combina filtros para reducir resultados

---

## ❓ Preguntas Frecuentes

**P: ¿Qué pasa si no especifico `page` y `size`?**
R: Usa los valores por defecto: `page=0` y `size=20`

**P: ¿Puedo buscar parcialmente en cliente?**
R: Sí, los parámetros `ccCliente` y `nombreCliente` hacen búsqueda parcial (LIKE en SQL)

**P: ¿Cuál es el máximo de registros por página?**
R: 100. Si especificas un valor mayor, se limita a 100.

**P: ¿Qué pasa si no hay resultados?**
R: Devuelve `200 OK` con `content: []` y `totalElements: 0`

**P: ¿Puedo combinar múltiples filtros?**
R: Sí, todos son AND lógico. Ejemplo: `?tipoUsuario=USER_COO&estadoVisita=NO_PAGO&fechaDesde=2026-09-01`

**P: ¿Las fechas incluyen hora?**
R: No en los parámetros. `fechaDesde` = inicio del día, `fechaHasta` = fin del día.

---

## 📞 Soporte

Para preguntas sobre el endpoint, contacta al equipo de backend.
