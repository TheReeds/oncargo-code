package pe.logyx.reputation.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;

/** Parámetros del Trust Score bayesiano (RN09). */
@ApplicationScoped
public class ReputationSettings {

    @ConfigProperty(name = "logyx.reputation.prior-mean", defaultValue = "75")
    BigDecimal priorMean;

    @ConfigProperty(name = "logyx.reputation.prior-weight", defaultValue = "5")
    int priorWeight;

    public BigDecimal priorMean() { return priorMean; }
    public int priorWeight() { return priorWeight; }
}
