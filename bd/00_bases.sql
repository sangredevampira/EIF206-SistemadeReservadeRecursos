-- =====================================================================
-- 00_bases.sql - Sistema de Reserva de Recursos (Proyecto 2)
--
-- Crea las dos bases del proyecto, vacias:
--   reservas_db   : la que usa el Backend (servidor.properties)
--   reservas_test : la de las pruebas de integracion *IT (servidor-test.properties)
--
-- Usa IF NOT EXISTS: si una base ya existe, no la toca ni borra sus datos.
-- Las tablas se crean despues con 01_schema.sql, una vez en cada base.
-- Este script no crea usuarios de MySQL ni contiene contrasenas.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS reservas_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_as_ci;

CREATE DATABASE IF NOT EXISTS reservas_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_as_ci;
