PRAGMA foreign_keys = ON;

-- =======================
-- TABLA PROFESOR
-- =======================
CREATE TABLE IF NOT EXISTS profesor (
                                        profesor_id   INTEGER PRIMARY KEY,             -- AUTOINCREMENT implícito
                                        nombre        TEXT    NOT NULL,
                                        apellido_paterno  TEXT NOT NULL,
                                        apellido_materno  TEXT,
                                        correo_electronico TEXT NOT NULL,
                                        telefono      TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_profesor_correo
    ON profesor(correo_electronico);

-- =======================
-- TABLA CURSO
-- =======================
CREATE TABLE IF NOT EXISTS curso (
                                     curso_id   INTEGER PRIMARY KEY,
                                     nombre     TEXT    NOT NULL,
                                     min_horas_semanales INTEGER NOT NULL,
                                     descripcion TEXT,
                                     color_hex  TEXT    NOT NULL DEFAULT '#FFFFFF'
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_curso_nombre
    ON curso(nombre);

-- =======================
-- TABLA PROFESOR_CURSO (N:M)
-- =======================
CREATE TABLE IF NOT EXISTS profesor_curso (
                                              profesor_id INTEGER NOT NULL,
                                              curso_id    INTEGER NOT NULL,
                                              PRIMARY KEY (profesor_id, curso_id),
    FOREIGN KEY (profesor_id) REFERENCES profesor(profesor_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (curso_id)    REFERENCES curso(curso_id)
    ON DELETE CASCADE ON UPDATE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_profesor_curso_profesor
    ON profesor_curso(profesor_id);

CREATE INDEX IF NOT EXISTS idx_profesor_curso_curso
    ON profesor_curso(curso_id);

-- =======================
-- TABLA GRUPO
-- =======================
CREATE TABLE IF NOT EXISTS grupo (
                                     grupo_id       TEXT    NOT NULL PRIMARY KEY,   -- tus IDs alfanuméricos
                                     curso_id       INTEGER NOT NULL,
                                     profesor_id    INTEGER NOT NULL,
                                     tamanio_grupo  INTEGER NOT NULL,
                                     rango_inicial  INTEGER NOT NULL,
                                     rango_final    INTEGER NOT NULL,
                                     anio           TEXT,      -- '2025'
                                     etapa          TEXT,      -- '1', '2', etc.
                                     FOREIGN KEY (curso_id)    REFERENCES curso(curso_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (profesor_id) REFERENCES profesor(profesor_id)
    ON DELETE CASCADE ON UPDATE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_grupo_curso
    ON grupo(curso_id);

CREATE INDEX IF NOT EXISTS idx_grupo_profesor
    ON grupo(profesor_id);

CREATE INDEX IF NOT EXISTS idx_grupo_ciclo
    ON grupo(anio, etapa);

-- =======================
-- TABLA DISPONIBILIDAD
-- =======================
CREATE TABLE IF NOT EXISTS disponibilidad (
                                              disponibilidad_id INTEGER PRIMARY KEY,
                                              profesor_id       INTEGER NOT NULL,
                                              curso_sugerido    INTEGER,
                                              bloque_inicial    INTEGER,
                                              bloque_final      INTEGER,
                                              FOREIGN KEY (profesor_id) REFERENCES profesor(profesor_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (curso_sugerido) REFERENCES curso(curso_id)
    ON DELETE SET NULL ON UPDATE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_disp_profesor
    ON disponibilidad(profesor_id);

-- =======================
-- TABLA HORARIO
-- =======================
CREATE TABLE IF NOT EXISTS horario (
                                       grupo_id            TEXT    NOT NULL,
                                       anio                TEXT    NOT NULL,
                                       etapa               TEXT    NOT NULL,
                                       bloque_logico_inicio INTEGER NOT NULL,
                                       columna_dia         INTEGER NOT NULL,
                                       fila_visual_inicio  INTEGER NOT NULL,
                                       span_visual_filas   INTEGER NOT NULL,
                                       fecha_generacion    TEXT,        -- ISO-8601 recomendado
                                       PRIMARY KEY (grupo_id, anio, etapa, bloque_logico_inicio),
    FOREIGN KEY (grupo_id) REFERENCES grupo(grupo_id)
    ON DELETE CASCADE ON UPDATE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_horario_ciclo
    ON horario(anio, etapa);

-- =======================
-- TABLA ALUMNO
-- =======================
CREATE TABLE IF NOT EXISTS alumno (
                                      matricula        TEXT    NOT NULL PRIMARY KEY,
                                      nombre_completo  TEXT    NOT NULL,
                                      correo_electronico TEXT  NOT NULL,
                                      numero_lista     INTEGER,
                                      anio             TEXT,
                                      etapa            TEXT
);

CREATE INDEX IF NOT EXISTS idx_alumno_ciclo
    ON alumno(anio, etapa);

-- =======================
-- TABLA ALUMNO_GRUPO (N:M)
-- =======================
CREATE TABLE IF NOT EXISTS alumno_grupo (
                                            matricula  TEXT NOT NULL,
                                            grupo_id   TEXT NOT NULL,
                                            PRIMARY KEY (matricula, grupo_id),
    FOREIGN KEY (matricula) REFERENCES alumno(matricula)
    ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (grupo_id)  REFERENCES grupo(grupo_id)
    ON DELETE CASCADE ON UPDATE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_alumno_grupo_matricula
    ON alumno_grupo(matricula);

CREATE INDEX IF NOT EXISTS idx_alumno_grupo_grupo
    ON alumno_grupo(grupo_id);