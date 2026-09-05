package my.savingbuddy.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import my.savingbuddy.domain.AccountKind;
import my.savingbuddy.domain.BillMethod;
import my.savingbuddy.domain.TransactionKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** JSON shapes exposed by the REST API. Records only; no behaviour. */
public final class Dtos {
    private Dtos() {}

    // ---- Summary (Home / Goals / Money screens) ----

    public record SummaryResponse(
        Profile profile,
        SafeToSpend safeToSpend,
        Savings savings,
        MoneyOverview money,
        List<AccountDto> accounts,
        Bills bills,
        List<GoalDto> goals,
        List<TransactionDto> recent
    ) {}

    public record Profile(
        String name,
        String firstName,
        int payday,
        LocalDate today,
        LocalDate nextPayday,
        int daysToPayday,
        String monthLabel
    ) {}

    public record SafeToSpend(
        BigDecimal amount,
        BigDecimal allowance,
        BigDecimal spentThisMonth,
        int daysRemaining,
        BigDecimal daily,
        BigDecimal weekly
    ) {}

    public record Savings(BigDecimal saved, BigDecimal target, boolean onTrack) {}

    public record MoneyOverview(
        BigDecimal total,
        BigDecimal reserved,
        BigDecimal available,
        BigDecimal bills,
        BigDecimal savings,
        BigDecimal spending,
        int accountCount
    ) {}

    public record AccountDto(
        Long id,
        String code,
        String name,
        AccountKind kind,
        BigDecimal balance,
        BigDecimal reserved,
        BigDecimal free,
        int goalsCount
    ) {}

    public record Bills(List<BillDto> items, int total, BigDecimal remaining) {}

    public record BillDto(
        Long id,
        String name,
        BigDecimal amount,
        int dueDay,
        LocalDate dueDate,
        int daysUntilDue,
        BillMethod method,
        String accountName,
        boolean paid,
        LocalDate lastPaidOn
    ) {}

    public enum GoalStatus { ON_TRACK, BEHIND, DELAYED, ON_HOLD }

    public record GoalDto(
        Long id,
        String name,
        String description,
        BigDecimal target,
        BigDecimal saved,
        BigDecimal monthly,
        String targetMonth,
        String effectiveMonth,
        boolean priority,
        int delayMonths,
        int monthsAtPace,
        GoalStatus status,
        BigDecimal behindBy,
        BigDecimal extraMonthly,
        double progress
    ) {}

    public record TransactionDto(
        Long id,
        String name,
        String category,
        TransactionKind kind,
        BigDecimal amount,
        String accountName,
        LocalDateTime occurredAt,
        String note
    ) {}

    // ---- Activity ----

    public record ActivityResponse(
        BigDecimal spentThisMonth,
        BigDecimal receivedSincePayday,
        LocalDate lastPayday,
        BigDecimal safeToSpend,
        String monthLabel,
        List<TransactionDto> transactions
    ) {}

    public record AddExpenseRequest(
        @NotNull @Positive @DecimalMax("9999999") BigDecimal amount,
        @NotBlank @Size(max = 40) String category,
        @Size(max = 80) String name,
        Long accountId
    ) {}

    public record AddExpenseResponse(TransactionDto transaction, BigDecimal safeToSpend, BigDecimal daily) {}

    // ---- Affordability ----

    public record AffordRequest(@NotNull @Positive @DecimalMax("9999999") BigDecimal amount) {}

    public enum Verdict { YES, NO }

    public record AffordPreview(
        BigDecimal amount,
        Verdict verdict,
        BigDecimal safeBefore,
        BigDecimal safeAfter,
        BigDecimal shortfall,
        BigDecimal savedBefore,
        BigDecimal savedAfter,
        BigDecimal savingsTarget,
        BigDecimal dailyBefore,
        BigDecimal dailyAfter,
        int daysRemaining,
        GoalImpact goal,
        WaitPlan waitPlan
    ) {}

    public record GoalImpact(
        Long id,
        String name,
        BigDecimal saved,
        BigDecimal target,
        double progress,
        String currentMonth,
        String newMonth,
        int delayMonths,
        boolean stalls
    ) {}

    public record WaitPlan(int weeks, BigDecimal weekly) {}

    public record BuyResponse(TransactionDto transaction, BigDecimal safeToSpend, BigDecimal daily, GoalDto goal) {}

    public record SavingPlanDto(Long id, BigDecimal totalAmount, int weeks, BigDecimal weeklyAmount, LocalDateTime createdAt) {}

    // ---- Insights ----

    public record InsightsResponse(
        double savingRate,
        int risingStreak,
        BigDecimal spentThisMonth,
        String monthLabel,
        List<MonthPoint> months,
        List<CategoryDto> categories,
        List<ObservationDto> observations
    ) {}

    public record MonthPoint(String month, String label, BigDecimal saved, BigDecimal income, boolean current) {}

    public record CategoryDto(String name, BigDecimal amount, double share, BigDecimal average, BigDecimal delta) {}

    public record ObservationDto(Long id, String title, String body, String tone) {}

    // ---- Auth ----

    public record AuthUser(String email) {}

