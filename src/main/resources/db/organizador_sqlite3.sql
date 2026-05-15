PRAGMA foreign_keys = ON;

CREATE TABLE profesor (
                          profesor_id INTEGER PRIMARY KEY AUTOINCREMENT,
                          nombre TEXT NOT NULL,
                          apellido_paterno TEXT NOT NULL,
                          apellido_materno TEXT,
                          correo_electronico TEXT NOT NULL,
                          telefono TEXT
);

CREATE TABLE curso (
                       curso_id INTEGER PRIMARY KEY AUTOINCREMENT,
                       nombre TEXT NOT NULL UNIQUE,
                       min_horas_semanales INTEGER NOT NULL,
                       descripcion TEXT,
                       color_hex TEXT NOT NULL DEFAULT '#FFFFFF'
);

CREATE TABLE profesor_curso (
                                profesor_id INTEGER NOT NULL,
                                curso_id INTEGER NOT NULL,
                                PRIMARY KEY (profesor_id, curso_id),
                                FOREIGN KEY (profesor_id)
                                    REFERENCES profesor (profesor_id)
                                    ON DELETE CASCADE ON UPDATE CASCADE,
                                FOREIGN KEY (curso_id)
                                    REFERENCES curso (curso_id)
                                    ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE grupo (
                       grupo_id TEXT PRIMARY KEY,
                       curso_id INTEGER NOT NULL,
                       profesor_id INTEGER NOT NULL,
                       tamanio_grupo INTEGER NOT NULL,
                       rango_inicial INTEGER NOT NULL,
                       rango_final INTEGER NOT NULL,
                       anio TEXT,
                       etapa TEXT,
                       FOREIGN KEY (profesor_id)
                           REFERENCES profesor (profesor_id)
                           ON DELETE CASCADE ON UPDATE CASCADE,
                       FOREIGN KEY (curso_id)
                           REFERENCES curso (curso_id)
                           ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE disponibilidad (
                                disponibilidad_id INTEGER PRIMARY KEY AUTOINCREMENT,
                                profesor_id INTEGER NOT NULL,
                                curso_sugerido INTEGER,
                                bloque_inicial INTEGER,
                                bloque_final INTEGER,
                                FOREIGN KEY (profesor_id)
                                    REFERENCES profesor (profesor_id)
                                    ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE horario (
                         grupo_id TEXT NOT NULL,
                         anio TEXT NOT NULL,
                         etapa TEXT NOT NULL,
                         bloque_logico_inicio INTEGER NOT NULL,
                         columna_dia INTEGER NOT NULL,
                         fila_visual_inicio INTEGER NOT NULL,
                         span_visual_filas INTEGER NOT NULL,
                         fecha_generacion TEXT,
                         PRIMARY KEY (grupo_id, anio, etapa, bloque_logico_inicio),
                         FOREIGN KEY (grupo_id)
                             REFERENCES grupo (grupo_id)
                             ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE alumno (
                        matricula TEXT PRIMARY KEY,
                        nombre_completo TEXT NOT NULL,
                        correo_electronico TEXT NOT NULL,
                        numero_lista INTEGER,
                        anio TEXT,
                        etapa TEXT
);

CREATE TABLE alumno_grupo (
                              matricula TEXT NOT NULL,
                              grupo_id TEXT NOT NULL,
                              PRIMARY KEY (matricula, grupo_id),
                              FOREIGN KEY (matricula)
                                  REFERENCES alumno (matricula)
                                  ON DELETE CASCADE ON UPDATE CASCADE,
                              FOREIGN KEY (grupo_id)
                                  REFERENCES grupo (grupo_id)
                                  ON DELETE CASCADE ON UPDATE CASCADE
);