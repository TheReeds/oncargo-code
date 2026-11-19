package pe.logyx.pricing.domain.model;

import java.math.BigDecimal;

/**
 * Desglose de un precio adjudicado (todo en soles, 2 decimales):
 * <ul>
 *   <li>{@code amount}: precio adjudicado, <b>IGV incluido</b> (lo que paga la PYME);</li>
 *   <li>{@code netOfVat} + {@code vat} = {@code amount} (valor de venta y IGV de {@code vatRate});</li>
 *   <li>{@code commission} = {@code amount × commissionRate}, la retiene OnCargo al transportista;</li>
 *   <li>{@code carrierNet} = {@code amount − commission}: lo que cobra el transportista ("cobras S/ X neto").</li>
 * </ul>
 */
public record AwardedPriceBreakdown(BigDecimal amount, BigDecimal vatRate, BigDecimal netOfVat, BigDecimal vat,
                                    BigDecimal commissionRate, BigDecimal commission, BigDecimal carrierNet) {}
