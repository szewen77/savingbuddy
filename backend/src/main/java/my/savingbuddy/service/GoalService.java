package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.GoalDto;
import my.savingbuddy.api.Dtos.GoalRequest;
import my.savingbuddy.domain.Goal;
import my.savingbuddy.repository.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

/**
 * Creating and editing savings goals.
 *
 * <p>Until this existed nothing but the demo seeder could create a goal, so every
 * real account was permanently goal-less — and goals drive the Goals screen, the
 * monthly savings figure, and the trade-off behind "Can I afford this?".
 */
@Service
public class GoalService {
    private final GoalRepository goals;
    private final BudgetService budget;
    private final BudgetClock clock;

    public GoalService(GoalRepository goals, BudgetService budget, BudgetClock clock) {
        this.goals = goals;
        this.budget = budget;
        this.clock = clock;
    }

    @Transactional
    public GoalDto create(Long userId, GoalRequest req) {
        YearMonth targetMonth = validate(req);

        List<Goal> existing = goals.findAllByUserIdOrderBySortOrderAsc(userId);
        // max + 1, not count + 1: deletes leave gaps, and a collision would make
        // the ordering — and so flexibleGoal's tie-break — non-deterministic.
        int nextOrder = existing.stream().mapToInt(Goal::getSortOrder).max().orElse(0) + 1;

        if (req.priority()) demoteOthers(existing, null);

        Goal goal = goals.save(new Goal(userId, req.name().trim(), description(req), req.target(),
            req.saved(), req.monthly(), targetMonth, req.priority(), nextOrder));

        return budget.toDto(goal, clock.currentMonth());
    }

    @Transactional
    public GoalDto update(Long userId, Long id, GoalRequest req) {
        YearMonth targetMonth = validate(req);
        Goal goal = owned(userId, id);

        if (req.priority()) demoteOthers(goals.findAllByUserIdOrderBySortOrderAsc(userId), id);

        goal.update(req.name().trim(), description(req), req.target(), req.saved(),
            req.monthly(), targetMonth, req.priority());

        return budget.toDto(goal, clock.currentMonth());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        // Nothing references a goal: no foreign key targets it, and the delay a
        // purchase caused lives on this row, not on the transaction. A hard
        // delete is safe and matches how unused accounts are removed.
        goals.delete(owned(userId, id));
    }

    /** Scoped lookup. Same message whether the goal is missing or someone else's — no enumeration. */
    private Goal owned(Long userId, Long id) {
        return goals.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new NotFoundException("Goal " + id + " not found"));
    }

    /** At most one priority goal per user: the Goals screen renders exactly one and hides any others. */
    private void demoteOthers(List<Goal> existing, Long keepId) {
        existing.stream()
            .filter(Goal::isPriority)
            .filter(g -> keepId == null || !g.getId().equals(keepId))
            .forEach(Goal::demote);
    }

    private String description(GoalRequest req) {
        String d = req.description() == null ? null : req.description().trim();
        // Left null rather than coerced to a placeholder — GoalDto and the UI both
        // treat description as optional and branch on its absence.
        return d == null || d.isBlank() ? null : d;
    }

    private YearMonth validate(GoalRequest req) {
        if (req.saved().compareTo(req.target()) > 0) {
            throw new SetupService.InvalidSetupException(
                "Saved (" + Money.scale(req.saved()) + ") cannot exceed the target (" + Money.scale(req.target()) + ")");
        }
        YearMonth targetMonth = YearMonth.parse(req.targetMonth());
        if (targetMonth.isBefore(clock.currentMonth())) {
            throw new SetupService.InvalidSetupException("The target month cannot be in the past");
        }
        return targetMonth;
    }
}
