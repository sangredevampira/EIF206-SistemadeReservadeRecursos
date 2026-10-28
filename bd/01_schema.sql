-- =====================================================================
-- 01_schema.sql - Sistema de Reserva de Recursos (Proyecto 2)
--
-- !!! ATENCION: ESTE SCRIPT DEJA VACIA LA BASE SELECCIONADA !!!
-- Empieza con DROP TABLE IF EXISTS: borra las 6 tablas y TODOS sus datos
-- y las vuelve a crear vacias. Correrlo sobre reservas_db borra todas las
-- reservas, usuarios, funcionarios, categorias y recursos guardados.
--
-- No tiene USE ni el nombre de ninguna base: crea las tablas en la base
-- seleccionada. Se corre una vez con reservas_db y otra con reservas_test
-- (primero hay que correr 00_bases.sql). En MySQL Workbench:
--   1. File > Open SQL Script... y abrir este archivo.
--   2. En el panel SCHEMAS, doble clic en la base (reservas_db o
--      reservas_test) para que quede en negrita. Confirmar que es la correcta.
--   3. Ejecutar todo con el rayo (Ctrl+Shift+Enter).
-- No usar File > Run SQL Script: en Workbench 8.0 falla con errores internos.
-- Las 3 advertencias 1681 "Integer display width is deprecated" por
-- TINYINT(1) son normales: es el tipo del modelo entidad-relacion.
--
-- Collation utf8mb4_0900_as_ci: no distingue mayusculas ("laptop" = "LAPTOP")
-- pero si tildes ("Camara" <> "Cámara"), igual que equalsIgnoreCase del
-- Proyecto 1. Vale tambien para los IDs.
-- Este script no crea usuarios de MySQL ni contiene contrasenas.
-- =====================================================================

-- Se borran en orden inverso a las FK: primero las tablas hijas.
DROP TABLE IF EXISTS reserva_recurso;
DROP TABLE IF EXISTS reserva;
DROP TABLE IF EXISTS recurso;
DROP TABLE IF EXISTS categoria;
DROP TABLE IF EXISTS funcionario;
DROP TABLE IF EXISTS usuario;

-- ---------------------------------------------------------------------
-- usuario: credenciales y rol. La clave nunca se guarda en claro:
-- clave_hash tiene el hash PBKDF2WithHmacSHA256 junto con su sal.
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id_usuario  VARCHAR(20)  NOT NULL,
    clave_hash  VARCHAR(255) NOT NULL,
    rol         ENUM('ADMINISTRADOR','FUNCIONARIO') NOT NULL,
    activo      TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT pk_usuario PRIMARY KEY (id_usuario),
    CONSTRAINT chk_usuario_activo CHECK (activo IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- ---------------------------------------------------------------------
-- funcionario: comparte el ID con su usuario (PK y FK a la vez).
-- El administrador no tiene funcionario. Baja logica con activo = 0.
-- ---------------------------------------------------------------------
CREATE TABLE funcionario (
    id_funcionario VARCHAR(20)  NOT NULL,
    nombre         VARCHAR(100) NOT NULL,
    telefono       VARCHAR(20)  NOT NULL,
    activo         TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT pk_funcionario PRIMARY KEY (id_funcionario),
    CONSTRAINT fk_funcionario_usuario FOREIGN KEY (id_funcionario)
        REFERENCES usuario (id_usuario)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_funcionario_activo CHECK (activo IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- ---------------------------------------------------------------------
-- categoria: sin baja logica. La descripcion no se repite (sin distinguir
-- mayusculas) y se guarda sin espacios al inicio o al final.
-- ---------------------------------------------------------------------
CREATE TABLE categoria (
    id_categoria VARCHAR(20)  NOT NULL,
    descripcion  VARCHAR(100) NOT NULL,
    CONSTRAINT pk_categoria PRIMARY KEY (id_categoria),
    CONSTRAINT uq_categoria_descripcion UNIQUE (descripcion),
    CONSTRAINT chk_categoria_descripcion
        CHECK (descripcion <> '' AND descripcion = TRIM(descripcion))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- ---------------------------------------------------------------------
-- recurso: pertenece a una categoria (obligatoria). Baja logica con
-- activo = 0. RESTRICT: una categoria con recursos, aunque esten
-- inactivos, no se puede eliminar.
-- ---------------------------------------------------------------------
CREATE TABLE recurso (
    id_recurso   VARCHAR(20)  NOT NULL,
    descripcion  VARCHAR(100) NOT NULL,
    id_categoria VARCHAR(20)  NOT NULL,
    activo       TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT pk_recurso PRIMARY KEY (id_recurso),
    CONSTRAINT fk_recurso_categoria FOREIGN KEY (id_categoria)
        REFERENCES categoria (id_categoria)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_recurso_activo CHECK (activo IN (0,1)),
    INDEX idx_recurso_categoria (id_categoria)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- ---------------------------------------------------------------------
-- reserva: estado con los mismos valores del enum EstadoReserva.
-- Indices compuestos para las consultas reales:
--   (fecha, estado)          reservas activas de un periodo
--   (id_funcionario, fecha)  reservas futuras de un funcionario antes de
--                            una baja; tambien es el indice de su FK
-- ---------------------------------------------------------------------
CREATE TABLE reserva (
    id_reserva       VARCHAR(20)  NOT NULL,
    id_funcionario   VARCHAR(20)  NOT NULL,
    nombre_actividad VARCHAR(200) NOT NULL,
    fecha            DATE         NOT NULL,
    hora_inicio      TIME         NOT NULL,
    hora_fin         TIME         NOT NULL,
    estado           ENUM('ACTIVA','CANCELADA') NOT NULL DEFAULT 'ACTIVA',
    CONSTRAINT pk_reserva PRIMARY KEY (id_reserva),
    CONSTRAINT fk_reserva_funcionario FOREIGN KEY (id_funcionario)
        REFERENCES funcionario (id_funcionario)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_reserva_horas CHECK (hora_inicio < hora_fin),
    INDEX idx_reserva_fecha_estado (fecha, estado),
    INDEX idx_reserva_funcionario_fecha (id_funcionario, fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- ---------------------------------------------------------------------
-- reserva_recurso: recursos de cada reserva. La PK compuesta impide
-- repetir un recurso en la misma reserva. CASCADE hacia reserva: el
-- detalle no existe sin su reserva (cancelar no borra, solo cambia estado).
-- RESTRICT hacia recurso: un recurso con reservas no se borra, se desactiva.
-- ---------------------------------------------------------------------
CREATE TABLE reserva_recurso (
    id_reserva VARCHAR(20) NOT NULL,
    id_recurso VARCHAR(20) NOT NULL,
    CONSTRAINT pk_reserva_recurso PRIMARY KEY (id_reserva, id_recurso),
    CONSTRAINT fk_rr_reserva FOREIGN KEY (id_reserva)
        REFERENCES reserva (id_reserva)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_rr_recurso FOREIGN KEY (id_recurso)
        REFERENCES recurso (id_recurso)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_rr_recurso (id_recurso)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
