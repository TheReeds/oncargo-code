package pe.logyx.shared.outbox;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Calificador de los eventos que entrega el {@link OutboxDispatcher}: {@code void on(@Observes @Outboxed X e)}.
 *
 * <p>El observador corre dentro de la transacción de sistema del despacho (datasource privilegiado). Si lanza
 * una excepción, todo lo que hizo se revierte y el evento se reintenta más tarde (al menos una vez): el
 * observador debe ser idempotente o atómico con el despacho (escribir solo en esa transacción).
 */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
public @interface Outboxed {

    final class Literal extends jakarta.enterprise.util.AnnotationLiteral<Outboxed> implements Outboxed {
        public static final Literal INSTANCE = new Literal();
    }
}
