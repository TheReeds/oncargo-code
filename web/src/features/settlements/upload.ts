/**
 * Sube el comprobante de un cobro por el proxy `/documentos/subir` (tipos `CARRIER_INVOICE` del transportista y
 * `PAYMENT_PROOF` de la PYME) y devuelve el `documentId` que se pasa al registrar la factura o el pago.
 */
export type ProofType = 'CARRIER_INVOICE' | 'PAYMENT_PROOF';

export async function uploadProof(shipmentId: string, type: ProofType, file: File): Promise<{ id: string } | { error: string }> {
  const form = new FormData();
  form.set('shipmentId', shipmentId);
  form.set('type', type);
  form.set('file', file);
  try {
    const res = await fetch('/documentos/subir', { method: 'POST', body: form });
    const body = await res.json().catch(() => null);
    if (!res.ok) return { error: body?.error?.message ?? 'No pudimos subir el archivo. Inténtalo de nuevo.' };
    const id = body?.data?.id as string | undefined;
    return id ? { id } : { error: 'No pudimos subir el archivo. Inténtalo de nuevo.' };
  } catch {
    return { error: 'No pudimos subir el archivo. Revisa tu conexión e inténtalo de nuevo.' };
  }
}
