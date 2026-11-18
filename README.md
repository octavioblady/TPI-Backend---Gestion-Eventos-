# Gestión de eventos en un complejo multiusos — TPI Backend 2026

Microservicios Spring Boot (Java 21) con API Gateway, Keycloak (OIDC/JWT), PostgreSQL, OpenAPI, Docker Compose y colección Postman. Documentación técnica, diagramas y tabla de códigos HTTP en [`docs/DOCUMENTACION-TECNICA.md`](docs/DOCUMENTACION-TECNICA.md).

## Estructura
- `common-library/`: errores estándar, utilidades de tiempo (zona Córdoba) y conversor de roles de Keycloak. Sin lógica de dominio.
- `recursos-service/` (8081): instalaciones, mantenimientos, servicios con stock, disponibilidad y reservas atómicas (`/internal/**`, rol SERVICIO).
- `eventos-service/` (8082): ciclo de vida del evento, precio, seña, compensación, resiliencia y pronóstico (Open-Meteo).
- `api-gateway/` (8000): único punto de entrada; valida JWT y permisos por rol; Swagger UI unificado.
- `keycloak/complejo-realm.json`: realm con roles ADMIN/CLIENTE/SERVICIO y usuarios `admin1`, `cliente1`, `cliente2` (contraseña = usuario; solo desarrollo).
- `postman/`: colección con escenarios positivos y negativos (E-01 a E-30) y su environment.

## Requisitos
Java 21, Maven 3.9, Docker Desktop (para el despliegue integral).

## Pruebas automáticas (no requieren Docker)
```bash
cd common-library && mvn install     # instala la librería en .m2 (hacerlo primero)
cd ../recursos-service && mvn test
cd ../eventos-service && mvn test
cd ../api-gateway && mvn test
```

## Despliegue integral
```bash
copy .env.example .env      # PowerShell/CMD (en Linux/Mac: cp). Editar contraseñas
docker compose up --build
```
- Gateway: http://localhost:8000 — Swagger UI: http://localhost:8000/swagger-ui.html
- Keycloak: http://localhost:8080 (admin con las credenciales del `.env`)
- Token de prueba (Postman o curl): `POST http://localhost:8080/realms/complejo/protocol/openid-connect/token` con `client_id=complejo-api`, `grant_type=password`, `username=cliente1`, `password=cliente1`.

## Postman
Importar `postman/TPI-Gestion-Eventos.postman_collection.json` y `postman/TPI-Gestion-Eventos.postman_environment.json`. La colección obtiene sola los tokens y los ids del catálogo. La carpeta 04 es manual (detener `recursos-service` con `docker compose stop recursos-service`).

## Decisiones principales
- Tarifa fija de $10.000/hora; seña del 50% como condición para confirmar (sin seña: RECHAZADO).
- Rechazo de negocio: respuesta 200/201 con estado RECHAZADO y `codigoRechazo`; falla técnica de Recursos: 503/504.
- Reservas atómicas e idempotentes por `eventoId`; sin doble reserva (bloqueo pesimista + restricción de exclusión en PostgreSQL).
- Eventos de un solo día; inicio y finalización manuales por ADMIN.
- Los datos iniciales (capacidades, horarios, precios de servicios) son valores de ejemplo editables por la API.

## Límites conocidos
- La consulta real a Open-Meteo no se probó en el entorno de desarrollo (sin acceso a Internet); está cubierta con un proveedor simulado y con la falla controlada.
- E-12 (cancelación con menos de 48 h) está cubierta por prueba automática con reloj fijo, no por Postman.
