package my.savingbuddy.domain;

import jakarta.persistence.*;

/** A pattern SavingBuddy has noticed and wants to surface on the Insights screen. */
@Entity
@Table(name = "observations")
public class Observation {
    public enum Tone { WARN, GOOD }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false) private String title;
    @Column(nullable = false, length = 500) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Tone tone;
    @Column(nullable = false) private int sortOrder;

    protected Observation() {}

    public Observation(Long userId, String title, String body, Tone tone, int sortOrder) {
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.tone = tone;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Tone getTone() { return tone; }
    public int getSortOrder() { return sortOrder; }
}
