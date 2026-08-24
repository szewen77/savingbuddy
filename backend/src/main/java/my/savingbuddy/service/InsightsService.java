package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.*;
import my.savingbuddy.domain.MonthSummary;
import my.savingbuddy.domain.Plan;
import my.savingbuddy.domain.Transaction;
import my.savingbuddy.repository.MonthSummaryRepository;
import my.savingbuddy.repository.ObservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
public class InsightsService {
    static final int HISTORY_MONTHS = 5;
    static final List<String> TRACKED = List.of("Eating out", "Groceries", "Transport");
    static final String EVERYTHING_ELSE = "Everything else";

    private final MonthSummaryRepository history;
    private final ObservationRepository observations;
    private final BudgetService budget;
    private final BudgetClock clock;

    public InsightsService(MonthSummaryRepository history, ObservationRepository observations, BudgetService budget, BudgetClock clock) {
        this.history = history;
        this.observations = observations;
        this.budget = budget;
        this.clock = clock;
    }

    public InsightsResponse insights() {
        Plan plan = budget.plan();
        YearMonth current = clock.currentMonth();
        YearMonth from = current.minusMonths(HISTORY_MONTHS);

        List<MonthSummary> past = history.findAllByOrderByMonthAsc().stream()
            .filter(m -> !m.getMonth().isBefore(from) && m.getMonth().isBefore(current)).toList();

        BigDecimal savedNow = budget.savedThisMonth();
        List<MonthPoint> months = new ArrayList<>();
        for (MonthSummary m : past) months.add(point(m.getMonth(), m.getSaved(), m.getIncome(), false));
        months.add(point(current, savedNow, plan.getSalary(), true));

        int streak = 0;
        for (int i = months.size() - 1; i > 0; i--) {
            if (months.get(i).saved().compareTo(months.get(i - 1).saved()) > 0) streak++; else break;
        }

        // Current month by category, live from transactions.
        Map<String, BigDecimal> byCat = new LinkedHashMap<>();
        TRACKED.forEach(c -> byCat.put(c, BigDecimal.ZERO));
        byCat.put(EVERYTHING_ELSE, BigDecimal.ZERO);
        for (Transaction t : budget.transactionsThisMonth()) {
            if (!t.getKind().isOutflow()) continue;
            String key = TRACKED.contains(t.getCategory()) ? t.getCategory() : EVERYTHING_ELSE;
            byCat.merge(key, t.getAmount(), BigDecimal::add);
        }
        BigDecimal spent = budget.outflowsThisMonth();

        Map<String, Function<MonthSummary, BigDecimal>> accessors = Map.of(
            "Eating out", MonthSummary::getEatingOut,
            "Groceries", MonthSummary::getGroceries,
            "Transport", MonthSummary::getTransport,
            EVERYTHING_ELSE, MonthSummary::getOther
        );

        List<CategoryDto> categories = byCat.entrySet().stream().map(e -> {
            BigDecimal amount = Money.scale(e.getValue());
            BigDecimal avg = average(past, accessors.get(e.getKey()));
            return new CategoryDto(e.getKey(), amount, Money.ratio(amount, spent), avg, Money.scale(amount.subtract(avg)));
        }).toList();

        List<ObservationDto> obs = observations.findAllByOrderBySortOrderAsc().stream()
            .map(o -> new ObservationDto(o.getId(), o.getTitle(), o.getBody(), o.getTone().name())).toList();

        return new InsightsResponse(Money.ratio(savedNow, plan.getSalary()), streak, spent,
            current.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH), months, categories, obs);
    }

    private static BigDecimal average(List<MonthSummary> past, Function<MonthSummary, BigDecimal> f) {
        if (past.isEmpty()) return Money.ZERO;
        BigDecimal sum = past.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(past.size()), 2, RoundingMode.HALF_UP);
    }

    private static MonthPoint point(YearMonth m, BigDecimal saved, BigDecimal income, boolean current) {
        return new MonthPoint(m.toString(), m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            Money.scale(saved), Money.scale(income), current);
    }
}
