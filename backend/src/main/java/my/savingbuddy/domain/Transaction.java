package my.savingbuddy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Account account;

    @Column(nullable = false)
    private String name;

    /** Free-text category: Groceries, Eating out, Transport, Recurring, Salary, Other... */
    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionKind kind;

    /** Always positive; direction comes from {@link #kind}. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    /** Optional secondary line, e.g. "Split: RM1,200 bills · RM2,500 savings". */
    private String note;

    protected Transaction() {}

    public Transaction(Long userId, Account account, String name, String category, TransactionKind kind,
                       BigDecimal amount, LocalDateTime occurredAt, String note) {
        this.userId = userId;
        this.account = account;
        this.name = name;
        this.category = category;
        this.kind = kind;
        this.amount = amount;
        this.occurredAt = occurredAt;
        this.note = note;
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public TransactionKind getKind() { return kind; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getNote() { return note; }
}
