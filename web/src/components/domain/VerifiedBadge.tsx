import { ShieldCheck } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';

/**
 * Sello de RUC verificado. `variant="badge"` (default) = chip con texto; `variant="icon"` = solo ícono con tooltip
 * (para listas densas). Renderiza `null` si `verified` es falso.
 */
export function VerifiedBadge({ verified = true, variant = 'badge', label = 'Empresa verificada' }: { verified?: boolean | null; variant?: 'badge' | 'icon'; label?: string }) {
  if (!verified) return null;
  if (variant === 'icon')
    return (
      <Tooltip content="RUC verificado">
        <span className="inline-flex"><ShieldCheck className="size-4 text-emerald-700" role="img" aria-label={label} /></span>
      </Tooltip>
    );
  return <Badge tone="emerald"><ShieldCheck className="size-3" aria-hidden="true" />{label}</Badge>;
}
