package my.savingbuddy.service;

import my.savingbuddy.domain.Goal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class GoalTest {
    private Goal japan() {
        return new Goal("Japan Trip", null, new BigDecimal("8000"), new BigDecimal("3100"), new BigDecimal("700"),
            YearMonth.of(2027, 3), false, 2);
    }

    @Test
    void monthsAtPaceRoundsUp() {
        assertThat(japan().monthsAtPace()).isEqualTo(7); // 4900 / 700
    }

    @Test
    void delayingPushesTargetMonthBack() {
        Goal g = japan();
        g.delayBy(2);
        assertThat(g.getDelayMonths()).isEqualTo(2);
        assertThat(g.effectiveTargetMonth()).isEqualTo(YearMonth.of(2027, 5));
        assertThat(g.isOnHold()).isFalse();
        assertThat(g.delayRoom()).isEqualTo(5);
    }

    @Test
    void delayIsCappedAtMonthsRemainingAndPutsGoalOnHold() {
        Goal g = japan();
        g.delayBy(99);
        assertThat(g.getDelayMonths()).isEqualTo(7);
        assertThat(g.isOnHold()).isTrue();
        assertThat(g.delayRoom()).isZero();
    }
}
