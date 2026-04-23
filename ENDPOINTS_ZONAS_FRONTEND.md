# 🎯 API Asignación de Zonas - Frontend

**Header:** `Authorization: Bearer {token}`

---

## Resumen

| Acción              | Método | Endpoint                        | Body                   |
| ------------------- | ------ | ------------------------------- | ---------------------- |
| Ver todos con zonas | GET    | `/usuarios/con-zonas`           | -                      |
| Ver zonas de uno    | GET    | `/usuarios/{id}/zonas`          | -                      |
| Definir zonas       | PUT    | `/usuarios/{id}/zonas`          | `{"zonaIds": [1,2,3]}` |
| Agregar zonas       | POST   | `/usuarios/{id}/zonas`          | `{"zonaIds": [4,5]}`   |
| Quitar zona         | DELETE | `/usuarios/{id}/zonas/{zonaId}` | -                      |

---

## 1. Ver TODOS los usuarios con sus zonas

```
GET api/usuarios/con-zonas
```

**Response:**

```json
[
  {
    "id": 5,
    "nombre": "Felipe Córdoba",
    "cargo": "Tecnico",
    "zonasAsignadas": [
      { "id": 1, "nombre": "Zona Norte" },
      { "id": 2, "nombre": "Zona Centro" }
    ]
  }
]
```

---

## 2. Ver zonas de UN usuario

```
GET api/usuarios/{id}/zonas
```

**Response:**

```json
{
  "id": 5,
  "nombre": "Felipe Córdoba",
  "zonasAsignadas": [
    { "id": 1, "nombre": "Zona Norte" },
    { "id": 2, "nombre": "Zona Centro" }
  ]
}
```

---

## 3. Definir o editar zonas (PUT - reemplaza todas)

```
PUT api/usuarios/{id}/zonas
Content-Type: application/json
```

**Request:**

```json
{
  "zonaIds": [3, 4]
}
```

**Efecto:** Usuario queda SOLO con zonas 3 y 4 (borró las anteriores)

---

## 4. Agregar zonas (POST - sin borrar)

```
POST api/usuarios/{id}/zonas
Content-Type: application/json
```

**Request:**

```json
{
  "zonaIds": [3, 4]
}
```

**Efecto:** Usuario conserva sus zonas y se le agregan 3 y 4

---

## 5. Quitar zona

```
DELETE api/usuarios/{id}/zonas/{zonaId}
```

**Efecto:** Quita esa zona específica del usuario
