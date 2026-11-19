package pe.logyx.templates.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.templates.domain.model.RequestSchedule;
import pe.logyx.templates.domain.model.ScheduleFrequency;
import pe.logyx.templates.domain.model.ScheduleMode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecurrencePolicyTest {

    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 17);

    @Test
    void elPrimerRecojoEsElPrimerDiaElegidoCuyoAvisoNoPaso() {
        // Martes: «lunes con 2 días de anticipación» → lunes 23 (se publica el sábado 21), no el lunes de ayer.
        assertEquals(LocalDate.of(2026, 11, 23), RecurrencePolicy.firstPickup(DayOfWeek.MONDAY, 2, TUESDAY));
        // El mismo día de la semana de hoy sin anticipación: hoy.
        assertEquals(TUESDAY, RecurrencePolicy.firstPickup(DayOfWeek.TUESDAY, 0, TUESDAY));
        // Con 2 días de anticipación, un recojo pasado mañana ya se avisó tarde: se va a la semana siguiente.
        assertEquals(LocalDate.of(2026, 11, 24), RecurrencePolicy.firstPickup(DayOfWeek.TUESDAY, 2, TUESDAY));
        assertEquals(LocalDate.of(2026, 11, 20), RecurrencePolicy.firstPickup(DayOfWeek.FRIDAY, 2, TUESDAY));
    }

    @Test
    void siguienteRecojoSegunLaFrecuencia() {
        LocalDate monday = LocalDate.of(2026, 11, 23);
        assertEquals(LocalDate.of(2026, 11, 30), RecurrencePolicy.following(monday, ScheduleFrequency.WEEKLY));
        assertEquals(LocalDate.of(2026, 12, 7), RecurrencePolicy.following(monday, ScheduleFrequency.BIWEEKLY));
    }

    @Test
    void publicaALas6DeLimaConLaAnticipacionIndicada() {
        OffsetDateTime run = RecurrencePolicy.runAt(LocalDate.of(2026, 11, 23), 2);
        assertEquals("2026-11-21T06:00-05:00", run.toString());
    }

    @Test
    void validaLosParametros() {
        var e = assertThrows(BusinessException.class, () -> RecurrencePolicy.validate(DayOfWeek.MONDAY, 15, null, TUESDAY));
        assertEquals("leadDays", e.field());
        e = assertThrows(BusinessException.class, () -> RecurrencePolicy.validate(DayOfWeek.MONDAY, 2, TUESDAY.minusDays(1), TUESDAY));
        assertEquals("LOGYX-TPL-003", e.code());
        assertThrows(BusinessException.class, () -> RecurrencePolicy.validate(null, 2, null, TUESDAY));
        assertDoesNotThrow(() -> RecurrencePolicy.validate(DayOfWeek.MONDAY, 0, TUESDAY, TUESDAY));
    }

    @Test
    void feriadosNacionalesYSemanaSanta() {
        assertTrue(PeruHolidays.isHoliday(LocalDate.of(2026, 7, 28)));
        assertTrue(PeruHolidays.isHoliday(LocalDate.of(2026, 12, 25)));
        assertTrue(PeruHolidays.isHoliday(LocalDate.of(2026, 4, 2)));    // Jueves Santo 2026
        assertTrue(PeruHolidays.isHoliday(LocalDate.of(2026, 4, 3)));    // Viernes Santo 2026
        assertTrue(PeruHolidays.isHoliday(LocalDate.of(2027, 3, 25)));   // Jueves Santo 2027
        assertFalse(PeruHolidays.isHoliday(LocalDate.of(2026, 11, 23)));
        assertEquals(LocalDate.of(2026, 4, 5), PeruHolidays.easter(2026));
    }

    @Test
    void laProgramacionAvanzaTerminaYSePausaTrasFallosSeguidos() {
        var cfg = new RequestSchedule.Config(ScheduleFrequency.WEEKLY, DayOfWeek.MONDAY, 2,
                LocalDate.of(2026, 12, 7), true, ScheduleMode.PUBLISH);
        RequestSchedule s = RequestSchedule.create(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), cfg, TUESDAY);
        assertEquals(LocalDate.of(2026, 11, 23), s.nextPickupDate());
        assertFalse(s.isDue(OffsetDateTime.parse("2026-11-20T10:00:00-05:00")));
        assertTrue(s.isDue(OffsetDateTime.parse("2026-11-21T06:00:00-05:00")));

        s.advance(false, OffsetDateTime.now());
        s.advance(false, OffsetDateTime.now());
        assertFalse(s.isPaused());
        s.advance(false, OffsetDateTime.now());               // tercer fallo seguido → pausada
        assertTrue(s.isPaused());
        assertEquals(LocalDate.of(2026, 12, 14), s.nextPickupDate());
        assertTrue(s.isFinished(), "pasó la fecha de fin (7 dic)");
    }

    @Test
    void unExitoLimpiaLosFallosYReanudarSaltaLoVencido() {
        var cfg = new RequestSchedule.Config(ScheduleFrequency.BIWEEKLY, DayOfWeek.FRIDAY, 1, null, false, ScheduleMode.DRAFT);
        RequestSchedule s = RequestSchedule.create(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), cfg, TUESDAY);
        s.advance(false, OffsetDateTime.now());
        s.advance(true, OffsetDateTime.now());
        assertEquals(0, s.failures());
        s.pause();
        s.resume(LocalDate.of(2027, 1, 4));                    // meses después: no intenta recuperar lo vencido
        assertFalse(s.isPaused());
        assertFalse(s.nextPickupDate().isBefore(LocalDate.of(2027, 1, 5)));
        assertEquals(DayOfWeek.FRIDAY, s.nextPickupDate().getDayOfWeek());
    }
}
