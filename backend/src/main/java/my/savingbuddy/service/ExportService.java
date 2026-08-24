package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.*;
import my.savingbuddy.domain.Plan;
import my.savingbuddy.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Dumps the entire local database as JSON. A local-only app has no server-side
 * backup, so this is both the disaster-recovery path and the guarantee that the
 * data can leave the app if its owner ever wants it elsewhere.
 */
@Service
@Transactional(readOnly = true)
public class ExportService {
    /** Bump alongside a Flyway migration that changes the exported shape. */
    static final int SCHEMA_VERSION = 1;

    private final PlanRepository plans;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final BillRepository bills;
    private final GoalRepository goals;
    private final MonthSummaryRepository months;
    private final ObservationRepository observations;
    private final SavingPlanRepository savingPlans;

    public ExportService(PlanRepository plans, AccountRepository accounts, TransactionRepository transactions,
                         BillRepository bills, GoalRepository goals, MonthSummaryRepository months,
                         ObservationRepository observations, SavingPlanRepository savingPlans) {
        this.plans = plans;
        this.accounts = accounts;
        this.transactions = transactions;
        this.bills = bills;
        this.goals = goals;
        this.months = months;
        this.observations = observations;
        this.savingPlans = savingPlans;
    }

    public ExportBundle export() {
        Plan p = plans.findFirstByOrderByIdAsc().orElse(null);

        return new ExportBundle(
            "savingbuddy",
            SCHEMA_VERSION,
            Instant.now(),
            p == null ? null : new ExportPlan(p.getOwnerName(), p.getEmployer(), p.getPayday(), p.getSalary(),
                p.getBillsAllocation(), p.getSavingsTarget(), p.getSpendingAllowance()),
            accounts.findAllByOrderBySortOrderAsc().stream()
                .map(a -> new ExportAccount(a.getId(), a.getCode(), a.getName(), a.getKind(), a.getBalance(), a.getSortOrder()))
                .toList(),
            transactions.findAllByOrderByOccurredAtDescIdDesc().stream()
                .map(t -> new ExportTransaction(t.getId(), t.getAccount().getId(), t.getAccount().getName(), t.getName(),
                    t.getCategory(), t.getKind(), t.getAmount(), t.getOccurredAt(), t.getNote()))
                .toList(),
            bills.findAllByOrderByDueDayAsc().stream()
                .map(b -> new ExportBill(b.getId(), b.getAccount().getId(), b.getName(), b.getAmount(), b.getDueDay(),
                    b.getMethod(), b.getLastPaidOn()))
                .toList(),
            goals.findAllByOrderBySortOrderAsc().stream()
                .map(g -> new ExportGoal(g.getId(), g.getName(), g.getDescription(), g.getTarget(), g.getSaved(),
                    g.getMonthly(), g.getTargetMonth().toString(), g.isPriority(), g.getDelayMonths(), g.getSortOrder()))
                .toList(),
            months.findAllByOrderByMonthAsc().stream()
                .map(m -> new ExportMonth(m.getMonth().toString(), m.getIncome(), m.getSaved(), m.getEatingOut(),
                    m.getGroceries(), m.getTransport(), m.getOther()))
                .toList(),
            observations.findAllByOrderBySortOrderAsc().stream()
                .map(o -> new ObservationDto(o.getId(), o.getTitle(), o.getBody(), o.getTone().name()))
                .toList(),
            savingPlans.findAll().stream()
                .map(s -> new SavingPlanDto(s.getId(), s.getTotalAmount(), s.getWeeks(), s.getWeeklyAmount(), s.getCreatedAt()))
                .toList()
        );
    }

    /** Filename for a downloaded export, e.g. savingbuddy-2026-08-23.json */
    public static String filename(java.time.LocalDate today) {
        return "savingbuddy-" + today + ".json";
    }

    /** Convenience for callers that only need the list sizes (used by the backup log line). */
    public List<Integer> counts() {
        return List.of((int) accounts.count(), (int) transactions.count(), (int) bills.count(), (int) goals.count());
    }
}
