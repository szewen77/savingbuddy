package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.*;
import my.savingbuddy.domain.Account;
import my.savingbuddy.domain.AccountKind;
import my.savingbuddy.domain.Plan;
import my.savingbuddy.repository.AccountRepository;
import my.savingbuddy.repository.BillRepository;
import my.savingbuddy.repository.PlanRepository;
import my.savingbuddy.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads and edits the configuration created at onboarding. */
@Service
public class SettingsService {
    private final PlanRepository plans;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final BillRepository bills;

    public SettingsService(PlanRepository plans, AccountRepository accounts,
                           TransactionRepository transactions, BillRepository bills) {
        this.plans = plans;
        this.accounts = accounts;
        this.transactions = transactions;
        this.bills = bills;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        Plan p = plan();
        return new SettingsResponse(
            new SettingsPlan(p.getOwnerName(), p.getEmployer(), p.getPayday(), p.getSalary(),
                p.getBillsAllocation(), p.getSavingsTarget(), p.getSpendingAllowance()),
            accounts.findAllByOrderBySortOrderAsc().stream().map(this::toDto).toList()
        );
    }

    @Transactional
    public SettingsResponse update(SettingsRequest req) {
        validateKinds(req.accounts());

        Plan p = plan();
        p.update(req.ownerName().trim(),
            req.employer() == null || req.employer().isBlank() ? "—" : req.employer().trim(),
            req.payday(),
            Money.scale(req.salary()),
            Money.scale(req.billsAllocation()),
            Money.scale(req.savingsTarget()),
            Money.scale(req.spendingAllowance()));

        List<Account> existing = accounts.findAllByOrderBySortOrderAsc();
        Set<Long> keep = new HashSet<>();
        int order = 1;

        for (SettingsAccountUpdate u : req.accounts()) {
            String code = u.code().trim().toUpperCase();
            String name = u.name().trim();
            if (u.id() == null) {
                accounts.save(new Account(code, name, u.kind(), Money.scale(u.balance()), order++));
                continue;
            }
            Account a = existing.stream().filter(x -> x.getId().equals(u.id())).findFirst()
                .orElseThrow(() -> new NotFoundException("Account " + u.id() + " not found"));
            a.update(code, name, u.kind(), Money.scale(u.balance()), order++);
            keep.add(a.getId());
        }

        // Anything the client dropped is a removal — but only if nothing references it.
        List<Account> removed = new ArrayList<>();
        for (Account a : existing) {
            if (keep.contains(a.getId())) continue;
            int txns = transactions.countByAccountId(a.getId());
            int billCount = bills.countByAccountId(a.getId());
            if (txns > 0 || billCount > 0) {
                throw new SetupService.InvalidSetupException(
                    "\"" + a.getName() + "\" still has " + txns + " transactions and " + billCount
                        + " bills, so it cannot be removed. Its history would be lost.");
            }
            removed.add(a);
        }
        accounts.deleteAll(removed);

        return get();
    }

    private void validateKinds(List<SettingsAccountUpdate> list) {
        long spending = list.stream().filter(a -> a.kind() == AccountKind.SPENDING).count();
        if (spending != 1) {
            throw new SetupService.InvalidSetupException(
                "Exactly one account must be marked SPENDING — that is the account Safe to Spend is measured against; got " + spending);
        }
        if (list.stream().noneMatch(a -> a.kind() == AccountKind.BILLS)) {
            throw new SetupService.InvalidSetupException("At least one account must be marked BILLS");
        }
    }

    private SettingsAccount toDto(Account a) {
        int txns = transactions.countByAccountId(a.getId());
        int billCount = bills.countByAccountId(a.getId());
        return new SettingsAccount(a.getId(), a.getCode(), a.getName(), a.getKind(), a.getBalance(),
            txns, billCount, txns == 0 && billCount == 0);
    }

    private Plan plan() {
        return plans.findFirstByOrderByIdAsc()
            .orElseThrow(() -> new NotFoundException("No budget plan has been set up"));
    }
}
