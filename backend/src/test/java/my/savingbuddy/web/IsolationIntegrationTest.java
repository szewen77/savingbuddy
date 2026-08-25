package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import my.savingbuddy.domain.*;
import my.savingbuddy.repository.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * THE multi-tenancy guarantee: two fully-populated users, and no endpoint ever
 * shows one of them anything belonging to the other.
 *
 * <p>Both users are seeded with rows in <em>every</em> user-owned table. That is
 * the point. An earlier version of this test created no goals, bills,
 * observations, month summaries or saving plans for anyone, so five repositories
 * were covered vacuously — every assertion about them passed against empty
 * tables, and would have kept passing if the finders lost their user scope.
 *
 * <p>Ordered, because the class shares one database: read-only scoping runs
 * before anything that mutates state.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IsolationIntegrationTest extends ApiTestBase {
    MockHttpSession alice;
    MockHttpSession bob;
    Long aliceId;
    Long bobId;

    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired BillRepository bills;
    @Autowired ObservationRepository observations;
    @Autowired MonthSummaryRepository months;
    @Autowired AccountRepository accounts;

    private static String setup(String name, String allowance, String accountsJson) {
        return """
            {
              "ownerName": "%s", "employer": "", "payday": 25,
              "salary": 5000, "billsAllocation": 1000, "savingsTarget": 1500, "spendingAllowance": %s,
              "accounts": [%s]
            }""".formatted(name, allowance, accountsJson);
    }

    private static String goal(String name, String monthly, String month) {
        return """
            {"name": "%s", "description": null, "target": 8000, "saved": 1000,
             "monthly": %s, "targetMonth": "%s", "priority": false}""".formatted(name, monthly, month);
    }

    @BeforeAll
    void twoFullyPopulatedUsers() throws Exception {
        alice = register("alice@example.com", "alices-password");
        bob = register("bob@example.com", "bobs-password");
        aliceId = users.findByEmailIgnoreCase("alice@example.com").orElseThrow().getId();
        bobId = users.findByEmailIgnoreCase("bob@example.com").orElseThrow().getId();

        doPost(alice, "/api/setup", setup("Alice", "2000", """
            {"code": "MBB", "name": "Maybank", "kind": "BILLS", "balance": 3000},
            {"code": "TNG", "name": "TnG", "kind": "SPENDING", "balance": 900}"""))
            .andExpect(status().isCreated());
        doPost(bob, "/api/setup", setup("Bob", "1000", """
            {"code": "RHB", "name": "RHB", "kind": "BILLS", "balance": 7000},
            {"code": "BSN", "name": "BSN", "kind": "SAVINGS", "balance": 12000},
            {"code": "GX", "name": "GXBank", "kind": "SPENDING", "balance": 400}"""))
            .andExpect(status().isCreated());

        doPost(alice, "/api/transactions", "{\"amount\":120,\"category\":\"Groceries\",\"name\":\"Alice shop\"}")
            .andExpect(status().isCreated());
        doPost(bob, "/api/transactions", "{\"amount\":45,\"category\":\"Transport\",\"name\":\"Bob commute\"}")
            .andExpect(status().isCreated());

        // Goals through the API, now that one exists.
        doPost(alice, "/api/goals", goal("Alice goal", "900", "2028-01")).andExpect(status().isCreated());
        doPost(bob, "/api/goals", goal("Bob goal", "100", "2028-01")).andExpect(status().isCreated());

        // The rest have no API yet, so seed them directly — without rows here the
        // assertions below would pass against empty tables and prove nothing.
        Account aliceBills = accounts.findAllByUserIdOrderBySortOrderAsc(aliceId).getFirst();
        Account bobBills = accounts.findAllByUserIdOrderBySortOrderAsc(bobId).getFirst();
        bills.save(new Bill(aliceId, "Alice bill", new BigDecimal("500.00"), 10, BillMethod.MANUAL, aliceBills, null));
        bills.save(new Bill(bobId, "Bob bill", new BigDecimal("77.00"), 12, BillMethod.MANUAL, bobBills, null));

        observations.save(new Observation(aliceId, "Alice observation", "hers", Observation.Tone.GOOD, 1));
        observations.save(new Observation(bobId, "Bob observation", "his", Observation.Tone.WARN, 1));

        YearMonth lastMonth = YearMonth.of(2026, 7);
        months.save(new MonthSummary(aliceId, lastMonth, bd(5000), bd(1500), bd(200), bd(300), bd(100), bd(50)));
        months.save(new MonthSummary(bobId, lastMonth, bd(3000), bd(400), bd(90), bd(120), bd(60), bd(30)));
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static BigDecimal bd(int v) { return BigDecimal.valueOf(v); }

    // ---------- reads ----------

    @Test @Order(1)
    void meReportsTheCallersOwnIdentity() throws Exception {
        doGet(alice, "/api/auth/me").andExpect(jsonPath("$.email").value("alice@example.com"));
        doGet(bob, "/api/auth/me").andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test @Order(2)
    void setupStatusNamesTheCaller() throws Exception {
        doGet(alice, "/api/setup")
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.ownerName").value("Alice"));
        doGet(bob, "/api/setup").andExpect(jsonPath("$.ownerName").value("Bob"));
    }

    @Test @Order(3)
    void summaryGoalsAndBillsAreScoped() throws Exception {
        doGet(alice, "/api/summary")
            .andExpect(jsonPath("$.profile.name").value("Alice"))
            .andExpect(jsonPath("$.accounts", hasSize(2)))
            .andExpect(jsonPath("$.safeToSpend.amount").value(1880.00))
            .andExpect(jsonPath("$.money.total").value(3780.00))
            .andExpect(jsonPath("$.goals", hasSize(1)))
            .andExpect(jsonPath("$.goals[0].name").value("Alice goal"))
            .andExpect(jsonPath("$.bills.items", hasSize(1)))
            .andExpect(jsonPath("$.bills.items[0].name").value("Alice bill"))
            .andExpect(jsonPath("$.savings.saved").value(900.00));

        doGet(bob, "/api/summary")
            .andExpect(jsonPath("$.profile.name").value("Bob"))
            .andExpect(jsonPath("$.accounts", hasSize(3)))
            .andExpect(jsonPath("$.safeToSpend.amount").value(955.00))
            .andExpect(jsonPath("$.money.total").value(19355.00))
            .andExpect(jsonPath("$.goals", hasSize(1)))
            .andExpect(jsonPath("$.goals[0].name").value("Bob goal"))
            .andExpect(jsonPath("$.bills.items", hasSize(1)))
            .andExpect(jsonPath("$.bills.items[0].name").value("Bob bill"))
            .andExpect(jsonPath("$.savings.saved").value(100.00));
    }

    @Test @Order(4)
    void activityAndItsFilterAreScoped() throws Exception {
        doGet(alice, "/api/transactions")
            .andExpect(jsonPath("$.transactions", hasSize(1)))
            .andExpect(jsonPath("$.transactions[0].name").value("Alice shop"));
        doGet(bob, "/api/transactions")
            .andExpect(jsonPath("$.transactions", hasSize(1)))
            .andExpect(jsonPath("$.transactions[0].name").value("Bob commute"));

        doGet(alice, "/api/transactions", "kind", "SPENDING")
            .andExpect(jsonPath("$.transactions", hasSize(1)))
            .andExpect(jsonPath("$.transactions[0].name").value("Alice shop"));
        doGet(bob, "/api/transactions", "kind", "SPENDING")
            .andExpect(jsonPath("$.transactions", hasSize(1)))
            .andExpect(jsonPath("$.transactions[0].name").value("Bob commute"));
    }

    @Test @Order(5)
    void insightsAreScoped() throws Exception {
        doGet(alice, "/api/insights")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.observations", hasSize(1)))
            .andExpect(jsonPath("$.observations[0].title").value("Alice observation"));
        doGet(bob, "/api/insights")
            .andExpect(jsonPath("$.observations", hasSize(1)))
            .andExpect(jsonPath("$.observations[0].title").value("Bob observation"));
    }

    @Test @Order(6)
    void settingsAndItsUsageCountsAreScoped() throws Exception {
        doGet(alice, "/api/settings")
            .andExpect(jsonPath("$.plan.ownerName").value("Alice"))
            .andExpect(jsonPath("$.accounts", hasSize(2)));
        doGet(bob, "/api/settings")
            .andExpect(jsonPath("$.plan.ownerName").value("Bob"))
            .andExpect(jsonPath("$.accounts", hasSize(3)))
            // Usage counts must come from the caller's own rows.
            .andExpect(jsonPath("$.accounts[2].transactionCount").value(1))
            .andExpect(jsonPath("$.accounts[1].transactionCount").value(0));
    }

    @Test @Order(7)
    void everyExportCollectionIsScoped() throws Exception {
        doGet(alice, "/api/export")
            .andExpect(jsonPath("$.plan.ownerName").value("Alice"))
            .andExpect(jsonPath("$.accounts", hasSize(2)))
            .andExpect(jsonPath("$.transactions", hasSize(1)))
            .andExpect(jsonPath("$.bills", hasSize(1)))
            .andExpect(jsonPath("$.goals", hasSize(1)))
            .andExpect(jsonPath("$.monthSummaries", hasSize(1)))
            .andExpect(jsonPath("$.observations", hasSize(1)))
            .andExpect(jsonPath("$.savingPlans", hasSize(0)));

        doGet(bob, "/api/export")
            .andExpect(jsonPath("$.plan.ownerName").value("Bob"))
            .andExpect(jsonPath("$.accounts", hasSize(3)))
            .andExpect(jsonPath("$.bills[0].name").value("Bob bill"))
            .andExpect(jsonPath("$.goals[0].name").value("Bob goal"))
            .andExpect(jsonPath("$.monthSummaries", hasSize(1)));
    }

    @Test @Order(8)
    void affordPreviewUsesTheCallersOwnFigures() throws Exception {
        doPost(alice, "/api/afford/preview", "{\"amount\":100}")
            .andExpect(jsonPath("$.safeBefore").value(1880.00))
            .andExpect(jsonPath("$.goal.name").value("Alice goal"));
        doPost(bob, "/api/afford/preview", "{\"amount\":100}")
            .andExpect(jsonPath("$.safeBefore").value(955.00))
            .andExpect(jsonPath("$.goal.name").value("Bob goal"));
    }

    @Test @Order(9)
    void idsAreDisjointWithinEveryCollection() throws Exception {
        // Compared per collection, not pooled: ids are per-table identity
        // sequences, so Alice's account 2 and Bob's transaction 2 are unrelated
        // rows and a pooled comparison would fail on a perfectly isolated app.
        for (String path : List.of("$.accounts[*].id", "$.transactions[*].id", "$.bills[*].id", "$.goals[*].id")) {
            Set<Integer> mine = ids(alice, path);
            Set<Integer> theirs = ids(bob, path);
            assertThat(mine).as(path + " for Alice").isNotEmpty();
            assertThat(theirs).as(path + " for Bob").isNotEmpty();
            // A catch-all: if any collection above ever leaked, this intersects.
            assertThat(mine).as("overlap in " + path).doesNotContainAnyElementsOf(theirs);
        }
    }

    private Set<Integer> ids(MockHttpSession as, String path) throws Exception {
        String body = doGet(as, "/api/export").andReturn().getResponse().getContentAsString();
        return new java.util.HashSet<>(com.jayway.jsonpath.JsonPath.parse(body).read(path));
    }

    // ---------- writes against another user's resources ----------

    @Test @Order(10)
    void spendingIntoAnotherUsersAccountIsImpossible() throws Exception {
        long aliceAccountId = firstAccountId(alice);

        doPost(bob, "/api/transactions",
                "{\"amount\":50,\"category\":\"Other\",\"accountId\":" + aliceAccountId + "}")
            .andExpect(status().isNotFound());

        doGet(alice, "/api/summary").andExpect(jsonPath("$.money.total").value(3780.00));
    }

    @Test @Order(11)
    void aForeignIdAndAnImpossibleIdFailIdentically() throws Exception {
        long aliceAccountId = firstAccountId(alice);

        String foreign = doPost(bob, "/api/transactions",
                "{\"amount\":50,\"category\":\"Other\",\"accountId\":" + aliceAccountId + "}")
            .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String impossible = doPost(bob, "/api/transactions",
                "{\"amount\":50,\"category\":\"Other\",\"accountId\":999999999}")
            .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        // Identical but for the id itself: a different status or wording would
        // tell Bob whether Alice's account exists.
        assertThat(foreign.replace(String.valueOf(aliceAccountId), "X"))
            .isEqualTo(impossible.replace("999999999", "X"));
    }

    @Test @Order(12)
    void anotherUsersGoalCannotBeEditedOrDeleted() throws Exception {
        long aliceGoalId = com.jayway.jsonpath.JsonPath
            .parse(doGet(alice, "/api/export").andReturn().getResponse().getContentAsString())
            .read("$.goals[0].id", Long.class);

        doPut(bob, "/api/goals/" + aliceGoalId, goal("Stolen", "1", "2028-01"))
            .andExpect(status().isNotFound());
        doDelete(bob, "/api/goals/" + aliceGoalId)
            .andExpect(status().isNotFound());

        doGet(alice, "/api/summary")
            .andExpect(jsonPath("$.goals", hasSize(1)))
            .andExpect(jsonPath("$.goals[0].name").value("Alice goal"));
    }

    @Test @Order(13)
    void settingsUpdateRejectsAnotherUsersAccountId() throws Exception {
        long aliceAccountId = firstAccountId(alice);
        String payload = """
            {"ownerName":"Bob Renamed","employer":"","payday":25,"salary":5000,
             "billsAllocation":1000,"savingsTarget":1500,"spendingAllowance":1000,
             "accounts":[{"id":%d,"code":"RHB","name":"Stolen","kind":"BILLS","balance":7000},
                         {"code":"GX","name":"GXBank","kind":"SPENDING","balance":400}]}"""
            .formatted(aliceAccountId);

        doPut(bob, "/api/settings", payload).andExpect(status().isNotFound());

        // The plan edit is applied before account ids are checked, so this also
        // proves the transaction actually rolls back.
        doGet(bob, "/api/settings").andExpect(jsonPath("$.plan.ownerName").value("Bob"));
        doGet(alice, "/api/settings")
            .andExpect(jsonPath("$.accounts", hasSize(2)))
            .andExpect(jsonPath("$.accounts[0].name").value("Maybank"));
    }

    @Test @Order(14)
    void settingsUpdateNeverDeletesAnotherUsersAccounts() throws Exception {
        // Bob lists only his own accounts. The delete sweep must consider only his.
        doPut(bob, "/api/settings", """
            {"ownerName":"Bob","employer":"","payday":25,"salary":5000,
             "billsAllocation":1000,"savingsTarget":1500,"spendingAllowance":1000,
             "accounts":[{"id":%d,"code":"RHB","name":"RHB","kind":"BILLS","balance":7000},
                         {"id":%d,"code":"GX","name":"GXBank","kind":"SPENDING","balance":400}]}"""
            .formatted(bobAccountId(0), bobAccountId(2)))
            .andExpect(status().isOk());

        doGet(alice, "/api/settings").andExpect(jsonPath("$.accounts", hasSize(2)));
        doGet(alice, "/api/summary").andExpect(jsonPath("$.money.total").value(3780.00));
    }

    // ---------- sessions ----------

    @Test @Order(15)
    void buyDelaysOnlyTheCallersGoal() throws Exception {
        doPost(bob, "/api/afford/buy", "{\"amount\":200}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.goal.name").value("Bob goal"));

        doGet(alice, "/api/summary").andExpect(jsonPath("$.goals[0].delayMonths").value(0));
    }

    @Test @Order(16)
    void waitPlansAreScoped() throws Exception {
        doPost(bob, "/api/afford/wait", "{\"amount\":60}").andExpect(status().isCreated());
        doGet(bob, "/api/export").andExpect(jsonPath("$.savingPlans", hasSize(1)));
        doGet(alice, "/api/export").andExpect(jsonPath("$.savingPlans", hasSize(0)));
    }

    @Test @Order(17)
    void alicesPasswordChangeLeavesBobSignedIn() throws Exception {
        doPost(alice, "/api/auth/password",
                "{\"currentPassword\":\"alices-password\",\"newPassword\":\"alices-new-password\"}")
            .andExpect(status().isNoContent());

        // Eviction selects victims by principal; a bug there would sign Bob out.
        doGet(bob, "/api/auth/me").andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("bob@example.com"));
        doGet(alice, "/api/auth/me").andExpect(status().isOk());
    }

    @Test @Order(18)
    void logoutEndsOnlyTheCallersSession() throws Exception {
        MockHttpSession aliceSecond = login("alice@example.com", "alices-new-password");
        doPost(aliceSecond, "/api/auth/logout", "").andExpect(status().isNoContent());

        doGet(aliceSecond, "/api/auth/me").andExpect(status().isUnauthorized());
        doGet(bob, "/api/auth/me").andExpect(status().isOk());
    }

    @Test @Order(19)
    void aNewUserSeesNothingAndIsNotConfigured() throws Exception {
        MockHttpSession cara = register("cara@example.com", "caras-password");
        doGet(cara, "/api/setup").andExpect(jsonPath("$.configured").value(false));
        // Not an empty summary — no plan at all.
        doGet(cara, "/api/summary").andExpect(status().isNotFound());
        doGet(cara, "/api/export")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan").doesNotExist())
            .andExpect(jsonPath("$.accounts", hasSize(0)))
            .andExpect(jsonPath("$.goals", hasSize(0)));
    }

    private long firstAccountId(MockHttpSession as) throws Exception {
        return com.jayway.jsonpath.JsonPath
            .parse(doGet(as, "/api/export").andReturn().getResponse().getContentAsString())
            .read("$.accounts[0].id", Long.class);
    }

    private long bobAccountId(int index) throws Exception {
        return com.jayway.jsonpath.JsonPath
            .parse(doGet(bob, "/api/export").andReturn().getResponse().getContentAsString())
            .read("$.accounts[" + index + "].id", Long.class);
    }
}
