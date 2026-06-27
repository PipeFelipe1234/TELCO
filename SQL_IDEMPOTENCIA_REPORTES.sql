-- Blindaje de idempotencia para reportes offline
-- Tabla real en este backend: registro_reportes
-- Clave unica tecnica: (registro_id, fecha_hora)

-- 1) Revisar cuantas parejas duplicadas existen
SELECT registro_id, fecha_hora, COUNT(*) AS total
FROM registro_reportes
GROUP BY registro_id, fecha_hora
HAVING COUNT(*) > 1;

-- 2) Eliminar duplicados conservando el id mas pequeno de cada pareja
DELETE rr1
FROM registro_reportes rr1
JOIN registro_reportes rr2
  ON rr1.registro_id = rr2.registro_id
 AND rr1.fecha_hora = rr2.fecha_hora
 AND rr1.id > rr2.id;

-- 3) Crear restriccion unica para bloquear nuevos duplicados
ALTER TABLE registro_reportes
ADD CONSTRAINT uq_registro_reporte_fecha
UNIQUE (registro_id, fecha_hora);
