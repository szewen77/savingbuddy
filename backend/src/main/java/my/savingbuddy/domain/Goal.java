package my.savingbuddy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

@Entity
@Table(name = "goals")
public class Goal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal target;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal saved;

    /** Planned contribution per month. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monthly;

    /** Planned completion month. */
    @Column(nullable = false)
    private YearMonth targetMonth;

    /** The one goal that is never traded off against purchases. */
    @Column(nullable = false)
    private boolean priority;

    /** Months this goal has been pushed back by "Buy anyway" decisions. */
    @Column(nullable = false)
    private int delayMonths;

    @Column(nullable = false)
    private int sortOrder;

    protected Goal() {}

    public Goal(Long userId, String name, String description, BigDecimal target, BigDecimal saved, BigDecimal monthly,
                YearMonth targetMonth, boolean priority, int sortOrder) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.target = target;
        this.saved = saved;
        this.monthly = monthly;
        this.targetMonth = targetMonth;
        this.priority = priority;
        this.sortOrder = sortOrder;
    }

    public BigDecimal remaining() { return target.subtract(saved).max(BigDecimal.ZERO); }

    /** Months of contributions still needed at the planned pace. */
    public int monthsAtPace() {
        if (monthly.signum() <= 0) return Integer.MAX_VALUE;
        return remaining().divide(monthly, 0, RoundingMode.CEILING).intValue();
    }

    /** Months this goal can still be delayed before it stops progressing entirely. */
    public int delayRoom() { return Math.max(0, monthsAtPace() - delayMonths); }

    public boolean isOnHold() { return monthsAtPace() > 0 && delayMonths >= monthsAtPace(); }

    public YearMonth effectiveTargetMonth() { return targetMonth.plusMonths(delayMonths); }

    public void delayBy(int months) { this.delayMonths = Math.min(monthsAtPace(), this.delayMonths + months); }

    /**
     * Applies an edit.
     *
     * <p>delayMonths resets to zero: it records slippage against a plan, and this
     * IS a new plan. Carrying it over would also let it exceed monthsAtPace if the
     * monthly contribution rose, which would strand the goal permanently on hold —
     * delayBy clamps on write, but nothing re-clamps on read.
     */
    public void update(String name, String description, BigDecimal target, BigDecimal saved,
                       BigDecimal monthly, YearMonth targetMonth, boolean priority) {
        this.name = name;
        this.description = description;
        this.target = target;
        this.saved = saved;
        this.monthly = monthly;
        this.targetMonth = targetMonth;
        this.priority = priority;
        this.delayMonths = 0;
    }

    /** Demoted when another goal of the same user is promoted — only one may be priority. */
    public void demote() { this.priority = false; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getTarget() { return target; }
    public BigDecimal getSaved() { return saved; }
    public BigDecimal getMonthly() { return monthly; }
    public YearMonth getTargetMonth() { return targetMonth; }
    public boolean isPriority() { return priority; }
    public int getDelayMonths() { return delayMonths; }
    public int getSortOrder() { return sortOrder; }
}
