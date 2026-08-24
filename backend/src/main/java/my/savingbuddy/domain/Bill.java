package my.savingbuddy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bills")
public class Bill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Day of month the bill is due (1-31). */
    @Column(nullable = false)
    private int dueDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillMethod method;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Account account;

    /** When this bill was last paid; it counts as paid for a month if this date falls in that month. */
    private LocalDate lastPaidOn;

    protected Bill() {}

    public Bill(String name, BigDecimal amount, int dueDay, BillMethod method, Account account, LocalDate lastPaidOn) {
        this.name = name;
        this.amount = amount;
        this.dueDay = dueDay;
        this.method = method;
        this.account = account;
        this.lastPaidOn = lastPaidOn;
    }

    public boolean isPaidFor(LocalDate today) {
        return lastPaidOn != null
            && lastPaidOn.getYear() == today.getYear()
            && lastPaidOn.getMonth() == today.getMonth();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public int getDueDay() { return dueDay; }
    public BillMethod getMethod() { return method; }
    public Account getAccount() { return account; }
    public LocalDate getLastPaidOn() { return lastPaidOn; }
}
