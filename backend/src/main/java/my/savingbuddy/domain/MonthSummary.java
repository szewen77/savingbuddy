package my.savingbuddy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.YearMonth;

/** Closed-month history used for trends and 6-month averages. The current month is always computed live. */
@Entity
@Table(name = "month_summaries")
public class MonthSummary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period", nullable = false, unique = true)
    private YearMonth month;

    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal income;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal saved;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal eatingOut;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal groceries;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal transport;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal other;

    protected MonthSummary() {}

    public MonthSummary(YearMonth month, BigDecimal income, BigDecimal saved, BigDecimal eatingOut,
                        BigDecimal groceries, BigDecimal transport, BigDecimal other) {
        this.month = month;
        this.income = income;
        this.saved = saved;
        this.eatingOut = eatingOut;
        this.groceries = groceries;
        this.transport = transport;
        this.other = other;
    }

    public YearMonth getMonth() { return month; }
    public BigDecimal getIncome() { return income; }
    public BigDecimal getSaved() { return saved; }
    public BigDecimal getEatingOut() { return eatingOut; }
    public BigDecimal getGroceries() { return groceries; }
    public BigDecimal getTransport() { return transport; }
    public BigDecimal getOther() { return other; }
}
