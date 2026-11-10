# Arquitectura LOGYX

## Estilo general
**Monolito modular + arquitectura hexagonal por módulo** en el backend; **organización por feature**
en web y móvil. Misma idea en los tres: *el código de una capacidad de negocio vive junto*, y dentro
de ella las dependencias apuntan hacia el dominio.

Por qué (resumen de ADR-0002):
- Escala por módulos: un módulo con carga (pricing, matching) se extrae a servicio sin reescribir dominio.
- Descubrible por IAs y personas: para trabajar en "ofertas" se abre `marketplace/` y nada más;
  todos los módulos tienen la misma forma, así que un agente que entendió uno entiende todos.
- Testeable: el dominio no depende de Quarkus ni de JPA.

## Backend — forma de un módulo
```
pe/logyx/<modulo>/
├── domain/                  # Reglas puras. Sin Quarkus, sin JPA, sin Jackson.
│   ├── model/               #   Entidades y value objects (records/clases con comportamiento)
│   └── <Regla>Policy.java   #   Reglas de negocio con nombre (p. ej. PriceFloorPolicy = RN03)
├── application/             # Casos de uso. Orquestan dominio + puertos. @Transactional vive aquí.
│   ├── port/in/             #   (opcional) interfaces de casos de uso
│   ├── port/out/            #   Interfaces que el dominio necesita: repositorios, gateways
│   └── <Accion>UseCase.java #   o <Modulo>Service.java si son varios casos pequeños
└── infrastructure/          # Adaptadores
    ├── persistence/         #   @Entity JPA + Panache, implementan port/out, mapean a domain
    ├── web/                 #   Recursos JAX-RS + DTOs (records) de request/response
    └── scheduling/ | client/#   Jobs, clientes HTTP externos
```
Dependencias permitidas: `infrastructure → application → domain`. Un módulo usa a otro **solo a
través de su `application`** (nunca su `infrastructure`). Kernel compartido: `pe.logyx.shared`
(envoltorio de respuesta, errores, tenancy/RLS, auditoría).

Módulos: `identity` (auth + organizaciones + usuarios), `fleet`, `catalog`, `pricing`,
`marketplace` (solicitudes, subastas, ofertas), `shipment` (envíos, paradas, tracking).
Pendientes (ver `docs/STATUS.md`): `negotiation`, `reputation`, `documents`, `notification`,
`incident`, `operator`, `smartload`, `returnload`.

## Web (Astro) — forma
```
web/src/
├── pages/                 # Rutas finas: leen sesión, llaman a features, renderizan
├── features/<feature>/    # components/ (Astro o React), server.ts (llamadas API), types.ts
├── components/ui/         # Primitivas shadcn/ui (no lógica de negocio)
├── components/layout/     # Shell, sidebar, topbar
├── lib/api/               # Cliente tipado generado desde docs/api/openapi.yaml
├── lib/session.ts         # Cookie httpOnly con el JWT (el token nunca llega al JS del navegador)
└── styles/                # tokens.css (de docs/design/DESIGN.md)
```

## Android — forma (estilo "Now in Android")
```
android/
├── app/                   # Activity, navegación raíz, DI
├── core/model/            # Kotlin puro: modelos del dominio (serializables)       ← compartible con iOS
├── core/network/          # Kotlin puro: Ktor + DTOs del contrato                   ← compartible con iOS
├── core/data/             # Repositorios, sesión (DataStore)
├── core/designsystem/     # Tema LOGYX, componentes Compose
└── feature/<feature>/     # ui/ (Screen + ViewModel + UiState), por feature
```

## Aislamiento de datos
Ver `docs/adr/0001-aislamiento-por-organizacion.md`.
