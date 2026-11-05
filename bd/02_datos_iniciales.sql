-- =====================================================================
-- 02_datos_iniciales.sql - Sistema de Reserva de Recursos (Proyecto 2)
--
-- Carga los datos de ejemplo del Proyecto 1 con los mismos IDs.
-- Se corre SOLO en reservas_db, justo despues de 01_schema.sql (que deja
-- las tablas vacias). En reservas_test NO: las pruebas *IT cargan sus
-- propios datos.
--
-- No tiene USE ni el nombre de ninguna base. En MySQL Workbench:
--   1. File > Open SQL Script... y abrir este archivo.
--   2. En el panel SCHEMAS, doble clic en reservas_db para que quede en
--      negrita. Confirmar que es la correcta.
--   3. Ejecutar todo con el rayo (Ctrl+Shift+Enter).
--
-- Cambios respecto a los JSON del Proyecto 1:
--   - Solo las 11 reservas de la rama main (sin las 2 de prueba del 25/10).
--   - RES-000001 y RES-000003 pasan del funcionario 1111111 (no existe)
--     al 44444.
--   - Cada recurso usa la categoria de recursos.json, no la copia que
--     guardaba cada reserva.
--   - Se agregan los recursos 891234, 654321 y 567890, que usaban las
--     reservas pero no estaban en recursos.json.
--   - Se limpian los espacios finales: "Laptop " y "Kristel ".
--
-- Las claves son las del Proyecto 1, guardadas como hash PBKDF2 (generado
-- con Sistema.Logica.GeneradorHashesDatosIniciales, el mismo HashClave que
-- verifica el login). Este script no contiene contrasenas de MySQL.
-- =====================================================================

-- ---------------------------------------------------------------------
-- usuario (5): admin/admin, 44444/44444, 123/1234, 77777/gigi, 222222/222222
-- ---------------------------------------------------------------------
INSERT INTO usuario (id_usuario, clave_hash, rol, activo) VALUES
    ('admin', 'pbkdf2_sha256$600000$8Bqx/lOxBsLvHhapKvsgWg==$uK1cj9dxC/SocTMqSX5EY3vrQr2H9U2OFGL9gQf4Qfs=', 'ADMINISTRADOR', 1),
    ('44444', 'pbkdf2_sha256$600000$PEJMKA/mbIs4Rzvh9FWqVg==$zb5KgOh7zN1psu/GDnQ26POrA9dl3vd57uxecC6pSLA=', 'FUNCIONARIO', 1),
    ('123', 'pbkdf2_sha256$600000$aw+NIs5EXHrh4taogzcqig==$j6slrApUgUdNI4aA+u1UW41s25bRot6Wwh9MGwlBGkA=', 'FUNCIONARIO', 1),
    ('77777', 'pbkdf2_sha256$600000$x4Y3wPzO7JRXTawCk66xUw==$U5OgKHUcVFE9jxxbRkB1elCCG7b+owlXQUcfH8OE27s=', 'FUNCIONARIO', 1),
    ('222222', 'pbkdf2_sha256$600000$zizEnvQtc9wwOx2lAUaX2w==$n7TT618Zhkh0LYNqugmhddSjwBeJ6nF+wZ7y3sPSpaA=', 'FUNCIONARIO', 1);

-- ---------------------------------------------------------------------
-- funcionario (4): mismo ID que su usuario
-- ---------------------------------------------------------------------
INSERT INTO funcionario (id_funcionario, nombre, telefono, activo) VALUES
    ('44444', 'Derek', '24351234', 1),
    ('123', 'Javier', '5676584', 1),
    ('77777', 'Kristel', '60092596', 1),
    ('222222', 'Sofia', '123-123', 1);

-- ---------------------------------------------------------------------
-- categoria (7)
-- ---------------------------------------------------------------------
INSERT INTO categoria (id_categoria, descripcion) VALUES
    ('CAT-000001', 'Sala de estudio'),
    ('CAT-000002', 'Laptop'),
    ('CAT-000003', 'Sala para 10 personas'),
    ('CAT-000004', 'Dispositivo electrónico'),
    ('CAT-000005', 'Accesorio de presentación'),
    ('CAT-000006', 'Mobiliario de apoyo'),
    ('CAT-000007', 'Cubiculo personal');

