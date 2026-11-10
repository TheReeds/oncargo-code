// Seguro para el cliente (islas). Complemento de `run()` en lib/actions.ts.
/**
 * Separa el mensaje en español de la API y el nombre del campo (`field`) que causó el error.
 * `const { message, field } = splitActionError(error); if (field) setErrors({ [field]: message }) else toast.error(message)`
 * Los nombres de campo de la API: ruc, email, plate, amount, vehicleId, y los parámetros de listado.
 */
export function splitActionError(error: { message: string }): { message: string; field: string | null } {
  const [message, field] = error.message.split('\u2063');
  return { message, field: field || null };
}
