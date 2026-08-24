package my.savingbuddy.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** Single source of "now" so calculations are testable with a fixed clock. */
@Component
public class BudgetClock {
    private final Clock clock;

    public BudgetClock(Clock clock) { this.clock = clock; }

    public LocalDate today() { return LocalDate.now(clock); }
    public LocalDateTime now() { return LocalDateTime.now(clock); }
    public YearMonth currentMonth() { return YearMonth.from(today()); }

    /** Days left in the calendar month, counting today. */
    public int daysRemainingInMonth() {
        LocalDate t = today();
        return t.lengthOfMonth() - t.getDayOfMonth() + 1;
    }

    public LocalDate nextPayday(int payday) {
        LocalDate t = today();
        LocalDate thisMonth = t.withDayOfMonth(Math.min(payday, t.lengthOfMonth()));
        if (!thisMonth.isBefore(t)) return thisMonth;
        YearMonth next = YearMonth.from(t).plusMonths(1);
        return next.atDay(Math.min(payday, next.lengthOfMonth()));
    }

    public LocalDate lastPayday(int payday) {
        LocalDate t = today();
        LocalDate thisMonth = t.withDayOfMonth(Math.min(payday, t.lengthOfMonth()));
        if (!thisMonth.isAfter(t)) return thisMonth;
        YearMonth prev = YearMonth.from(t).minusMonths(1);
        return prev.atDay(Math.min(payday, prev.lengthOfMonth()));
    }
}
