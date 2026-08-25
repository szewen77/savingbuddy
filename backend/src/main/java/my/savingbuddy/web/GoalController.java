package my.savingbuddy.web;

import jakarta.validation.Valid;
import my.savingbuddy.api.Dtos.GoalDto;
import my.savingbuddy.api.Dtos.GoalRequest;
import my.savingbuddy.security.CurrentUser;
import my.savingbuddy.service.GoalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goals")
public class GoalController {
    private final GoalService goals;
    private final CurrentUser currentUser;

    public GoalController(GoalService goals, CurrentUser currentUser) {
        this.goals = goals;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalDto create(@Valid @RequestBody GoalRequest request) {
        return goals.create(currentUser.id(), request);
    }

    @PutMapping("/{id}")
    public GoalDto update(@PathVariable Long id, @Valid @RequestBody GoalRequest request) {
        return goals.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        goals.delete(currentUser.id(), id);
    }
}
