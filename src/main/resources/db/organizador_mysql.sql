-- MySQL Workbench Forward Engineering

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- -----------------------------------------------------
-- Schema organizador_db
-- -----------------------------------------------------

-- -----------------------------------------------------
-- Schema organizador_db
-- -----------------------------------------------------
CREATE SCHEMA IF NOT EXISTS `organizador_db` DEFAULT CHARACTER SET utf8 COLLATE utf8_unicode_ci ;
USE `organizador_db` ;

-- -----------------------------------------------------
-- Table `organizador_db`.`profesor`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `organizador_db`.`profesor` (
                                                           `profesor_id` INT NOT NULL AUTO_INCREMENT,
                                                           `nombre` VARCHAR(45) NOT NULL,
    `apellido_paterno` VARCHAR(45) NOT NULL,
    `apellido_materno` VARCHAR(45) NULL,
    `correo_electronico` VARCHAR(45) NOT NULL,
    `telefono` VARCHAR(25) NULL,
    PRIMARY KEY (`profesor_id`),
    UNIQUE INDEX `profesor_id_UNIQUE` (`profesor_id` ASC) VISIBLE)
    ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `organizador_db`.`curso`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `organizador_db`.`curso` (
                                                        `curso_id` INT NOT NULL AUTO_INCREMENT,
                                                        `nombre` VARCHAR(45) NOT NULL,
    `min_horas_semanales` INT NOT NULL,
    `descripcion` TEXT NULL,
    `color_hex` VARCHAR(7) NOT NULL DEFAULT '#FFFFFF',
    PRIMARY KEY (`curso_id`),
    UNIQUE INDEX `curso_id_UNIQUE` (`curso_id` ASC) VISIBLE,
    UNIQUE INDEX `nombre_UNIQUE` (`nombre` ASC) VISIBLE)
    ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `organizador_db`.`profesor_curso`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `organizador_db`.`profesor_curso` (
                                                                 `profesor_id` INT NOT NULL,
                                                                 `curso_id` INT NOT NULL,
                                                                 PRIMARY KEY (`profesor_id`, `curso_id`),
    INDEX `fk_profesor_has_curso_profesor_idx` (`profesor_id` ASC) VISIBLE,
    INDEX `fk_profesor_has_curso_curso1_idx` (`curso_id` ASC) VISIBLE,
    CONSTRAINT `fk_profesor_has_curso_profesor`
    FOREIGN KEY (`profesor_id`)
    REFERENCES `organizador_db`.`profesor` (`profesor_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
    CONSTRAINT `fk_profesor_has_curso_curso1`
    FOREIGN KEY (`curso_id`)
    REFERENCES `organizador_db`.`curso` (`curso_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
    ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `organizador_db`.`grupo`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `organizador_db`.`grupo` (
                                                        `grupo_id` VARCHAR(50) NOT NULL,
    `curso_id` INT NOT NULL,
    `profesor_id` INT NOT NULL,
    `tamanio_grupo` INT NOT NULL,
    `rango_inicial` INT NOT NULL,
    `rango_final` INT NOT NULL,
    `anio` VARCHAR(4) NULL,
    `etapa` VARCHAR(3) NULL,
    PRIMARY KEY (`grupo_id`),
    UNIQUE INDEX `grupo_id_UNIQUE` (`grupo_id` ASC) VISIBLE,
    INDEX `fk_grupo_profesor1_idx` (`profesor_id` ASC) VISIBLE,
    INDEX `fk_grupo_curso1_idx` (`curso_id` ASC) VISIBLE,
    CONSTRAINT `fk_grupo_profesor1`
    FOREIGN KEY (`profesor_id`)
    REFERENCES `organizador_db`.`profesor` (`profesor_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
    CONSTRAINT `fk_grupo_curso1`
    FOREIGN KEY (`curso_id`)
    REFERENCES `organizador_db`.`curso` (`curso_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
    ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `organizador_db`.`disponibilidad`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `organizador_db`.`disponibilidad` (
                                                                 `disponibilidad_id` INT NOT NULL AUTO_INCREMENT,
                                                                 `profesor_id` INT NOT NULL,
                                                                 `curso_sugerido` INT NULL,
                                                                 `bloque_inicial` INT NULL,
                                                                 `bloque_final` INT NULL,
                                                                 PRIMARY KEY (`disponibilidad_id`),
    INDEX `fk_disponibilidad_profesor1_idx` (`profesor_id` ASC) VISIBLE,
    CONSTRAINT `fk_disponibilidad_profesor1`
    FOREIGN KEY (`profesor_id`)
    REFERENCES `organizador_db`.`profesor` (`profesor_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
    ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `organizador_db`.`horario`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `organizador_db`.`horario` (
                                                          `grupo_id` VARCHAR(50) NOT NULL,
    `anio` VARCHAR(4) NOT NULL,
    `etapa` VARCHAR(3) NOT NULL,
    `bloque_logico_inicio` INT NOT NULL,
    `columna_dia` INT NOT NULL,
    `fila_visual_inicio` INT NOT NULL,
    `span_visual_filas` INT NOT NULL,
    `fecha_generacion` TIMESTAMP NULL,
    PRIMARY KEY (`grupo_id`, `anio`, `etapa`, `bloque_logico_inicio`),
    CONSTRAINT `fk_horario_grupo1`
    FOREIGN KEY (`grupo_id`)
    REFERENCES `organizador_db`.`grupo` (`grupo_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
    ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `organizador_db`.`alumno`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `organizador_db`.`alumno` (
                                                         `matricula` VARCHAR(50) NOT NULL,
    `nombre_completo` VARCHAR(150) NOT NULL,
    `correo_electronico` VARCHAR(100) NOT NULL,
    `numero_lista` INT NULL,
    `anio` VARCHAR(20) NULL,
    `etapa` VARCHAR(20) NULL,
    PRIMARY KEY (`matricula`))
    ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `organizador_db`.`alumno_grupo`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `organizador_db`.`alumno_grupo` (
                                                               `matricula` VARCHAR(50) NOT NULL,
    `grupo_id` VARCHAR(50) NOT NULL,
    PRIMARY KEY (`matricula`, `grupo_id`),
    INDEX `fk_alumno_has_grupo_grupo1_idx` (`grupo_id` ASC) VISIBLE,
    INDEX `fk_alumno_has_grupo_alumno1_idx` (`matricula` ASC) VISIBLE,
    CONSTRAINT `fk_alumno_has_grupo_alumno1`
    FOREIGN KEY (`matricula`)
    REFERENCES `organizador_db`.`alumno` (`matricula`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
    CONSTRAINT `fk_alumno_has_grupo_grupo1`
    FOREIGN KEY (`grupo_id`)
    REFERENCES `organizador_db`.`grupo` (`grupo_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
    ENGINE = InnoDB;


SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
