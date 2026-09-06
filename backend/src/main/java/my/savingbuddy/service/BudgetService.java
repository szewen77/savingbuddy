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
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public Plan plan(Long userId) {
        return plans.findByUserId(userId).orElseThrow(() -> new NotFoundException("No budget plan has been set up"));
    }

    public Account spendingAccount(Long userId) {
        return accounts.findFirstByUserIdAndKind(userId, AccountKind.SPENDING)
            .orElseThrow(() -> new NotFoundException("No spending account configured"));
    }

    public List<Transaction> transactionsThisMonth(Long userId) {
        YearMonth m = clock.currentMonth();
        LocalDateTime from = m.atDay(1).atStartOfDay();
        return transactions.findAllByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
            userId, from, from.plusMonths(1));
    }

    /** Discretionary spending this month — what Safe to Spend is measured against. Bills are excluded. */
    public BigDecimal spentThisMonth(Long userId) {
        return Money.scale(sumSpending(transactionsThisMonth(userId).stream()));
    }

    /** The discretionary total of a stream of transactions. Bills and income are excluded. */
    private static BigDecimal sumSpending(Stream<Transaction> transactions) {
        return transactions
            .filter(t -> t.getKind() == TransactionKind.SPENDING)
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Every ringgit that left the accounts this month, bills included. */
    public BigDecimal outflowsThisMonth(Long userId) {
        return Money.scale(transactionsThisMonth(userId).stream()
            .filter(t -> t.getKind().isOutflow())
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BigDecimal safeToSpend(Long userId) {
        return Money.floorZero(plan(userId).getSpendingAllowance().subtract(spentThisMonth(userId)));
    }

    public BigDecimal dailyAllowance(BigDecimal safe) {
        return Money.divide(safe, clock.daysRemainingInMonth());
    }

    /** Month's allocated savings = sum of goal contributions. */
    public BigDecimal savedThisMonth(Long userId) {
        return Money.scale(goals.findAllByUserIdOrderBySortOrderAsc(userId).stream().map(Goal::getMonthly).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BigDecimal unpaidBillsThisMonth(Long userId) {
        LocalDate today = clock.today();
        return Money.scale(bills.findAllByUserIdOrderByDueDayAsc(userId).stream()
            .filter(b -> !b.isPaidFor(today))
            .map(Bill::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** The goal that absorbs a purchase: the non-priority goal taking the largest monthly contribution. */
    /**
     * The goal a purchase is traded off against, if there is one.
     *
     * <p>Empty is a normal state, not an error: a user may have no goals at all,
     * or only priority ones. "Can I afford this" is still answerable without a
     * goal — there is simply no delay to report — so callers must handle empty
     * rather than treating it as not-found.
     */
    public Optional<Goal> flexibleGoal(Long userId) {
        List<Goal> all = goals.findAllByUserIdOrderBySortOrderAsc(userId);
        return all.stream()
            .filter(g -> !g.isPriority() && !g.isOnHold())
            .max((a, b) -> a.getMonthly().compareTo(b.getMonthly()))
            .or(() -> all.stream().filter(g -> !g.isPriority()).findFirst());
    }

    public SummaryResponse summary(Long userId) {
        Plan plan = plan(userId);
        LocalDate today = clock.today();
        YearMonth month = clock.currentMonth();
        String monthLabel = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        // Fetched once and reduced twice: the month's total drives Safe to Spend,
        // which is allowance-based and account-independent, while the per-account
        // split is what each account card reports. An expense can now name any
        // account the user owns, so attributing the whole month's spending to
        // whichever account is SPENDING would report money that never left it.
        List<Transaction> monthTransactions = transactionsThisMonth(userId);
        BigDecimal spent = Money.scale(sumSpending(monthTransactions.stream()));
        Map<Long, BigDecimal> spentByAccount = monthTransactions.stream()
            .filter(t -> t.getKind() == TransactionKind.SPENDING)
            .collect(Collectors.groupingBy(
                t -> t.getAccount().getId(),
                Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));

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

        BigDecimal saved = savedThisMonth(userId);
        Savings savings = new Savings(saved, plan.getSavingsTarget(), saved.compareTo(plan.getSavingsTarget().multiply(new BigDecimal("0.75"))) >= 0);

        List<Account> accts = accounts.findAllByUserIdOrderBySortOrderAsc(userId);
        List<Goal> goalList = goals.findAllByUserIdOrderBySortOrderAsc(userId);
        BigDecimal unpaid = unpaidBillsThisMonth(userId);
        BigDecimal committed = Money.scale(goalList.stream().map(Goal::getSaved).reduce(BigDecimal.ZERO, BigDecimal::add));

        List<AccountDto> accountDtos = accts.stream().map(a -> {
            BigDecimal reserved = switch (a.getKind()) {
                case BILLS -> unpaid.min(a.getBalance());
                case SAVINGS -> committed.min(a.getBalance());
                case SPENDING -> Money.scale(spentByAccount.getOrDefault(a.getId(), BigDecimal.ZERO));
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

        List<Bill> billList = bills.findAllByUserIdOrderByDueDayAsc(userId);
        List<BillDto> billDtos = billList.stream().map(b -> toDto(b, today)).toList();
        Bills billsDto = new Bills(billDtos, billList.size(), unpaid);

        List<GoalDto> goalDtos = goalList.stream().map(g -> toDto(g, month)).toList();

        List<TransactionDto> recent = transactions.findAllByUserIdOrderByOccurredAtDescIdDesc(userId).stream()
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
