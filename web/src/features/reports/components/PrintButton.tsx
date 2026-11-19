import { Printer } from 'lucide-react';
import { Button } from '@/components/ui/button';

/** «Imprimir o guardar PDF»: abre el diálogo de impresión del navegador (no hay librería PDF; la página trae estilos de impresión). */
export default function PrintButton() {
  return <Button type="button" onClick={() => window.print()}><Printer aria-hidden="true" />Imprimir o guardar PDF</Button>;
}
