package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.*;
import my.savingbuddy.domain.*;
import my.savingbuddy.repository.AccountRepository;
import my.savingbuddy.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
public class TransactionService {
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final BudgetService budget;
    private final BudgetClock clock;

    public TransactionService(TransactionRepository transactions, AccountRepository accounts, BudgetService budget, BudgetClock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.budget = budget;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ActivityResponse activity(TransactionKind kind) {
        Plan plan = budget.plan();
        LocalDate lastPayday = clock.lastPayday(plan.getPayday());
        List<Transaction> all = transactions.findAllByOrderByOccurredAtDescIdDesc();

        BigDecimal received = Money.scale(all.stream()
            .filter(t -> t.getKind() == TransactionKind.INCOME && !t.getOccurredAt().toLocalDate().isBefore(lastPayday))
            .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        List<TransactionDto> items = all.stream()
            .filter(t -> kind == null || t.getKind() == kind)
            .map(BudgetService::toDto).toList();

        return new ActivityResponse(budget.outflowsThisMonth(), received, lastPayday, budget.safeToSpend(),
            clock.currentMonth().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH), items);
    }

    @Transactional
    public AddExpenseResponse addExpense(AddExpenseRequest req) {
        Account account = req.accountId() == null
            ? budget.spendingAccount()
            : accounts.findById(req.accountId()).orElseThrow(() -> new NotFoundException("Account " + req.accountId() + " not found"));
        String name = req.name() == null || req.name().isBlank() ? req.category().trim() : req.name().trim();
        Transaction t = new Transaction(account, name, req.category().trim(), TransactionKind.SPENDING,
            Money.scale(req.amount()), clock.now(), null);
        account.debit(t.getAmount());
        transactions.save(t);
        BigDecimal safe = budget.safeToSpend();
        return new AddExpenseResponse(BudgetService.toDto(t), safe, budget.dailyAllowance(safe));
    }

    /** Records a one-off purchase from the affordability flow. */
    @Transactional
    public Transaction recordPurchase(BigDecimal amount) {
        Account account = budget.spendingAccount();
        Transaction t = new Transaction(account, "One-off purchase", "Other", TransactionKind.SPENDING,
            Money.scale(amount), clock.now(), "Bought after checking affordability");
        account.debit(t.getAmount());
        return transactions.save(t);
    }
}
