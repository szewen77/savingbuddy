package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.SetupAccount;
import my.savingbuddy.api.Dtos.SetupRequest;
import my.savingbuddy.api.Dtos.SetupStatus;
import my.savingbuddy.domain.Account;
import my.savingbuddy.domain.AccountKind;
import my.savingbuddy.domain.Plan;
import my.savingbuddy.repository.AccountRepository;
import my.savingbuddy.repository.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** First-run configuration. A fresh install has no plan until this runs. */
@Service
public class SetupService {
    private final PlanRepository plans;
    private final AccountRepository accounts;

    public SetupService(PlanRepository plans, AccountRepository accounts) {
        this.plans = plans;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public SetupStatus status() {
        return plans.findFirstByOrderByIdAsc()
            .map(p -> new SetupStatus(true, p.getOwnerName()))
            .orElseGet(() -> new SetupStatus(false, null));
    }

    @Transactional
    public SetupStatus configure(SetupRequest req) {
        if (plans.count() > 0) {
            throw new AlreadyConfiguredException("SavingBuddy is already set up");
        }
        long spending = req.accounts().stream().filter(a -> a.kind() == AccountKind.SPENDING).count();
        if (spending != 1) {
            throw new InvalidSetupException(
                "Exactly one account must be marked SPENDING — that is the account Safe to Spend is measured against; got " + spending);
        }
        if (req.accounts().stream().noneMatch(a -> a.kind() == AccountKind.BILLS)) {
            throw new InvalidSetupException("At least one account must be marked BILLS");
        }

        plans.save(new Plan(
            req.ownerName().trim(),
            req.employer() == null || req.employer().isBlank() ? "—" : req.employer().trim(),
            req.payday(),
            Money.scale(req.salary()),
            Money.scale(req.billsAllocation()),
            Money.scale(req.savingsTarget()),
            Money.scale(req.spendingAllowance())
        ));

        int order = 1;
        for (SetupAccount a : req.accounts()) {
            accounts.save(new Account(a.code().trim(), a.name().trim(), a.kind(), Money.scale(a.balance()), order++));
        }

        return status();
    }

    public static class AlreadyConfiguredException extends RuntimeException {
        public AlreadyConfiguredException(String m) { super(m); }
    }

    public static class InvalidSetupException extends RuntimeException {
        public InvalidSetupException(String m) { super(m); }
    }
}
