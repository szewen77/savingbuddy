package my.savingbuddy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountKind kind;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    private int sortOrder;

    protected Account() {}

    public Account(String code, String name, AccountKind kind, BigDecimal balance, int sortOrder) {
        this.code = code;
        this.name = name;
        this.kind = kind;
        this.balance = balance;
        this.sortOrder = sortOrder;
    }

    /** Applies an edit from the settings screen. Balance is set outright, not adjusted. */
    public void update(String code, String name, AccountKind kind, BigDecimal balance, int sortOrder) {
        this.code = code;
        this.name = name;
        this.kind = kind;
        this.balance = balance;
        this.sortOrder = sortOrder;
    }

    public void debit(BigDecimal amount) { this.balance = this.balance.subtract(amount); }
    public void credit(BigDecimal amount) { this.balance = this.balance.add(amount); }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public AccountKind getKind() { return kind; }
    public BigDecimal getBalance() { return balance; }
    public int getSortOrder() { return sortOrder; }
}
