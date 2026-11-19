import { useEffect } from 'react';
import { Toaster as Sonner, toast as sonner } from 'sonner';
import { AlertCircle, CheckCircle2, Info, Loader2, TriangleAlert } from 'lucide-react';
import { ConfirmHost } from './confirm-dialog';
import { useResolvedTheme } from '@/lib/theme';
import { splitActionError } from '@/lib/action-error';
import { openRequestAccess } from '@/lib/permissions';

const FLASH_KEY = 'logyx:flash';

/**
 * Toasts de LOGYX. `toast.success('Oferta enviada')`, `toast.error(error.message)`, `toast.info(..)`.
 * Si después de la acción vas a redirigir/recargar la página, usa `flash('Solicitud publicada')` ANTES de navegar:
 * el mensaje aparece en la página siguiente.
 */
export const toast = {
  success: (message: string, description?: string) => sonner.success(message, { description }),
  // Un 403 de rol Lector (`LOGYX-AUTH-007`, el código viaja invisible en el mensaje de la action) ofrece «Pedir acceso».
  error: (message: string, description?: string) => {
    const f = splitActionError({ message });
    const action = f.code === 'LOGYX-AUTH-007' ? { label: 'Pedir acceso', onClick: () => openRequestAccess('OPERATE') } : undefined;
    return sonner.error(f.message, { description, duration: action ? 10000 : 7000, action });
  },
  info: (message: string, description?: string) => sonner.info(message, { description }),
  warning: (message: string, description?: string) => sonner.warning(message, { description }),
  promise: sonner.promise,
  dismiss: sonner.dismiss,
};

export function flash(message: string, kind: 'success' | 'info' = 'success') {
  try { sessionStorage.setItem(FLASH_KEY, JSON.stringify({ message, kind })); } catch { /* sin storage */ }
}

export function Toaster() {
  const theme = useResolvedTheme();
  useEffect(() => {
    try {
      const raw = sessionStorage.getItem(FLASH_KEY);
      if (!raw) return;
      sessionStorage.removeItem(FLASH_KEY);
      const f = JSON.parse(raw) as { message: string; kind: 'success' | 'info' };
      setTimeout(() => toast[f.kind](f.message), 150);
    } catch { /* ignorar */ }
  }, []);
  return (
    <Sonner
      theme={theme}
      position="bottom-right"
      closeButton
      duration={4500}
      icons={{
        success: <CheckCircle2 className="size-4 text-emerald-700" />,
        error: <AlertCircle className="size-4 text-red-600" />,
        info: <Info className="size-4 text-petrol-700" />,
        warning: <TriangleAlert className="size-4 text-amber-700" />,
        loading: <Loader2 className="size-4 animate-spin text-ink-500" />,
      }}
      toastOptions={{
        classNames: {
          toast: '!rounded-input !border !border-steel-200 !bg-surface !text-ink-900 !shadow-lg !font-sans !text-sm',
          description: '!text-ink-500',
          closeButton: '!border-steel-200 !bg-surface !text-ink-500',
        },
      }}
    />
  );
}

/** Montado una sola vez en Base.astro: toasts + host de confirmaciones. */
export default function Overlays() {
  return (
    <>
      <Toaster />
      <ConfirmHost />
    </>
  );
}
