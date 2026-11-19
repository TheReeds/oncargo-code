package pe.logyx.settlement.domain.model;

/** Comisión de OnCargo de un envío (2.12): pendiente, cobrada al transportista o no aplica (tasa 0 o envío previo a V18). */
public enum CommissionStatus { PENDING, COLLECTED, NOT_APPLICABLE }
