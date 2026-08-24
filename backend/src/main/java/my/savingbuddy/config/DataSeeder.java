package my.savingbuddy.config;

import my.savingbuddy.domain.*;
import my.savingbuddy.repository.*;
import my.savingbuddy.service.BudgetClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Seeds a realistic demo household. Dates are relative to "today" so the demo always looks alive.
 *
 * <p>Only active under the {@code demo} profile — a real install starts empty and is configured
 * through {@code POST /api/setup}, so nobody inherits someone else's Japan Trip.
 * Run the demo with {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo}.
 */
@Component
@Profile("demo")
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PlanRepository plans;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final BillRepository bills;
    private final GoalRepository goals;
    private final MonthSummaryRepository months;
    private final ObservationRepository observations;
    private final BudgetClock clock;

    public DataSeeder(PlanRepository plans, AccountRepository accounts, TransactionRepository transactions,
                      BillRepository bills, GoalRepository goals, MonthSummaryRepository months,
                      ObservationRepository observations, BudgetClock clock) {
        this.plans = plans;
        this.accounts = accounts;
        this.transactions = transactions;
        this.bills = bills;
        this.goals = goals;
        this.months = months;
        this.observations = observations;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (plans.count() > 0) return;
        seed();
        log.info("Seeded demo household for {}", clock.today());
    }

    public void seed() {
        LocalDate today = clock.today();
        YearMonth month = clock.currentMonth();
        LocalDate monthStart = month.atDay(1);

        plans.save(new Plan("Sze Yin", "Kitaro Sdn Bhd", 25, rm(4500), rm(1200), rm(2500), rm(2000)));

        Account pb = accounts.save(new Account("PB", "Public Bank", AccountKind.BILLS, rm(6000), 1));
        Account cimb = accounts.save(new Account("CIMB", "CIMB", AccountKind.SAVINGS, rm(13700), 2));
        Account hl = accounts.save(new Account("HL", "Hong Leong Bank", AccountKind.SPENDING, rm(2000), 3));

        // Discretionary spending this month: RM574 in total.
        spend(hl, "Village Grocer", "Groceries", 42, at(today, monthStart, 0, 10, 12));
        spend(hl, "Zus Coffee", "Eating out", 26, at(today, monthStart, 0, 8, 40));
        spend(hl, "Grab", "Transport", 31, at(today, monthStart, 1, 18, 30));
        spend(hl, "Nasi Kandar Pelita", "Eating out", 38, at(today, monthStart, 2, 13, 5));
        spend(hl, "Jaya Grocer", "Groceries", 74, at(today, monthStart, 6, 19, 20));
        spend(hl, "Sushi Zanmai", "Eating out", 128, at(today, monthStart, 7, 20, 45));
        spend(hl, "Petronas", "Transport", 80, at(today, monthStart, 10, 7, 55));
        spend(hl, "Merchant's Lane", "Eating out", 52, at(today, monthStart, 13, 11, 30));
        spend(hl, "Lotus's", "Groceries", 72, at(today, monthStart, 14, 17, 10));
        spend(hl, "Grab", "Transport", 31, at(today, monthStart, 17, 9, 15));

        // Bills already paid this month: RM370.
        LocalDateTime gymPaid = at(today, monthStart, 1, 7, 0);
        LocalDateTime telcoPaid = at(today, monthStart, 12, 6, 30);
        transactions.save(new Transaction(pb, "Gym membership", "Recurring", TransactionKind.BILL, rm(200), gymPaid, null));
        transactions.save(new Transaction(pb, "Unifi & Maxis", "Recurring", TransactionKind.BILL, rm(170), telcoPaid, null));

        // Last salary.
        LocalDate lastPayday = clock.lastPayday(25);
        transactions.save(new Transaction(pb, "Salary — Kitaro Sdn Bhd", "Salary", TransactionKind.INCOME, rm(4500),
            lastPayday.atTime(9, 2), "Split: RM1,200 bills · RM2,500 savings"));

        bills.saveAll(List.of(
            new Bill("Unifi & Maxis", rm(170), 10, BillMethod.AUTO_DEBIT, pb, telcoPaid.toLocalDate()),
            new Bill("Gym membership", rm(200), 21, BillMethod.AUTO_DEBIT, pb, gymPaid.toLocalDate()),
            new Bill("PTPTN", rm(300), 25, BillMethod.AUTO_DEBIT, pb, null),
            new Bill("Car Loan", rm(350), 26, BillMethod.AUTO_DEBIT, pb, null),
            new Bill("Insurance", rm(200), 28, BillMethod.MANUAL, pb, null),
            new Bill("Utilities", rm(350), 30, BillMethod.VARIES, pb, null)
        ));

        goals.saveAll(List.of(
            new Goal("Emergency Fund", "3 months of expenses", rm(12000), rm(7200), rm(1000), month.plusMonths(9), true, 1),
            new Goal("Japan Trip", "Flights, stay and spending money", rm(8000), rm(3100), rm(700), month.plusMonths(7), false, 2),
            new Goal("New Laptop", "MacBook Air", rm(5000), rm(3200), rm(300), month.plusMonths(4), false, 3)
        ));

        // Five closed months of history. Savings rise for the last three, and again this month.
        int[] saved = {1163, 907, 1395, 1605, 1790};
        int[] eatingOut = {150, 172, 148, 165, 165};
        int[] groceries = {184, 196, 190, 202, 178};
        int[] transport = {176, 190, 184, 170, 190};
        for (int i = 0; i < 5; i++) {
            months.save(new MonthSummary(month.minusMonths(5 - i), rm(4500), rm(saved[i]),
                rm(eatingOut[i]), rm(groceries[i]), rm(transport[i]), rm(370)));
        }

        observations.saveAll(List.of(
            new Observation("Weekends cost 2.4× a weekday",
                "Mostly Friday dinners. Capping them at RM80 frees RM160 a month.", Observation.Tone.WARN, 1),
            new Observation("You never dip below RM800 spare",
                "Your savings target could safely rise to RM2,700.", Observation.Tone.GOOD, 2),
            new Observation("Gym unused for 6 weeks",
                "RM200/month. Pausing it covers a third of the Japan Trip gap.", Observation.Tone.GOOD, 3)
        ));
    }

    private void spend(Account account, String name, String category, long amount, LocalDateTime when) {
        transactions.save(new Transaction(account, name, category, TransactionKind.SPENDING, rm(amount), when, null));
    }

    /** {@code daysAgo} before today, clamped to the start of the current month so monthly totals stay intact. */
    private static LocalDateTime at(LocalDate today, LocalDate monthStart, int daysAgo, int hour, int minute) {
        LocalDate d = today.minusDays(daysAgo);
        if (d.isBefore(monthStart)) d = monthStart;
        return d.atTime(LocalTime.of(hour, minute));
    }

    private static BigDecimal rm(long v) { return BigDecimal.valueOf(v).setScale(2); }
}
