# ADR-0005 — Almacenamiento de documentos y evidencia

**Estado:** aceptado · 2026-11-10

## Contexto
RF37/RF46 exigen subir fotos de entrega, firmas y documentos del envío (guía de remisión, orden de servicio).
La arquitectura objetivo (E3) usa MinIO/S3, pero el MVP corre en una sola máquina y los tests no deben depender
de servicios externos.

## Decisión
- Puerto `documents/application/port/out/DocumentStorage` (`put/open/delete` por **clave**), adaptador
  `documents/infrastructure/storage/LocalFileSystemDocumentStorage` sobre `logyx.documents.storage-dir`
  (por omisión `~/.logyx/documents`, env `LOGYX_DOCUMENTS_DIR`; en tests `${java.io.tmpdir}/logyx-test-documents`).
  Escritura atómica (temporal + move) y verificación de que la clave no escape del directorio raíz.
- La clave la genera el servidor (`shipments/{shipmentId}/{documentId}.{ext}`); el nombre del cliente solo se
  guarda saneado para mostrar. En BD (`documents`, V5) van los metadatos, tamaño y SHA-256.
- Upload `multipart/form-data` (`file`, `type`, `stopId`). Formato validado por **firma binaria** (JPG/PNG/PDF),
  no por extensión ni Content-Type; máx. `logyx.documents.max-size-bytes` (10 MB; body HTTP 12M).
- La descarga pasa por la API (`GET /documents/{id}/file`): la autorización es la RLS de `documents`
  (partes del envío + operador) más la regla del conductor de `ShipmentQueries.detail`. No hay URLs públicas.
- Completar la entrega exige un upload previo de `DELIVERY_PHOTO` (no se envían archivos en `complete`).

## Consecuencias
- (+) Cambiar a S3/MinIO = un adaptador nuevo que implemente `DocumentStorage`; el contrato HTTP no cambia.
  Con S3 se podría devolver una URL firmada temporal en vez de transmitir el archivo.
- (−) En disco local no hay replicación ni antivirus; antes de producción: volumen persistente con backup o S3.
- (−) Si el commit falla después de escribir el archivo puede quedar un huérfano (se borra si falla el insert).
