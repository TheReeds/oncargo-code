package pe.logyx.operator.application.port.out;

import pe.logyx.operator.domain.FraudSignalPolicy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Datos crudos de las señales de fraude (2.15): SQL de solo lectura bajo las políticas {@code *_operator} (nunca
 * SystemTx). Excluye organizaciones operadoras.
 */
public interface FraudSignalReader {
    List<FraudSignalPolicy.Shared> sharedData();
    List<FraudSignalPolicy.Rate> withdrawalRates(long minSample);
    List<FraudSignalPolicy.Rate> cancellationRates(long minSample);
    List<FraudSignalPolicy.LowBids> lowBidsFromNewAccounts(OffsetDateTime createdSince, BigDecimal ratioOfSuggested);
}
