package my.savingbuddy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** The single household budget plan. One row. */
@Entity
@Table(name = "plan")
public class Plan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private String employer;

    /** Day of month salary lands. */
    @Column(nullable = false)
    private int payday;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal salary;

    /** Portion of salary routed to the bills account each payday. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal billsAllocation;

    /** Monthly savings target (what *should* go to goals). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal savingsTarget;

    /** Monthly discretionary budget that Safe to Spend is measured against. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal spendingAllowance;

    protected Plan() {}

    public Plan(Long userId, String ownerName, String employer, int payday, BigDecimal salary, BigDecimal billsAllocation,
                BigDecimal savingsTarget, BigDecimal spendingAllowance) {
        this.userId = userId;
        this.ownerName = ownerName;
        this.employer = employer;
        this.payday = payday;
        this.salary = salary;
        this.billsAllocation = billsAllocation;
        this.savingsTarget = savingsTarget;
        this.spendingAllowance = spendingAllowance;
    }

    /** Applies an edit from the settings screen. */
    public void update(String ownerName, String employer, int payday, BigDecimal salary,
                       BigDecimal billsAllocation, BigDecimal savingsTarget, BigDecimal spendingAllowance) {
        this.ownerName = ownerName;
        this.employer = employer;
        this.payday = payday;
        this.salary = salary;
        this.billsAllocation = billsAllocation;
        this.savingsTarget = savingsTarget;
        this.spendingAllowance = spendingAllowance;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getOwnerName() { return ownerName; }
    public String getEmployer() { return employer; }
    public int getPayday() { return payday; }
    public BigDecimal getSalary() { return salary; }
    public BigDecimal getBillsAllocation() { return billsAllocation; }
    public BigDecimal getSavingsTarget() { return savingsTarget; }
    public BigDecimal getSpendingAllowance() { return spendingAllowance; }
}
