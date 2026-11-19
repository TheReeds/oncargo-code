import { useState } from 'react';
import { Download } from 'lucide-react';
import { Button } from './button';
import { toast } from './toaster';

/**
 * «Exportar CSV»: baja el archivo del proxy `/exportar/<recurso>?<filtros activos>` y, si la API lo rechaza (p. ej. más
 * de 10 000 filas), muestra el motivo en un aviso en vez de guardar un archivo con el error.
 *  <ExportButton href={`/exportar/solicitudes?${query}`} />
 */
export function ExportButton({ href, label = 'Exportar CSV', variant = 'outline', size = 'sm' }: { href: string; label?: string; variant?: 'outline' | 'ghost'; size?: 'sm' | 'default' }) {
  const [busy, setBusy] = useState(false);
  async function download() {
    setBusy(true);
    try {
      const res = await fetch(href, { credentials: 'same-origin', headers: { accept: 'text/csv' } });
      if (!res.ok) {
        toast.error('No pudimos exportar', (await res.text()) || 'Intenta nuevamente en unos segundos.');
        return;
      }
      const name = /filename="?([^";]+)"?/i.exec(res.headers.get('content-disposition') ?? '')?.[1] ?? 'exportacion.csv';
      const url = URL.createObjectURL(await res.blob());
      const a = Object.assign(document.createElement('a'), { href: url, download: name });
      document.body.append(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch {
      toast.error('No pudimos exportar', 'No pudimos conectar con el servidor. Intenta nuevamente.');
    } finally {
      setBusy(false);
    }
  }
  return <Button type="button" variant={variant} size={size} loading={busy} onClick={download}>{!busy && <Download />}{label}</Button>;
}
