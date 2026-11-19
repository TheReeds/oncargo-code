import { PRIVACY_EMAIL, SUPPORT_EMAIL } from '@/lib/contact';

/**
 * BORRADOR de los Términos y condiciones de OnCargo (decisión del dueño, 2026-11-17): pendiente de revisión legal.
 * Cada cambio de texto debe subir `logyx.terms.version` en el backend; al registrarse se guarda la versión aceptada.
 */
export type TermsSection = { id: string; h: string; p: string[] };

export const TERMS_SECTIONS: TermsSection[] = [
  { id: 'quienes-somos', h: 'Quiénes somos y qué hacemos', p: [
    'OnCargo es una plataforma tecnológica que conecta a empresas que necesitan mover carga (PYMEs) con transportistas del corredor Lima – Sierra Sur. Facilitamos la publicación de cargas, la subasta de ofertas, el seguimiento del envío y el registro de lo acordado.',
    'OnCargo no es transportista ni asegurador: el servicio de transporte lo presta el transportista que la PYME elige. Estos términos regulan el uso de la plataforma; el contrato de transporte de cada envío se forma entre la PYME y el transportista cuando la PYME acepta una oferta.',
  ] },
  { id: 'cuentas', h: 'Cuentas y verificación', p: [
    'Para usar la plataforma registras a tu empresa con su RUC y un usuario administrador. Declaras que los datos son verdaderos, que tienes facultades para obligar a tu empresa y que mantendrás tu contraseña en reserva.',
    'El equipo de OnCargo revisa cada empresa antes de habilitarla para publicar, ofertar o reservar. Podemos rechazar o suspender una cuenta que incumpla estos términos, use datos falsos o ponga en riesgo a otros usuarios. Te explicamos el motivo y a quién escribir para revisar tu caso.',
  ] },
  { id: 'subasta', h: 'Cómo funcionan la subasta y el envío', p: [
    'La PYME publica una carga con su ruta, peso, tipo y fecha de recojo. La plataforma calcula un precio sugerido; ninguna oferta puede ser menor al 75 % de ese precio. La subasta dura hasta 20 minutos o hasta recibir 5 ofertas, lo que ocurra primero.',
    'Al aceptar una oferta, las demás se rechazan y se genera el envío, que el conductor del transportista ejecuta parada por parada con evidencia de recojo y entrega. Una oferta aceptada compromete a ambas partes; las cancelaciones y reasignaciones se hacen dentro de la plataforma y quedan registradas.',
  ] },
  { id: 'precios', h: 'Precios, IGV y comisión', p: [
    'El precio adjudicado es el monto final de la oferta aceptada e incluye el IGV (18 %); en la plataforma lo ves desglosado. La PYME no paga recargos de OnCargo sobre ese precio.',
    'OnCargo cobra al transportista una comisión, un porcentaje del precio adjudicado. La tasa vigente se publica en la página de precios y se guarda en cada envío al adjudicarlo: un cambio posterior de la tasa no altera los envíos ya adjudicados. El transportista ve el monto neto que cobra antes de enviar su oferta.',
  ] },
  { id: 'responsabilidad', h: 'Responsabilidad por la carga', p: [
    'El transportista es responsable de la carga desde que la recibe hasta que la entrega, de acuerdo con su póliza de seguro de transporte, la normativa de transporte aplicable y lo acordado en el envío. Es quien debe mantener vigentes su licencia, seguro, documentos del vehículo y autorizaciones.',
    'La PYME declara con veracidad la naturaleza, el peso, el volumen y, cuando corresponda, el valor de la carga (valor declarado), así como si es frágil, perecible o peligrosa. Una declaración falsa o incompleta puede liberar al transportista y es responsabilidad de la PYME.',
    'OnCargo ofrece la plataforma y el registro de evidencias; no custodia la carga ni responde por pérdidas, daños o retrasos del transporte, salvo en los casos en que la ley no permita excluirlo. Los reclamos se registran como incidencias dentro de la plataforma y el equipo de OnCargo puede mediar, sin que su intervención reemplace a la póliza del transportista ni a las vías legales de las partes.',
  ] },
  { id: 'conducta', h: 'Uso aceptable', p: [
    'Antes de adjudicar no se comparten teléfonos, correos, enlaces ni usuarios de redes en ofertas, notas o chat: la plataforma los bloquea para que los acuerdos queden registrados. Después de adjudicar, el envío muestra los contactos de recojo y entrega solo a las partes y al conductor asignado.',
    'No puedes usar la plataforma para actividades ilícitas, transportar mercancía prohibida, manipular ofertas o reseñas, ni acceder a datos de otras empresas. Podemos revisar las ofertas y conversaciones de una solicitud para resolver una disputa; cada consulta queda registrada.',
  ] },
  { id: 'datos', h: 'Datos personales', p: [
    `Tratamos tus datos conforme a la Ley N.° 29733 y a nuestra política de privacidad. Aceptas esa política al registrarte y puedes ejercer tus derechos ARCO escribiendo a ${PRIVACY_EMAIL}.`,
  ] },
  { id: 'cambios', h: 'Cambios en estos términos', p: [
    'Cada versión de estos términos tiene un identificador y la fecha en que la aceptaste queda registrada con tu cuenta. Si cambiamos el texto, te lo avisaremos y te pediremos aceptar la nueva versión para seguir operando.',
  ] },
  { id: 'ley', h: 'Ley aplicable y contacto', p: [
    `Estos términos se rigen por las leyes de la República del Perú. Para dudas sobre ellos o sobre tu cuenta, escribe a ${SUPPORT_EMAIL}.`,
  ] },
];