    public record RegisterRequest(
        @NotBlank @jakarta.validation.constraints.Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 200) String signupCode,
        @Size(max = 200) String inviteToken
    ) {
        /** Redacted: the generated record toString would print the password, the code and the token. */
        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + ", password=***, signupCode=***, inviteToken=***]";
        }
    }

    public record RegistrationStatus(String mode) {}

    public record RegistrationModeRequest(@NotBlank String mode) {}

    /** The code is returned once, at creation, and never stored in plaintext. */
    public record PasswordResetDto(String email, String token, java.time.Instant expiresAt) {
        @Override
        public String toString() { return "PasswordResetDto[email=" + email + ", token=***]"; }
    }

    public record RedeemResetRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {
        @Override
        public String toString() { return "RedeemResetRequest[token=***, newPassword=***]"; }
    }

    /** {@code token} is populated only on creation — it is never stored, so it cannot be shown again. */
    public record InviteDto(
        Long id,
        String token,
        String status,
        java.time.Instant createdAt,
        java.time.Instant expiresAt,
        String usedBy
    ) {}

    /** Bounded deliberately: the email becomes a rate-limiter key, so an unbounded
     *  value would let an unauthenticated caller drive memory use. */
    public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 200) String password
    ) {}

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {}

    // ---- Setup / onboarding ----

    public record SetupStatus(boolean configured, String ownerName) {}

    public record SetupAccount(
        @NotBlank @Size(max = 8) String code,
        @NotBlank @Size(max = 60) String name,
        @NotNull AccountKind kind,
        @NotNull @PositiveOrZero @DecimalMax("99999999") BigDecimal balance
    ) {}

    public record SetupRequest(
        @NotBlank @Size(max = 60) String ownerName,
        @Size(max = 60) String employer,
        @Min(1) @Max(31) int payday,
        @NotNull @PositiveOrZero @DecimalMax("9999999") BigDecimal salary,
        @NotNull @PositiveOrZero @DecimalMax("9999999") BigDecimal billsAllocation,
        @NotNull @PositiveOrZero @DecimalMax("9999999") BigDecimal savingsTarget,
        @NotNull @Positive @DecimalMax("9999999") BigDecimal spendingAllowance,
        @NotEmpty List<@Valid SetupAccount> accounts
    ) {}

    // ---- Settings ----

    public record SettingsResponse(SettingsPlan plan, List<SettingsAccount> accounts) {}

    public record SettingsPlan(
        String ownerName, String employer, int payday, BigDecimal salary,
        BigDecimal billsAllocation, BigDecimal savingsTarget, BigDecimal spendingAllowance
    ) {}

    /** An account as it appears in settings, with the usage that decides whether it can be removed. */
    public record SettingsAccount(
        Long id, String code, String name, AccountKind kind, BigDecimal balance,
        int transactionCount, int billCount, boolean removable
    ) {}

    public record SettingsAccountUpdate(
        /** Null for an account being added. */
        Long id,
        @NotBlank @Size(max = 8) String code,
        @NotBlank @Size(max = 60) String name,
        @NotNull AccountKind kind,
        @NotNull @PositiveOrZero @DecimalMax("99999999") BigDecimal balance
    ) {}

    public record SettingsRequest(
        @NotBlank @Size(max = 60) String ownerName,
        @Size(max = 60) String employer,
        @Min(1) @Max(31) int payday,
        @NotNull @PositiveOrZero @DecimalMax("9999999") BigDecimal salary,
        @NotNull @PositiveOrZero @DecimalMax("9999999") BigDecimal billsAllocation,
        @NotNull @PositiveOrZero @DecimalMax("9999999") BigDecimal savingsTarget,
        @NotNull @Positive @DecimalMax("9999999") BigDecimal spendingAllowance,
        @NotEmpty List<@Valid SettingsAccountUpdate> accounts
    ) {}

    // ---- Goals ----

    /**
     * Create/update payload. delayMonths and sortOrder are absent deliberately —
     * both are server-owned, and sortOrder is load-bearing in flexibleGoal's
     * tie-break, so a client must not be able to steer which goal absorbs a purchase.
     */
    public record GoalRequest(
        @NotBlank @Size(max = 60) String name,
        @Size(max = 200) String description,
        @NotNull @DecimalMin("1") @DecimalMax("9999999") BigDecimal target,
        @NotNull @PositiveOrZero @DecimalMax("9999999") BigDecimal saved,
        @NotNull @DecimalMin("1") @DecimalMax("9999999") BigDecimal monthly,
        @NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "must be a month like 2027-03") String targetMonth,
        boolean priority
    ) {}

    // ---- Export ----

    /** Complete contents of the local database, so the data is never trapped in the app. */
    public record ExportBundle(
        String app,
        int schemaVersion,
        java.time.Instant exportedAt,
        ExportPlan plan,
        List<ExportAccount> accounts,
        List<ExportTransaction> transactions,
        List<ExportBill> bills,
        List<ExportGoal> goals,
        List<ExportMonth> monthSummaries,
        List<ObservationDto> observations,
        List<SavingPlanDto> savingPlans
    ) {}

    public record ExportPlan(
        String ownerName, String employer, int payday, BigDecimal salary,
        BigDecimal billsAllocation, BigDecimal savingsTarget, BigDecimal spendingAllowance
    ) {}

    public record ExportAccount(Long id, String code, String name, AccountKind kind, BigDecimal balance, int sortOrder) {}

    public record ExportTransaction(
        Long id, Long accountId, String accountName, String name, String category,
        TransactionKind kind, BigDecimal amount, LocalDateTime occurredAt, String note
    ) {}

    public record ExportBill(
        Long id, Long accountId, String name, BigDecimal amount, int dueDay, BillMethod method, LocalDate lastPaidOn
    ) {}

    public record ExportGoal(
        Long id, String name, String description, BigDecimal target, BigDecimal saved, BigDecimal monthly,
        String targetMonth, boolean priority, int delayMonths, int sortOrder
    ) {}

    public record ExportMonth(
        String period, BigDecimal income, BigDecimal saved,
        BigDecimal eatingOut, BigDecimal groceries, BigDecimal transport, BigDecimal other
    ) {}

    // ---- Errors ----

    public record ErrorResponse(String message, List<String> errors) {}
}
