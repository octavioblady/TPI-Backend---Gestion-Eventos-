-- Instalaciones del complejo. Tarifa fija: $10.000 por hora.
-- Capacidades y horarios son valores iniciales de ejemplo; el ADMIN puede modificarlos por la API.
INSERT INTO instalacion (nombre, descripcion, capacidad, aire_libre, activa, tarifa_hora) VALUES
    ('Cancha multiuso', 'Básquet, vóley, futsal y handball', 100, FALSE, TRUE, 10000.00),
    ('SUM mediano', 'Salón de usos múltiples mediano', 50, FALSE, TRUE, 10000.00),
    ('Salón principal y patio exterior', 'Salón principal con patio exterior (instalación conjunta)', 150, TRUE, TRUE, 10000.00),
    ('Cocina', 'Cocina (no incluye catering)', 10, FALSE, TRUE, 10000.00);

-- Horarios: cancha hasta las 22:00, resto hasta las 23:00, todos los días.
INSERT INTO horario_instalacion (instalacion_id, dia, apertura, cierre)
SELECT i.id, d.dia, TIME '08:00', CASE WHEN i.nombre = 'Cancha multiuso' THEN TIME '22:00' ELSE TIME '23:00' END
FROM instalacion i
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) AS d(dia);

INSERT INTO equipamiento_instalacion (instalacion_id, nombre, cantidad)
SELECT id, 'Aros de básquet', 2 FROM instalacion WHERE nombre = 'Cancha multiuso'
UNION ALL SELECT id, 'Arcos de futsal/handball', 2 FROM instalacion WHERE nombre = 'Cancha multiuso'
UNION ALL SELECT id, 'Red de vóley', 1 FROM instalacion WHERE nombre = 'Cancha multiuso';

INSERT INTO servicio_complementario (nombre, descripcion, precio_unitario, stock_total, activo) VALUES
    ('Sillas', 'Silla plegable', 200.00, 200, TRUE),
    ('Mesas', 'Mesa rectangular', 1500.00, 40, TRUE),
    ('Equipo de sonido', 'Equipo de sonido con micrófono', 25000.00, 3, TRUE);
