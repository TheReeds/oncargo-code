import { Select } from './select';

/** Opciones de hora cada `step` minutos (`'06:00'`). */
export function timeOptions(step = 30) {
  const out: { value: string; label: string }[] = [];
  for (let m = 0; m < 24 * 60; m += step) {
    const v = `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
    out.push({ value: v, label: v });
  }
  return out;
}
const OPTIONS = timeOptions(30);

/** Selector de hora en formato 24 h (`'HH:mm'`, cada 30 min) para usar junto a `DatePicker`. Valor `''` = sin hora. */
export function TimeSelect({ value, onValueChange, id, invalid, disabled, className, placeholder = 'Hora', step }: { value: string; onValueChange: (v: string) => void; id?: string; invalid?: boolean; disabled?: boolean; className?: string; placeholder?: string; step?: number }) {
  return <Select id={id} value={value} onValueChange={onValueChange} options={step ? timeOptions(step) : OPTIONS} placeholder={placeholder} invalid={invalid} disabled={disabled} className={className} />;
}
