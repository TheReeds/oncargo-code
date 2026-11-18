import { ShieldCheck } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';

/**
 * Sello "Verificado por OnCargo" (RN01): la organización pasó la revisión del equipo de OnCargo. **No** significa que el RUC
 * tenga un dígito verificador válido (`rucValid`): eso se comprueba al registrarse y no da sello.
 * `variant="badge"` (default) = chip con texto; `variant="icon"` = solo ícono con tooltip (listas densas).
 * Renderiza `null` si `verified` es falso. Pasa `rucVerified` de la API, nunca `rucValid`.
 */
export function VerifiedBadge({ verified = true, variant = 'badge', label = 'Verificado por OnCargo' }: { verified?: boolean | null; variant?: 'badge' | 'icon'; label?: string }) {
  if (!verified) return null;
  if (variant === 'icon')
    return (
      <Tooltip content="Verificada por OnCargo">
        <span className="inline-flex"><ShieldCheck className="size-4 text-emerald-700" role="img" aria-label={label} /></span>
      </Tooltip>
    );
  return <Badge tone="emerald"><ShieldCheck className="size-3" aria-hidden="true" />{label}</Badge>;
}
