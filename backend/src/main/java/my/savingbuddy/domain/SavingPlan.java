package my.savingbuddy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A "Wait & Save" commitment: set aside a purchase price over a few weeks instead of buying now. */
@Entity
@Table(name = "saving_plans")
public class SavingPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount;
    @Column(nullable = false) private int weeks;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal weeklyAmount;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected SavingPlan() {}

    public SavingPlan(Long userId, BigDecimal totalAmount, int weeks, BigDecimal weeklyAmount, LocalDateTime createdAt) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.weeks = weeks;
        this.weeklyAmount = weeklyAmount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public int getWeeks() { return weeks; }
    public BigDecimal getWeeklyAmount() { return weeklyAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
