package my.savingbuddy.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetClockTest {
    private static BudgetClock at(String isoDate) {
        return new BudgetClock(Clock.fixed(Instant.parse(isoDate + "T04:00:00Z"), ZoneId.of("Asia/Kuala_Lumpur")));
    }

    @Test
    void daysRemainingCountsToday() {
        assertThat(at("2026-08-22").daysRemainingInMonth()).isEqualTo(10);
        assertThat(at("2026-08-31").daysRemainingInMonth()).isEqualTo(1);
    }

    @Test
    void paydayBeforeTodayRollsToNextMonth() {
        assertThat(at("2026-08-22").nextPayday(25)).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(at("2026-08-26").nextPayday(25)).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(at("2026-08-25").nextPayday(25)).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void lastPaydayLooksBackwards() {
        assertThat(at("2026-08-22").lastPayday(25)).isEqualTo(LocalDate.of(2026, 7, 25));
        assertThat(at("2026-08-25").lastPayday(25)).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void paydayClampsToShortMonths() {
        assertThat(at("2026-02-10").nextPayday(31)).isEqualTo(LocalDate.of(2026, 2, 28));
    }
}
