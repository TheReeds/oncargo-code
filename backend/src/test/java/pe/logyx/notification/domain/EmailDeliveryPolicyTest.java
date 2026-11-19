package pe.logyx.notification.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.notification.domain.EmailDeliveryPolicy.Kind;
import pe.logyx.notification.domain.model.EmailMode;
import pe.logyx.notification.domain.model.EmailSettings;
import pe.logyx.shared.domain.BusinessException;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EmailDeliveryPolicyTest {

    private static ZonedDateTime lima(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, EmailDeliveryPolicy.LIMA);
    }

    private static EmailSettings immediate(String from, String to) {
        return new EmailSettings(true, EmailMode.IMMEDIATE, 7, from == null ? null : LocalTime.parse(from),
                to == null ? null : LocalTime.parse(to), null);
    }

    private static EmailSettings daily(int hour, String from, String to) {
        return new EmailSettings(true, EmailMode.DAILY, hour, from == null ? null : LocalTime.parse(from),
                to == null ? null : LocalTime.parse(to), null);
    }

    @Test
    void sinPreferenciasSaleAlMomentoYConElCorreoApagadoNoSale() {
        assertEquals(Kind.NOW, EmailDeliveryPolicy.decide(null, lima(2026, 11, 19, 10, 0)).kind());
        assertEquals(Kind.NOW, EmailDeliveryPolicy.decide(EmailSettings.DEFAULT, lima(2026, 11, 19, 3, 0)).kind());
        var off = new EmailSettings(false, EmailMode.DAILY, 7, LocalTime.of(22, 0), LocalTime.of(7, 0), null);
        assertEquals(Kind.SKIP, EmailDeliveryPolicy.decide(off, lima(2026, 11, 19, 10, 0)).kind());
    }

    @Test
    void elSilencioQueCruzaLaMedianocheEsperaHastaLaHoraDeFin() {
        var s = immediate("22:00", "07:00");
        // De noche: hasta mañana a las 07:00
        var night = EmailDeliveryPolicy.decide(s, lima(2026, 11, 19, 23, 30));
        assertEquals(Kind.DEFER, night.kind());
        assertEquals(EmailDeliveryPolicy.QUIET, night.reason());
        assertEquals(lima(2026, 11, 20, 7, 0), night.sendAfter());
        // Justo a las 22:00 ya es silencio; de madrugada termina el mismo día
        assertEquals(lima(2026, 11, 20, 7, 0), EmailDeliveryPolicy.decide(s, lima(2026, 11, 19, 22, 0)).sendAfter());
        assertEquals(lima(2026, 11, 20, 7, 0), EmailDeliveryPolicy.decide(s, lima(2026, 11, 20, 0, 0)).sendAfter());
        assertEquals(lima(2026, 11, 20, 7, 0), EmailDeliveryPolicy.decide(s, lima(2026, 11, 20, 6, 59)).sendAfter());
        // A las 07:00 en punto y de día sale al momento
        assertEquals(Kind.NOW, EmailDeliveryPolicy.decide(s, lima(2026, 11, 20, 7, 0)).kind());
        assertEquals(Kind.NOW, EmailDeliveryPolicy.decide(s, lima(2026, 11, 20, 13, 0)).kind());
    }

    @Test
    void elSilencioDelMismoDiaYElCambioDeMesYAnio() {
        var s = immediate("13:00", "15:00");
        assertEquals(Kind.NOW, EmailDeliveryPolicy.decide(s, lima(2026, 11, 19, 12, 59)).kind());
        assertEquals(lima(2026, 11, 19, 15, 0), EmailDeliveryPolicy.decide(s, lima(2026, 11, 19, 13, 0)).sendAfter());
        assertEquals(Kind.NOW, EmailDeliveryPolicy.decide(s, lima(2026, 11, 19, 15, 0)).kind());
        // 31 dic de noche → 1 ene del año siguiente
        var night = immediate("21:00", "06:30");
        assertEquals(lima(2027, 1, 1, 6, 30), EmailDeliveryPolicy.decide(night, lima(2026, 12, 31, 23, 59)).sendAfter());
    }

    @Test
    void laHoraSeLeeEnLimaAunqueLleguePorUtc() {
        var s = immediate("22:00", "07:00");
        // 04:00 UTC = 23:00 de Lima (UTC−5 todo el año, sin horario de verano)
        var utc = ZonedDateTime.of(2026, 11, 20, 4, 0, 0, 0, ZoneOffset.UTC);
        var d = EmailDeliveryPolicy.decide(s, utc);
        assertEquals(Kind.DEFER, d.kind());
        assertEquals(lima(2026, 11, 20, 7, 0).toInstant(), d.sendAfter().toInstant());
        // Mismo desfase en enero y en julio: Lima no cambia la hora
        assertEquals(-5 * 3600, lima(2027, 1, 15, 12, 0).getOffset().getTotalSeconds());
        assertEquals(-5 * 3600, lima(2027, 7, 15, 12, 0).getOffset().getTotalSeconds());
    }

    @Test
    void elResumenDiarioVaALaProximaHoraElegida() {
        var s = daily(7, null, null);
        var before = EmailDeliveryPolicy.decide(s, lima(2026, 11, 19, 6, 0));
        assertEquals(Kind.DEFER, before.kind());
        assertEquals(EmailDeliveryPolicy.DIGEST, before.reason());
        assertEquals(lima(2026, 11, 19, 7, 0), before.sendAfter());
        assertEquals(lima(2026, 11, 20, 7, 0), EmailDeliveryPolicy.decide(s, lima(2026, 11, 19, 7, 0)).sendAfter());
        assertEquals(lima(2026, 11, 20, 7, 0), EmailDeliveryPolicy.decide(s, lima(2026, 11, 19, 18, 0)).sendAfter());
        assertEquals(lima(2026, 12, 1, 0, 0), EmailDeliveryPolicy.decide(daily(0, null, null), lima(2026, 11, 30, 23, 0)).sendAfter());
    }

    @Test
    void siElResumenCaeEnElSilencioEsperaAQueTermine() {
        var s = daily(6, "22:00", "08:00");
        var d = EmailDeliveryPolicy.decide(s, lima(2026, 11, 19, 12, 0));
        assertEquals(EmailDeliveryPolicy.DIGEST, d.reason());
        assertEquals(lima(2026, 11, 20, 8, 0), d.sendAfter());
        // Resumen fuera del silencio: a su hora aunque ahora sea de noche
        assertEquals(lima(2026, 11, 20, 9, 0), EmailDeliveryPolicy.decide(daily(9, "22:00", "08:00"),
                lima(2026, 11, 19, 23, 0)).sendAfter());
    }

    @Test
    void validaElHorarioDeSilencio() {
        var only = new EmailSettings(true, EmailMode.IMMEDIATE, 7, LocalTime.of(22, 0), null, null);
        var ex = assertThrows(BusinessException.class, () -> EmailDeliveryPolicy.validate(only));
        assertEquals("LOGYX-NTF-004", ex.code());
        assertEquals("quietTo", ex.field());
        var zero = immediate("22:00", "22:00");
        assertEquals("LOGYX-NTF-004", assertThrows(BusinessException.class, () -> EmailDeliveryPolicy.validate(zero)).code());
        var hour = new EmailSettings(true, EmailMode.DAILY, 24, null, null, null);
        assertEquals("LOGYX-NTF-005", assertThrows(BusinessException.class, () -> EmailDeliveryPolicy.validate(hour)).code());
        assertDoesNotThrow(() -> EmailDeliveryPolicy.validate(immediate("22:00", "07:00")));
        assertFalse(EmailDeliveryPolicy.inQuiet(LocalTime.NOON, LocalTime.NOON, LocalTime.NOON));
    }
}
