import { useEffect, useRef, useState } from 'react';
import { Check, Copy } from 'lucide-react';
import { Button } from './button';
import { cn } from '@/lib/utils';

/** Último mensaje anunciado (VoiceOver no repite un mensaje idéntico del mismo `role="status"`). */
let lastMessage = '';

function legacyCopy(text: string): boolean {
  const field = Object.assign(document.createElement('textarea'), { value: text });
  field.setAttribute('readonly', '');
  field.style.cssText = 'position:fixed;inset-block-start:0;opacity:0';
  document.body.append(field);
  field.select();
  try { return document.execCommand('copy'); } finally { field.remove(); }
}

/**
 * Copia un texto al portapapeles (Async Clipboard; respaldo `execCommand` si el navegador lo niega). El mensaje
 * «Copiado» / «No se pudo copiar» es a la vez la confirmación visible y la región viva (`role="status"`, fuera del
 * botón), y el ícono cambia: no depende solo del color. No se deshabilita mientras muestra el aviso (copiar es idempotente).
 *  <CopyButton text={url} label="Copiar enlace" />
 */
export function CopyButton({ text, label = 'Copiar', variant = 'outline', size = 'sm', className }: { text: string; label?: string; variant?: 'outline' | 'default' | 'ghost'; size?: 'sm' | 'default'; className?: string }) {
  const [state, setState] = useState<'idle' | 'ok' | 'fail'>('idle');
  const [message, setMessage] = useState('');
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);

  async function copy() {
    let ok = true;
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      ok = legacyCopy(text);
    }
    clearTimeout(timer.current);
    let msg = ok ? 'Copiado' : 'No se pudo copiar: selecciona el texto y cópialo';
    if (msg === lastMessage) msg += ' ';
    lastMessage = msg;
    setState(ok ? 'ok' : 'fail');
    setMessage('');
    requestAnimationFrame(() => setMessage(msg));
    timer.current = setTimeout(() => { setState('idle'); setMessage(''); }, 2500);
  }

  return (
    <span className={cn('relative inline-flex items-center gap-2', className)}>
      <Button type="button" variant={variant} size={size} onClick={copy} data-copied={state === 'ok' || undefined}>
        {state === 'ok' ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
        {label}
      </Button>
      <span role="status" className={cn('text-xs transition-opacity', message ? 'opacity-100' : 'opacity-0', state === 'fail' ? 'text-red-600' : 'text-emerald-700')}>{message}</span>
    </span>
  );
}
