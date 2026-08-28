-- Migracion para aceptar novedadId grandes en reportes
-- Ejecutar en MySQL/Railway sobre la base de datos productiva

ALTER TABLE registro_reportes
MODIFY COLUMN novedad_id BIGINT NULL;