-- ---------------------------------------------------------------------
-- recurso (11): los 8 de recursos.json y los 3 que faltaban
-- ---------------------------------------------------------------------
INSERT INTO recurso (id_recurso, descripcion, id_categoria, activo) VALUES
    ('771980', 'Aula-2005', 'CAT-000001', 1),
    ('238715', 'Laptop W #238715', 'CAT-000004', 1),
    ('343433', 'Sala 1 primer piso', 'CAT-000003', 1),
    ('235699', 'Proyector E235699', 'CAT-000004', 1),
    ('789013', 'Puntero Láser Rojo', 'CAT-000005', 1),
    ('987654', 'Pizarra Blanca Móvil', 'CAT-000006', 1),
    ('318911', 'Laptop windows #318911', 'CAT-000002', 1),
    ('118554', 'Cubiculo 2005', 'CAT-000007', 1),
    ('891234', 'Monitor Dell 27 pulgadas', 'CAT-000004', 1),
    ('654321', 'Kit Conferencia Logitech', 'CAT-000004', 1),
    ('567890', 'Sala 2A segundo piso', 'CAT-000003', 1);

-- ---------------------------------------------------------------------
-- reserva (11)
-- ---------------------------------------------------------------------
INSERT INTO reserva (id_reserva, id_funcionario, nombre_actividad, fecha, hora_inicio, hora_fin, estado) VALUES
    ('RES-000001', '44444', 'Reunión de equipo', '2026-07-01', '09:00:00', '10:00:00', 'ACTIVA'),
    ('RES-000002', '44444', 'Capacitación técnica', '2026-07-02', '13:00:00', '15:00:00', 'ACTIVA'),
    ('RES-000003', '44444', 'Reunión con proveedor (cancelada)', '2026-07-01', '11:00:00', '12:00:00', 'CANCELADA'),
    ('RES-000004', '44444', 'Sesión de estudio', '2026-06-15', '08:00:00', '09:00:00', 'ACTIVA'),
    ('RES-000005', '44444', 'Capacitación técnica', '2026-09-11', '08:30:00', '10:30:00', 'ACTIVA'),
    ('RES-000006', '77777', 'Reunion de coordinacion', '2026-09-09', '09:00:00', '11:00:00', 'CANCELADA'),
    ('RES-000007', '77777', 'Reunión de seguimiento de proyecto', '2026-09-09', '08:00:00', '10:00:00', 'ACTIVA'),
    ('RES-000008', '123', 'Reunión de ventas', '2026-09-14', '08:00:00', '10:00:00', 'CANCELADA'),
    ('RES-000009', '123', 'Reunión de estudio', '2026-09-09', '10:30:00', '12:00:00', 'CANCELADA'),
    ('RES-000010', '123', 'Examen del tigre', '2026-09-11', '18:00:00', '19:30:00', 'ACTIVA'),
    ('RES-000011', '44444', 'reunión de ventas', '2026-09-28', '08:00:00', '10:00:00', 'CANCELADA');

-- ---------------------------------------------------------------------
-- reserva_recurso (20)
-- ---------------------------------------------------------------------
INSERT INTO reserva_recurso (id_reserva, id_recurso) VALUES
    ('RES-000001', '343433'),
    ('RES-000001', '235699'),
    ('RES-000002', '238715'),
    ('RES-000002', '891234'),
    ('RES-000002', '654321'),
    ('RES-000003', '567890'),
    ('RES-000004', '771980'),
    ('RES-000005', '343433'),
    ('RES-000006', '238715'),
    ('RES-000006', '343433'),
    ('RES-000006', '987654'),
    ('RES-000007', '238715'),
    ('RES-000007', '343433'),
    ('RES-000007', '987654'),
    ('RES-000008', '343433'),
    ('RES-000008', '789013'),
    ('RES-000009', '771980'),
    ('RES-000010', '343433'),
    ('RES-000011', '343433'),
    ('RES-000011', '789013');

-- ---------------------------------------------------------------------
-- Verificacion (seleccionar y ejecutar aparte). Esperado:
-- usuario 5, funcionario 4, categoria 7, recurso 11, reserva 11,
-- reserva_recurso 20.
-- ---------------------------------------------------------------------
-- SELECT 'usuario' AS tabla, COUNT(*) AS filas FROM usuario
-- UNION ALL SELECT 'funcionario', COUNT(*) FROM funcionario
-- UNION ALL SELECT 'categoria', COUNT(*) FROM categoria
-- UNION ALL SELECT 'recurso', COUNT(*) FROM recurso
-- UNION ALL SELECT 'reserva', COUNT(*) FROM reserva
-- UNION ALL SELECT 'reserva_recurso', COUNT(*) FROM reserva_recurso;
