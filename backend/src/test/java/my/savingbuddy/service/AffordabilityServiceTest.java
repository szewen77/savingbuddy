package my.savingbuddy.service;

import my.savingbuddy.domain.Goal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class AffordabilityServiceTest {
    private Goal japan() {
        return new Goal(1L, "Japan Trip", null, new BigDecimal("8000"), new BigDecimal("3100"), new BigDecimal("700"),
            YearMonth.of(2027, 3), false, 2);
    }

    @Test
    void oneContributionsWorthDelaysOneMonth() {
        var d = AffordabilityService.delayFor(japan(), new BigDecimal("399"));
        assertThat(d.months()).isEqualTo(1);
        assertThat(d.stalls()).isFalse();
    }

    @Test
    void partialContributionsRoundUp() {
        var d = AffordabilityService.delayFor(japan(), new BigDecimal("1401"));
        assertThat(d.months()).isEqualTo(3);
    }

    @Test
    void purchaseLargerThanWhatIsOwedStallsTheGoal() {
        var d = AffordabilityService.delayFor(japan(), new BigDecimal("6000")); // 9 months > 7 left
        assertThat(d.months()).isEqualTo(7);
        assertThat(d.stalls()).isTrue();
    }

    @Test
    void zeroAmountHasNoImpact() {
        var d = AffordabilityService.delayFor(japan(), BigDecimal.ZERO);
        assertThat(d.months()).isZero();
        assertThat(d.stalls()).isFalse();
    }
}
