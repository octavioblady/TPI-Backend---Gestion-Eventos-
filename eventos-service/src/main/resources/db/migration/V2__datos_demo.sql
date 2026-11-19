-- Datos de demostración: eventos en distintos estados para probar la API desde Swagger sin cargar nada a mano.
-- Las fechas son relativas al día en que se crea la base (CURRENT_DATE). Solo se cargan si no hay eventos.
-- Deben coincidir con las reservas de recursos-service (V3__datos_demo.sql): mismos ids de evento, instalaciones y fechas.
-- Los ids de cliente son los de los usuarios cliente1 y cliente2 del realm de Keycloak (keycloak/complejo-realm.json).
DO $$
DECLARE
    admin_id  CONSTANT VARCHAR := 'admin1';
    cliente1  CONSTANT VARCHAR := 'cliente1';
    cliente2  CONSTANT VARCHAR := 'cliente2';
BEGIN
    IF EXISTS (SELECT 1 FROM evento) THEN
        RETURN;
    END IF;

    INSERT INTO evento (id, version, cliente_id, creado_por, inicio, fin, asistentes, estado, precio_total, senia_monto,
                        senia_abonada, senia_reembolsable, codigo_rechazo, motivo_rechazo, creado_en) VALUES
    -- 1: CONFIRMADO en el SUM con 20 sillas
    (1, 1, cliente1, cliente1, (CURRENT_DATE + 20) + TIME '18:00', (CURRENT_DATE + 20) + TIME '22:00', 30, 'CONFIRMADO',
        44000.00, 22000.00, TRUE, NULL, NULL, NULL, now() - INTERVAL '3 days'),
    -- 2: SOLICITADO en la cancha, a la espera de la seña
    (2, 0, cliente1, cliente1, (CURRENT_DATE + 25) + TIME '10:00', (CURRENT_DATE + 25) + TIME '14:00', 60, 'SOLICITADO',
        40000.00, 20000.00, FALSE, NULL, NULL, NULL, now() - INTERVAL '1 day'),
    -- 3: CONFIRMADO en el salón y patio con mesas y equipo de sonido (a más de 16 días: sin pronóstico)
    (3, 1, cliente2, cliente2, (CURRENT_DATE + 30) + TIME '17:00', (CURRENT_DATE + 30) + TIME '22:00', 100, 'CONFIRMADO',
        90000.00, 45000.00, TRUE, NULL, NULL, NULL, now() - INTERVAL '2 days'),
    -- 4: CONFIRMADO en el salón y patio dentro de los 16 días, con advertencia meteorológica
    (4, 1, cliente2, cliente2, (CURRENT_DATE + 15) + TIME '12:00', (CURRENT_DATE + 15) + TIME '16:00', 80, 'CONFIRMADO',
        40000.00, 20000.00, TRUE, NULL, NULL, NULL, now() - INTERVAL '4 days'),
    -- 5: CANCELADO (con más de 48 h de anticipación: la seña es reembolsable)
    (5, 2, cliente1, cliente1, (CURRENT_DATE + 12) + TIME '10:00', (CURRENT_DATE + 12) + TIME '12:00', 5, 'CANCELADO',
        20000.00, 10000.00, TRUE, TRUE, NULL, NULL, now() - INTERVAL '6 days'),
    -- 6: RECHAZADO por conflicto de disponibilidad (el SUM ya estaba reservado por el evento 1)
    (6, 1, cliente2, cliente2, (CURRENT_DATE + 20) + TIME '19:00', (CURRENT_DATE + 20) + TIME '21:00', 20, 'RECHAZADO',
        20000.00, 10000.00, FALSE, NULL, 'CONFLICTO_DISPONIBILIDAD',
        'La instalación SUM mediano ya está reservada en esa franja', now() - INTERVAL '2 days'),
    -- 7: FINALIZADO (ya ocurrió)
    (7, 3, cliente1, cliente1, (CURRENT_DATE - 10) + TIME '18:00', (CURRENT_DATE - 10) + TIME '21:00', 40, 'FINALIZADO',
        30000.00, 15000.00, TRUE, NULL, NULL, NULL, now() - INTERVAL '40 days');

    INSERT INTO evento_instalacion (evento_id, instalacion_id, nombre, aire_libre, tarifa_hora, horas_facturadas, subtotal) VALUES
    (1, 2, 'SUM mediano', FALSE, 10000.00, 4, 40000.00),
    (2, 1, 'Cancha multiuso', FALSE, 10000.00, 4, 40000.00),
    (3, 3, 'Salón principal y patio exterior', TRUE, 10000.00, 5, 50000.00),
    (4, 3, 'Salón principal y patio exterior', TRUE, 10000.00, 4, 40000.00),
    (5, 4, 'Cocina', FALSE, 10000.00, 2, 20000.00),
    (6, 2, 'SUM mediano', FALSE, 10000.00, 2, 20000.00),
    (7, 1, 'Cancha multiuso', FALSE, 10000.00, 3, 30000.00);

    INSERT INTO evento_servicio (evento_id, servicio_id, nombre, cantidad, precio_unitario, subtotal) VALUES
    (1, 1, 'Sillas', 20, 200.00, 4000.00),
    (3, 2, 'Mesas', 10, 1500.00, 15000.00),
    (3, 3, 'Equipo de sonido', 1, 25000.00, 25000.00);

    INSERT INTO historial_estado_evento (evento_id, estado_anterior, estado_nuevo, motivo, usuario, fecha) VALUES
    (1, NULL, 'SOLICITADO', 'Evento solicitado', cliente1, now() - INTERVAL '3 days'),
    (1, 'SOLICITADO', 'CONFIRMADO', 'Seña abonada y condiciones verificadas', admin_id, now() - INTERVAL '3 days' + INTERVAL '1 hour'),
    (2, NULL, 'SOLICITADO', 'Evento solicitado', cliente1, now() - INTERVAL '1 day'),
    (3, NULL, 'SOLICITADO', 'Evento solicitado', cliente2, now() - INTERVAL '2 days'),
    (3, 'SOLICITADO', 'CONFIRMADO', 'Seña abonada y condiciones verificadas', admin_id, now() - INTERVAL '2 days' + INTERVAL '1 hour'),
    (4, NULL, 'SOLICITADO', 'Evento solicitado', cliente2, now() - INTERVAL '4 days'),
    (4, 'SOLICITADO', 'CONFIRMADO', 'Seña abonada y condiciones verificadas', admin_id, now() - INTERVAL '4 days' + INTERVAL '1 hour'),
    (5, NULL, 'SOLICITADO', 'Evento solicitado', cliente1, now() - INTERVAL '6 days'),
    (5, 'SOLICITADO', 'CONFIRMADO', 'Seña abonada y condiciones verificadas', admin_id, now() - INTERVAL '6 days' + INTERVAL '1 hour'),
    (5, 'CONFIRMADO', 'CANCELADO', 'Cancelado por el cliente', cliente1, now() - INTERVAL '5 days'),
    (6, NULL, 'SOLICITADO', 'Evento solicitado', cliente2, now() - INTERVAL '2 days'),
    (6, 'SOLICITADO', 'RECHAZADO', 'La instalación SUM mediano ya está reservada en esa franja', admin_id, now() - INTERVAL '2 days' + INTERVAL '1 hour'),
    (7, NULL, 'SOLICITADO', 'Evento solicitado', cliente1, now() - INTERVAL '40 days'),
    (7, 'SOLICITADO', 'CONFIRMADO', 'Seña abonada y condiciones verificadas', admin_id, now() - INTERVAL '39 days'),
    (7, 'CONFIRMADO', 'EN_CURSO', 'Inicio del evento', admin_id, (CURRENT_DATE - 10) + TIME '18:00'),
    (7, 'EN_CURSO', 'FINALIZADO', 'Fin del evento', admin_id, (CURRENT_DATE - 10) + TIME '21:00');

    INSERT INTO advertencia_meteorologica (evento_id, consultada_en, probabilidad_lluvia, rafaga_viento_kmh, granizo, detalle) VALUES
    (4, now() - INTERVAL '1 day', 70, 35.0, FALSE, 'Probabilidad de lluvia de 70 % (umbral 40 %)');

    PERFORM setval(pg_get_serial_sequence('evento', 'id'), 7);
END
$$;
