package pe.logyx.pricing.domain.model;

import java.math.BigDecimal;

/**
 * Desglose de un precio adjudicado (todo en soles, 2 decimales). Decisión del dueño (2026-11-18): la comisión se
 * calcula sobre el valor de venta sin IGV y lleva su propio IGV.
 * <ul>
 *   <li>{@code amount}: precio adjudicado, <b>IGV incluido</b> (lo que paga la PYME);</li>
 *   <li>{@code netOfVat} + {@code vat} = {@code amount} (valor de venta y IGV de {@code vatRate});</li>
 *   <li>{@code commission} = {@code netOfVat × commissionRate}, sin IGV; la retiene OnCargo al transportista;</li>
 *   <li>{@code commissionVat} = {@code commission × vatRate}: el IGV de la factura de la comisión;</li>
 *   <li>{@code carrierNet} = {@code amount − commission − commissionVat}: lo que cobra el transportista
 *       ("cobras S/ X neto").</li>
 * </ul>
 */
public record AwardedPriceBreakdown(BigDecimal amount, BigDecimal vatRate, BigDecimal netOfVat, BigDecimal vat,
                                    BigDecimal commissionRate, BigDecimal commission, BigDecimal commissionVat,
                                    BigDecimal carrierNet) {}
