-- Datos de demostración: reservas y mantenimientos para probar la API desde Swagger sin cargar nada a mano.
-- Las fechas son relativas al día en que se crea la base (CURRENT_DATE). Solo se cargan si no hay reservas ni mantenimientos.
-- Deben coincidir con los eventos de eventos-service (V2__datos_demo.sql): mismos ids de evento, instalaciones y fechas.
DO $$
DECLARE
    cancha  BIGINT := (SELECT id FROM instalacion WHERE nombre = 'Cancha multiuso');
    sum_    BIGINT := (SELECT id FROM instalacion WHERE nombre = 'SUM mediano');
    salon   BIGINT := (SELECT id FROM instalacion WHERE nombre = 'Salón principal y patio exterior');
    cocina  BIGINT := (SELECT id FROM instalacion WHERE nombre = 'Cocina');
    sillas  BIGINT := (SELECT id FROM servicio_complementario WHERE nombre = 'Sillas');
    mesas   BIGINT := (SELECT id FROM servicio_complementario WHERE nombre = 'Mesas');
    sonido  BIGINT := (SELECT id FROM servicio_complementario WHERE nombre = 'Equipo de sonido');
BEGIN
    IF EXISTS (SELECT 1 FROM reserva_espacio) OR EXISTS (SELECT 1 FROM mantenimiento) THEN
        RETURN;
    END IF;

    -- Reservas de las instalaciones (eventos CONFIRMADO, CANCELADO y FINALIZADO de eventos-service)
    INSERT INTO reserva_espacio (evento_id, instalacion_id, inicio, fin, estado) VALUES
    (1, sum_,   (CURRENT_DATE + 20) + TIME '18:00', (CURRENT_DATE + 20) + TIME '22:00', 'ACTIVA'),
    (3, salon,  (CURRENT_DATE + 30) + TIME '17:00', (CURRENT_DATE + 30) + TIME '22:00', 'ACTIVA'),
    (4, salon,  (CURRENT_DATE + 15) + TIME '12:00', (CURRENT_DATE + 15) + TIME '16:00', 'ACTIVA'),
    (5, cocina, (CURRENT_DATE + 12) + TIME '10:00', (CURRENT_DATE + 12) + TIME '12:00', 'LIBERADA'),
    (7, cancha, (CURRENT_DATE - 10) + TIME '18:00', (CURRENT_DATE - 10) + TIME '21:00', 'ACTIVA');

    -- Servicios complementarios reservados y su stock por fecha
    INSERT INTO reserva_servicio (evento_id, servicio_id, fecha, cantidad, estado) VALUES
    (1, sillas, CURRENT_DATE + 20, 20, 'ACTIVA'),
    (3, mesas,  CURRENT_DATE + 30, 10, 'ACTIVA'),
    (3, sonido, CURRENT_DATE + 30, 1,  'ACTIVA');

    INSERT INTO stock_servicio_fecha (servicio_id, fecha, cantidad_reservada) VALUES
    (sillas, CURRENT_DATE + 20, 20),
    (mesas,  CURRENT_DATE + 30, 10),
    (sonido, CURRENT_DATE + 30, 1);

    -- Mantenimientos: dos programados y uno ya finalizado
    INSERT INTO mantenimiento (instalacion_id, desde, hasta, motivo, estado) VALUES
    (cocina, (CURRENT_DATE + 3)  + TIME '00:00', (CURRENT_DATE + 5)  + TIME '00:00', 'Revisión de la instalación de gas', 'ACTIVO'),
    (salon,  (CURRENT_DATE + 40) + TIME '00:00', (CURRENT_DATE + 42) + TIME '00:00', 'Reparación del techo del patio', 'ACTIVO'),
    (cancha, (CURRENT_DATE - 30) + TIME '00:00', (CURRENT_DATE - 28) + TIME '00:00', 'Pintura de las líneas de la cancha', 'FINALIZADO');
END
$$;
