/**
 * BORRADOR de «Seguridad y seguro» (roadmap 2.17, V-02): qué hace OnCargo, qué cubre el transportista y cómo se reclama.
 * Pendiente de revisión legal y comercial: no promete coberturas que OnCargo no da. Debe ser coherente con
 * `terms-content.ts` (sección «Responsabilidad por la carga»).
 */
export type SafetySection = { id: string; h: string; p: string[]; list?: string[] };

export const SAFETY_SECTIONS: SafetySection[] = [
  { id: 'oncargo', h: 'Lo que hace OnCargo', p: [
    'OnCargo es la plataforma donde se publica, se adjudica y se sigue el envío. No es transportista ni aseguradora: no custodia la carga y no ofrece un seguro propio.',
  ], list: [
    'Revisa cada empresa antes de habilitarla: RUC, razón social y datos de contacto, en 1 día hábil.',
    'Registra las evidencias del envío: recojo, entrega con foto y firma, y los ajustes aceptados.',
    'Oculta teléfonos y correos hasta que la PYME acepta una oferta.',
    'Recibe las incidencias y media entre las partes con plazos de respuesta según su gravedad.',
  ] },
  { id: 'transportista', h: 'Lo que cubre el transportista', p: [
    'El transportista responde por la carga desde que la recibe hasta que la entrega, según su póliza de seguro de transporte, la normativa aplicable y lo acordado en el envío.',
    'Debe mantener vigentes su SOAT, la revisión técnica de sus vehículos y la licencia de sus conductores; OnCargo registra sus vencimientos. Antes de aceptar una oferta, la PYME puede pedirle por el chat las condiciones de su póliza.',
  ] },
  { id: 'pyme', h: 'Lo que declara la PYME', p: [
    'La PYME declara el tipo, el peso y la naturaleza de la carga, y puede registrar su valor declarado en la solicitud. Esa información define el vehículo adecuado y es la referencia ante un reclamo.',
    'Si la carga es de alto valor o la póliza del transportista no la cubre, la PYME puede contratar un seguro de carga por su cuenta.',
  ] },
  { id: 'reclamos', h: 'Cómo se reclama', p: [
    'Un daño, una pérdida, un retraso o un cobro en desacuerdo se registran como incidencia dentro del envío, con fotos y una descripción. Las dos partes y el equipo de OnCargo ven lo mismo.',
    'OnCargo puede mediar y dejar constancia de lo resuelto; su intervención no reemplaza a la póliza del transportista ni a las vías legales de las partes.',
  ] },
  { id: 'pagos', h: 'Pagos', p: [
    'El pago del flete se hace entre la PYME y el transportista, fuera de la plataforma, por el medio que acuerden. En OnCargo se registran el comprobante, el pago y su confirmación, y se puede abrir una disputa.',
  ] },
];
