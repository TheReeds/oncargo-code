import type { ComponentType, ReactNode } from 'react';
import { Card, CardBody, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { cn } from '@/lib/utils';

const ICON = { default: 'text-ink-500', petrol: 'text-petrol-700', money: 'text-emerald-700', warn: 'text-amber-700', alert: 'text-red-700' } as const;
const COUNT = { default: 'bg-steel-100 text-ink-700', petrol: 'bg-petrol-50 text-petrol-700', money: 'bg-emerald-50 text-emerald-700', warn: 'bg-amber-50 text-amber-700', alert: 'bg-red-50 text-red-700' } as const;
export type SectionTone = keyof typeof ICON;

/**
 * Tarjeta con encabezado estándar de los paneles: título (con ícono opcional), subtítulo, contador y/o acción a la derecha.
 * Es el mismo encabezado del panel de la PYME («Requiere tu atención»): úsalo para toda tarjeta de contenido en vez de armar
 * `Card` + `CardHeader` a mano.
 *  - `icon` + `tone`: ícono lucide junto al título; el tono expresa significado (`warn` por atender, `alert` incidencia, `money` dinero).
 *  - `count`: contador en píldora (`0` no se muestra salvo `showZero`). `action`: enlace o botón a la derecha (p. ej. «Ver todas →»).
 *  - `flush`: el contenido va sin relleno (tablas, listas de filas con divisores). Sin `flush`, el contenido lleva `CardBody`.
 *  - `as`: nivel del título accesible (`h2` por defecto).
 */
export function SectionCard({ title, description, icon: Icon, tone = 'default', count, showZero, action, flush, className, bodyClassName, id, children }: {
  title: ReactNode; description?: ReactNode; icon?: ComponentType<{ size?: number; className?: string }>; tone?: SectionTone;
  count?: number | string; showZero?: boolean; action?: ReactNode; flush?: boolean; className?: string; bodyClassName?: string; id?: string; children?: ReactNode;
}) {
  const showCount = count != null && (showZero || (typeof count === 'string' ? count !== '' : count > 0));
  return (
    <Card className={cn('min-w-0 overflow-hidden', className)} id={id}>
      <CardHeader>
        <div className="min-w-0">
          <CardTitle className="flex items-center gap-2">{Icon && <Icon size={16} className={cn('shrink-0', ICON[tone])} aria-hidden="true" />}<span className="min-w-0">{title}</span></CardTitle>
          {description && <CardDescription>{description}</CardDescription>}
        </div>
        {(showCount || action) && (
          <div className="flex shrink-0 items-center gap-3">
            {showCount && <span className={cn('rounded-full px-2 py-0.5 font-mono text-xs font-semibold tabular-nums', COUNT[tone])}>{count}</span>}
            {action}
          </div>
        )}
      </CardHeader>
      {flush ? children : <CardBody className={bodyClassName}>{children}</CardBody>}
    </Card>
  );
}
