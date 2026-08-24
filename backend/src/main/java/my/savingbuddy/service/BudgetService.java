package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.*;
import my.savingbuddy.domain.*;
import my.savingbuddy.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Computes the numbers the app is built around: Safe to Spend, what each account is for,
 * goal health, and the month's bills. Everything here is derived from stored facts — nothing is cached.
 */
@Service
@Transactional(readOnly = true)
public class BudgetService {
    private final PlanRepository plans;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final BillRepository bills;
    private final GoalRepository goals;
    private final BudgetClock clock;

    public BudgetService(PlanRepository plans, AccountRepository accounts, TransactionRepository transactions,
                         BillRepository bills, GoalRepository goals, BudgetClock clock) {
        this.plans = plans;
        this.accounts = accounts;
        this.transactions = transactions;
        this.bills = bills;
        this.goals = goals;
        this.clock = clock;
    }

    public Plan plan() {
        return plans.findFirstByOrderByIdAsc().orElseThrow(() -> new NotFoundException("No budget plan has been set up"));
    }

    public Account spendingAccount() {
        return accounts.findFirstByKind(AccountKind.SPENDING)
            .orElseThrow(() -> new NotFoundException("No spending account configured"));
    }

    public List<Transaction> transactionsThisMonth() {
        YearMonth m = clock.currentMonth();
        LocalDateTime from = m.atDay(1).atStartOfDay();
        return transactions.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
            from, from.plusMonths(1));
    }

    /** Discretionary spending this month — what Safe to Spend is measured against. Bills are excluded. */
    public BigDecimal spentThisMonth() {
        return Money.scale(transactionsThisMonth().stream()
            .filter(t -> t.getKind() == TransactionKind.SPENDING)
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** Every ringgit that left the accounts this month, bills included. */
    public BigDecimal outflowsThisMonth() {
        return Money.scale(transactionsThisMonth().stream()
            .filter(t -> t.getKind().isOutflow())
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BigDecimal safeToSpend() {
        return Money.floorZero(plan().getSpendingAllowance().subtract(spentThisMonth()));
    }

    public BigDecimal dailyAllowance(BigDecimal safe) {
        return Money.divide(safe, clock.daysRemainingInMonth());
    }

    /** Month's allocated savings = sum of goal contributions. */
    public BigDecimal savedThisMonth() {
        return Money.scale(goals.findAll().stream().map(Goal::getMonthly).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BigDecimal unpaidBillsThisMonth() {
        LocalDate today = clock.today();
        return Money.scale(bills.findAll().stream()
            .filter(b -> !b.isPaidFor(today))
            .map(Bill::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** The goal that absorbs a purchase: the non-priority goal taking the largest monthly contribution. */
    public Goal flexibleGoal() {
        return goals.findAllByOrderBySortOrderAsc().stream()
            .filter(g -> !g.isPriority() && !g.isOnHold())
            .max((a, b) -> a.getMonthly().compareTo(b.getMonthly()))
            .or(() -> goals.findAllByOrderBySortOrderAsc().stream().filter(g -> !g.isPriority()).findFirst())
            .orElseThrow(() -> new NotFoundException("No flexible goal to trade off against"));
    }

    public SummaryResponse summary() {
        Plan plan = plan();
        LocalDate today = clock.today();
        YearMonth month = clock.currentMonth();
        String monthLabel = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        BigDecimal spent = spentThisMonth();
        BigDecimal safe = Money.floorZero(plan.getSpendingAllowance().subtract(spent));
        int daysRemaining = clock.daysRemainingInMonth();
        BigDecimal daily = Money.divide(safe, daysRemaining);
        BigDecimal weekly = daily.multiply(BigDecimal.valueOf(7)).min(safe);

        LocalDate nextPayday = clock.nextPayday(plan.getPayday());
        Profile profile = new Profile(
            plan.getOwnerName(),
            plan.getOwnerName().split(" ")[0],
            plan.getPayday(),
            today,
            nextPayday,
            (int) ChronoUnit.DAYS.between(today, nextPayday),
            monthLabel
        );

        BigDecimal saved = savedThisMonth();
        Savings savings = new Savings(saved, plan.getSavingsTarget(), saved.compareTo(plan.getSavingsTarget().multiply(new BigDecimal("0.75"))) >= 0);

        List<Account> accts = accounts.findAllByOrderBySortOrderAsc();
        List<Goal> goalList = goals.findAllByOrderBySortOrderAsc();
        BigDecimal unpaid = unpaidBillsThisMonth();
        BigDecimal committed = Money.scale(goalList.stream().map(Goal::getSaved).reduce(BigDecimal.ZERO, BigDecimal::add));

        List<AccountDto> accountDtos = accts.stream().map(a -> {
            BigDecimal reserved = switch (a.getKind()) {
                case BILLS -> unpaid.min(a.getBalance());
                case SAVINGS -> committed.min(a.getBalance());
                case SPENDING -> spent;
            };
            BigDecimal free = switch (a.getKind()) {
                case SPENDING -> safe;
                default -> Money.floorZero(a.getBalance().subtract(reserved));
            };
            return new AccountDto(a.getId(), a.getCode(), a.getName(), a.getKind(), Money.scale(a.getBalance()),
                reserved, free, a.getKind() == AccountKind.SAVINGS ? goalList.size() : 0);
        }).toList();

        BigDecimal billsTotal = sumByKind(accts, AccountKind.BILLS);
        BigDecimal savingsTotal = sumByKind(accts, AccountKind.SAVINGS);
        BigDecimal spendingTotal = sumByKind(accts, AccountKind.SPENDING);
        BigDecimal total = billsTotal.add(savingsTotal).add(spendingTotal);
        MoneyOverview money = new MoneyOverview(total, Money.floorZero(total.subtract(safe)), safe, billsTotal, savingsTotal, spendingTotal, accts.size());

        List<Bill> billList = bills.findAllByOrderByDueDayAsc();
        List<BillDto> billDtos = billList.stream().map(b -> toDto(b, today)).toList();
        Bills billsDto = new Bills(billDtos, billList.size(), unpaid);

        List<GoalDto> goalDtos = goalList.stream().map(g -> toDto(g, month)).toList();

        List<TransactionDto> recent = transactions.findAllByOrderByOccurredAtDescIdDesc().stream()
            .limit(4).map(BudgetService::toDto).toList();

        return new SummaryResponse(profile, new SafeToSpend(safe, Money.scale(plan.getSpendingAllowance()), spent, daysRemaining, daily, weekly),
            savings, money, accountDtos, billsDto, goalDtos, recent);
    }

    private static BigDecimal sumByKind(List<Account> accts, AccountKind kind) {
        return Money.scale(accts.stream().filter(a -> a.getKind() == kind).map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BillDto toDto(Bill b, LocalDate today) {
        YearMonth m = YearMonth.from(today);
        LocalDate due = m.atDay(Math.min(b.getDueDay(), m.lengthOfMonth()));
        return new BillDto(b.getId(), b.getName(), Money.scale(b.getAmount()), b.getDueDay(), due,
            (int) ChronoUnit.DAYS.between(today, due), b.getMethod(), b.getAccount().getName(),
            b.isPaidFor(today), b.getLastPaidOn());
    }

    public GoalDto toDto(Goal g, YearMonth month) {
        BigDecimal remaining = g.remaining();
        int monthsAtPace = g.monthsAtPace();
        int monthsToTarget = (int) Math.max(0, ChronoUnit.MONTHS.between(month, g.getTargetMonth()));
        GoalStatus status;
        BigDecimal behindBy = Money.ZERO;
        BigDecimal extraMonthly = Money.ZERO;
        if (g.isOnHold()) {
            status = GoalStatus.ON_HOLD;
        } else if (g.getDelayMonths() > 0) {
            status = GoalStatus.DELAYED;
        } else if (monthsAtPace <= monthsToTarget || remaining.signum() == 0) {
            status = GoalStatus.ON_TRACK;
        } else {
            status = GoalStatus.BEHIND;
            BigDecimal coveredAtPace = g.getMonthly().multiply(BigDecimal.valueOf(monthsToTarget));
            behindBy = Money.floorZero(remaining.subtract(coveredAtPace));
            if (monthsToTarget > 0) {
                BigDecimal required = remaining.divide(BigDecimal.valueOf(monthsToTarget), 0, RoundingMode.CEILING);
                extraMonthly = Money.floorZero(required.subtract(g.getMonthly()));
            } else {
                extraMonthly = remaining;
            }
        }
        return new GoalDto(g.getId(), g.getName(), g.getDescription(), Money.scale(g.getTarget()), Money.scale(g.getSaved()),
            Money.scale(g.getMonthly()), g.getTargetMonth().toString(), g.effectiveTargetMonth().toString(), g.isPriority(),
            g.getDelayMonths(), monthsAtPace == Integer.MAX_VALUE ? 0 : monthsAtPace, status, behindBy, extraMonthly,
            Money.ratio(g.getSaved(), g.getTarget()));
    }

    public static TransactionDto toDto(Transaction t) {
        return new TransactionDto(t.getId(), t.getName(), t.getCategory(), t.getKind(), Money.scale(t.getAmount()),
            t.getAccount().getName(), t.getOccurredAt(), t.getNote());
    }
}
