-- Activamos las claves foráneas
PRAGMA foreign_keys = ON;


-- ==========================================
-- TABLA: RECINTOS
-- ==========================================

CREATE TABLE IF NOT EXISTS RECINTOS (
    id TEXT PRIMARY KEY,
    nombre TEXT NOT NULL
);


-- ==========================================
-- TABLA: ZONAS
-- ==========================================

CREATE TABLE IF NOT EXISTS ZONAS (
    id TEXT PRIMARY KEY,
    recinto_id TEXT NOT NULL,
    nombre TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('general', 'numerada')),
    aforo INTEGER NOT NULL CHECK (aforo > 0),
    filas INTEGER,
    asientos_por_fila INTEGER,

    FOREIGN KEY (recinto_id) REFERENCES RECINTOS(id),

    CHECK (
        (tipo = 'general' AND filas IS NULL AND asientos_por_fila IS NULL)
        OR
        (tipo = 'numerada' AND filas > 0 AND asientos_por_fila > 0
         AND aforo = filas * asientos_por_fila)
    )
);


-- ==========================================
-- TABLA: ASIENTOS
-- ==========================================

CREATE TABLE IF NOT EXISTS ASIENTOS (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    zona_id TEXT NOT NULL,
    fila INTEGER NOT NULL CHECK (fila > 0),
    numero INTEGER NOT NULL CHECK (numero > 0),

    FOREIGN KEY (zona_id) REFERENCES ZONAS(id),

    UNIQUE (zona_id, fila, numero)
);


-- ==========================================
-- TABLA: EVENTOS
-- ==========================================

CREATE TABLE IF NOT EXISTS EVENTOS (
    id TEXT PRIMARY KEY,
    recinto_id TEXT NOT NULL,
    nombre TEXT NOT NULL,
    fecha_hora TEXT NOT NULL,
    categoria TEXT NOT NULL,
    descripcion TEXT,
    imagen TEXT,

    FOREIGN KEY (recinto_id) REFERENCES RECINTOS(id)
);


-- ==========================================
-- TABLA: TIPOS_ENTRADA
-- ==========================================

CREATE TABLE IF NOT EXISTS TIPOS_ENTRADA (
    id TEXT PRIMARY KEY,
    evento_id TEXT NOT NULL,
    zona_id TEXT NOT NULL,
    nombre TEXT NOT NULL,
    precio REAL NOT NULL CHECK (precio >= 0),
    cuota INTEGER NOT NULL CHECK (cuota >= 0),

    FOREIGN KEY (evento_id) REFERENCES EVENTOS(id),
    FOREIGN KEY (zona_id) REFERENCES ZONAS(id)
);


-- ==========================================
-- TABLA: COMPRAS
-- ==========================================

CREATE TABLE IF NOT EXISTS COMPRAS (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    comprador_nombre TEXT NOT NULL,
    comprador_correo TEXT NOT NULL,
    fecha TEXT NOT NULL
);


-- ==========================================
-- TABLA: ENTRADAS
-- ==========================================

CREATE TABLE IF NOT EXISTS ENTRADAS (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    compra_id INTEGER NOT NULL,
    evento_id TEXT NOT NULL,
    tipo_entrada_id TEXT NOT NULL,
    asiento_id INTEGER,
    localizador TEXT NOT NULL UNIQUE,
    titular TEXT NOT NULL,
    precio REAL NOT NULL CHECK (precio >= 0),
    estado TEXT NOT NULL CHECK (
        estado IN ('reservada', 'vendida', 'cancelada')
    ),

    FOREIGN KEY (compra_id) REFERENCES COMPRAS(id),
    FOREIGN KEY (evento_id) REFERENCES EVENTOS(id),
    FOREIGN KEY (tipo_entrada_id) REFERENCES TIPOS_ENTRADA(id),
    FOREIGN KEY (asiento_id) REFERENCES ASIENTOS(id)
);


-- ==========================================
-- EVITAR ASIENTOS DUPLICADOS
-- ==========================================

CREATE UNIQUE INDEX IF NOT EXISTS asiento_ocupado
ON ENTRADAS(evento_id, asiento_id)
WHERE asiento_id IS NOT NULL
AND estado IN ('reservada', 'vendida');