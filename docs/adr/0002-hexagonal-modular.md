# ADR-0002 — Monolito modular con hexagonal por módulo; features en clientes

**Estado:** aceptado · 2026-11-10 · Reemplaza ADR-01 del documento de arquitectura del EPE (lo precisa).

## Decisión
- Backend: un desplegable Quarkus; dentro, un paquete por capacidad de negocio con capas
  `domain / application / infrastructure` (puertos y adaptadores). Ver `docs/architecture/overview.md`.
- Web y Android: carpeta por feature; primitivas de UI separadas de la lógica.
- Un agente debe poder resolver una tarea abriendo **una** carpeta de módulo + el contrato OpenAPI.

## Alternativas descartadas
- Clean Architecture con capas globales (`controllers/`, `services/`, `repositories/` en la raíz):
  dispersa cada feature en N carpetas; peor para extraer servicios y para agentes.
- Microservicios desde el inicio: costo operativo injustificado para 3 personas / MVP.
