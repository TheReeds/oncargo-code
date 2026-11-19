package pe.logyx.operator.domain;

/**
 * Tipo de señal de fraude del panel «Señales» del operador (roadmap 2.15). Enum con nombre propio (schema estable).
 * {@code SHARED_*}: el mismo dato en dos o más organizaciones; {@code HIGH_*}: tasa por encima del umbral con una
 * muestra mínima; {@code LOW_BIDS_NEW_ACCOUNT}: cuenta nueva con muchas ofertas muy por debajo del sugerido.
 */
public enum FraudSignalKind {
    SHARED_PHONE, SHARED_PLATE, SHARED_LICENSE, SHARED_SIGNUP_IP, HIGH_WITHDRAWAL_RATE, HIGH_CANCELLATION_RATE,
    LOW_BIDS_NEW_ACCOUNT
}
