package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.*;
import my.savingbuddy.domain.Goal;
import my.savingbuddy.domain.Plan;
import my.savingbuddy.domain.SavingPlan;
import my.savingbuddy.domain.Transaction;
import my.savingbuddy.repository.SavingPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * "Can I afford this?" — shows what a purchase does to the month before it happens,
 * then either records it (and slows the flexible goal) or turns it into a short saving plan.
 */
@Service
public class AffordabilityService {
    static final int WAIT_WEEKS = 3;

    private final BudgetService budget;
    private final TransactionService transactions;
    private final SavingPlanRepository savingPlans;
    private final BudgetClock clock;

    public AffordabilityService(BudgetService budget, TransactionService transactions,
                                SavingPlanRepository savingPlans, BudgetClock clock) {
        this.budget = budget;
        this.transactions = transactions;
        this.savingPlans = savingPlans;
        this.clock = clock;
    }

    /** Pure calculation of the impact; nothing is written. */
    @Transactional(readOnly = true)
    public AffordPreview preview(Long userId, BigDecimal rawAmount) {
        BigDecimal amount = Money.scale(rawAmount);
        Plan plan = budget.plan(userId);
        BigDecimal safeBefore = budget.safeToSpend(userId);
        BigDecimal safeAfterRaw = safeBefore.subtract(amount);
        BigDecimal safeAfter = Money.floorZero(safeAfterRaw);
        BigDecimal shortfall = Money.floorZero(safeAfterRaw.negate());
        Verdict verdict = safeAfterRaw.signum() < 0 ? Verdict.NO : Verdict.YES;

        BigDecimal savedBefore = budget.savedThisMonth(userId);
        BigDecimal savedAfter = Money.floorZero(savedBefore.subtract(amount));

        int days = clock.daysRemainingInMonth();
        BigDecimal dailyBefore = Money.divide(safeBefore, days);
        BigDecimal dailyAfter = Money.divide(safeAfter, days);

        // Null when the user has no goal to trade off against — the verdict and
        // every safe-to-spend figure above stay meaningful without one.
        GoalImpact impact = budget.flexibleGoal(userId).map(goal -> {
            Delay delay = delayFor(goal, amount);
            return new GoalImpact(goal.getId(), goal.getName(), Money.scale(goal.getSaved()), Money.scale(goal.getTarget()),
                Money.ratio(goal.getSaved(), goal.getTarget()), goal.effectiveTargetMonth().toString(),
                delay.stalls() ? null : goal.effectiveTargetMonth().plusMonths(delay.months()).toString(),
                delay.months(), delay.stalls());
        }).orElse(null);

        WaitPlan wait = new WaitPlan(WAIT_WEEKS, Money.divide(amount, WAIT_WEEKS));

        return new AffordPreview(amount, verdict, safeBefore, safeAfter, shortfall, savedBefore, savedAfter,
            Money.scale(plan.getSavingsTarget()), dailyBefore, dailyAfter, days, impact, wait);
    }

    @Transactional
    public BuyResponse buy(Long userId, BigDecimal rawAmount) {
        BigDecimal amount = Money.scale(rawAmount);
        Optional<Goal> flexible = budget.flexibleGoal(userId);

        Transaction t = transactions.recordPurchase(userId, amount);

        // With no goal there is nothing to push back — the purchase is still
        // recorded and the spending figures still move.
        GoalDto goalDto = flexible.map(goal -> {
            Delay delay = delayFor(goal, amount);
            goal.delayBy(delay.stalls() ? goal.delayRoom() : delay.months());
            return budget.toDto(goal, clock.currentMonth());
        }).orElse(null);

        BigDecimal safe = budget.safeToSpend(userId);
        return new BuyResponse(BudgetService.toDto(t), safe, budget.dailyAllowance(safe), goalDto);
    }

    @Transactional
    public SavingPlanDto waitAndSave(Long userId, BigDecimal rawAmount) {
        BigDecimal amount = Money.scale(rawAmount);
        SavingPlan plan = savingPlans.save(new SavingPlan(userId, amount, WAIT_WEEKS, Money.divide(amount, WAIT_WEEKS), clock.now()));
        return new SavingPlanDto(plan.getId(), plan.getTotalAmount(), plan.getWeeks(), plan.getWeeklyAmount(), plan.getCreatedAt());
    }

    record Delay(int months, boolean stalls) {}

    /** A purchase is funded by skipping contributions to the flexible goal: one month per contribution's worth. */
    static Delay delayFor(Goal goal, BigDecimal amount) {
        if (amount.signum() <= 0 || goal.getMonthly().signum() <= 0) return new Delay(0, false);
        int raw = amount.divide(goal.getMonthly(), 0, RoundingMode.CEILING).intValue();
        int room = goal.delayRoom();
        return new Delay(Math.min(raw, room), raw > room);
    }
}
