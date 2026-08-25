package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Goal creation, editing and deletion.
 *
 * <p>Registers its own user rather than using the demo profile: the seeded
 * household already has three goals at fixed positions, and these tests assert on
 * counts and ordering.
 */
class GoalIntegrationTest extends ApiTestBase {

    private static String goal(String name, String target, String saved, String monthly,
                               String month, boolean priority) {
        return """
            {"name": "%s", "description": "why it matters", "target": %s, "saved": %s,
             "monthly": %s, "targetMonth": "%s", "priority": %s}"""
            .formatted(name, target, saved, monthly, month, priority);
    }

    @BeforeAll
    void signIn() throws Exception {
        session = register("goals@example.com", "long-enough-pw");
        doPost("/api/setup", """
            {"ownerName":"Mei","employer":"","payday":25,"salary":5000,
             "billsAllocation":1000,"savingsTarget":1500,"spendingAllowance":2000,
             "accounts":[{"code":"MBB","name":"Maybank","kind":"BILLS","balance":3000},
                         {"code":"TNG","name":"TnG","kind":"SPENDING","balance":900}]}""")
            .andExpect(status().isCreated());
    }

    @Test
    void aGoalCanBeCreatedAndShowsUpEverywhere() throws Exception {
        doPost("/api/goals", goal("Japan Trip", "8000", "3100", "500", "2027-06", false))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Japan Trip"))
            .andExpect(jsonPath("$.saved").value(3100.00))
            .andExpect(jsonPath("$.target").value(8000.00))
            .andExpect(jsonPath("$.status").value("ON_TRACK"))
            .andExpect(jsonPath("$.effectiveMonth").value("2027-06"));

        // savedThisMonth is the sum of goal monthlies, so the summary moves immediately.
        doGet("/api/summary")
            .andExpect(jsonPath("$.goals", hasSize(1)))
            .andExpect(jsonPath("$.savings.saved").value(500.00));
    }

    @Test
    void editingReplacesThePlanAndClearsAccumulatedDelay() throws Exception {
        String id = createGoal("Laptop", "5000", "1000", "500", "2027-01");

        // Push it back the way "Buy Anyway" would: 600 against a 500/month goal
        // costs it two months of contributions.
        doPost("/api/afford/buy", "{\"amount\":600}").andExpect(status().isCreated());
        doGet("/api/summary").andExpect(jsonPath("$.goals[0].delayMonths").value(2));

        doPut("/api/goals/" + id, goal("Laptop Pro", "6000", "1000", "1000", "2027-05", false))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Laptop Pro"))
            .andExpect(jsonPath("$.target").value(6000.00))
            // Re-planning supersedes slippage: the new date is what was asked for.
            .andExpect(jsonPath("$.effectiveMonth").value("2027-05"))
            .andExpect(jsonPath("$.status").value("ON_TRACK"));
    }

    @Test
    void onlyOneGoalCanBePriority() throws Exception {
        String first = createGoal("Emergency Fund", "12000", "2000", "400", "2028-01");
        doPut("/api/goals/" + first, goal("Emergency Fund", "12000", "2000", "400", "2028-01", true))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priority").value(true));

        doPost("/api/goals", goal("House", "50000", "0", "800", "2030-01", true))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.priority").value(true));

        // Promoting the second must demote the first — the Goals screen renders
        // exactly one priority goal and hides any others entirely.
        long priorities = countPriorityGoals();
        org.assertj.core.api.Assertions.assertThat(priorities).isEqualTo(1);
    }

    @Test
    void impossibleGoalsAreRejected() throws Exception {
        doPost("/api/goals", goal("Backwards", "1000", "5000", "100", "2027-01", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("cannot exceed the target")));

        doPost("/api/goals", goal("Yesterday", "1000", "0", "100", "2020-01", false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("cannot be in the past")));

        // monthly must be positive: zero would render "0 months of saving left at this pace".
        doPost("/api/goals", goal("Free", "1000", "0", "0", "2027-01", false))
            .andExpect(status().isBadRequest());

        doPost("/api/goals", goal("", "1000", "0", "100", "2027-01", false))
            .andExpect(status().isBadRequest());

        doPost("/api/goals", goal("Bad month", "1000", "0", "100", "2027-13", false))
            .andExpect(status().isBadRequest());
    }

    @Test
    void aGoalCanBeDeleted() throws Exception {
        String id = createGoal("Transient", "1000", "0", "100", "2027-09");
        doDelete("/api/goals/" + id).andExpect(status().isNoContent());
        doDelete("/api/goals/" + id).andExpect(status().isNotFound());
    }

    @Test
    void unknownGoalsAre404() throws Exception {
        doPut("/api/goals/999999", goal("Ghost", "1000", "0", "100", "2027-09", false))
            .andExpect(status().isNotFound());
        doDelete("/api/goals/999999").andExpect(status().isNotFound());
    }

    private String createGoal(String name, String target, String saved, String monthly, String month) throws Exception {
        String body = doPost("/api/goals", goal(name, target, saved, monthly, month, false))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return String.valueOf(com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class));
    }

    private long countPriorityGoals() throws Exception {
        String body = doGet("/api/summary").andReturn().getResponse().getContentAsString();
        java.util.List<Boolean> flags = com.jayway.jsonpath.JsonPath.parse(body).read("$.goals[*].priority");
        return flags.stream().filter(Boolean::booleanValue).count();
    }
}
