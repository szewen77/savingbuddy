package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * THE multi-tenancy guarantee: two users, and every endpoint returns only the
 * caller's data. If a repository finder ever loses its user scope, this class
 * is what catches it.
 */
class IsolationIntegrationTest extends ApiTestBase {
    MockHttpSession alice;
    MockHttpSession bob;

    private static String setup(String name, String allowance, String accounts) {
        return """
            {
              "ownerName": "%s", "employer": "", "payday": 25,
              "salary": 5000, "billsAllocation": 1000, "savingsTarget": 1500, "spendingAllowance": %s,
              "accounts": [%s]
            }""".formatted(name, allowance, accounts);
    }

    @BeforeAll
    void twoConfiguredUsers() throws Exception {
        alice = register("alice@example.com", "alices-password");
        bob = register("bob@example.com", "bobs-password");

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
    }

    @Test
    void summaryIsScoped() throws Exception {
        doGet(alice, "/api/summary")
            .andExpect(jsonPath("$.profile.name").value("Alice"))
            .andExpect(jsonPath("$.accounts", hasSize(2)))
            .andExpect(jsonPath("$.safeToSpend.amount").value(1880.00))
            .andExpect(jsonPath("$.money.total").value(3780.00));
        doGet(bob, "/api/summary")
            .andExpect(jsonPath("$.profile.name").value("Bob"))
            .andExpect(jsonPath("$.accounts", hasSize(3)))
            .andExpect(jsonPath("$.safeToSpend.amount").value(955.00))
            .andExpect(jsonPath("$.money.total").value(19355.00));
    }

    @Test
    void activityIsScoped() throws Exception {
        doGet(alice, "/api/transactions")
            .andExpect(jsonPath("$.transactions", hasSize(1)))
            .andExpect(jsonPath("$.transactions[0].name").value("Alice shop"));
        doGet(bob, "/api/transactions")
            .andExpect(jsonPath("$.transactions", hasSize(1)))
            .andExpect(jsonPath("$.transactions[0].name").value("Bob commute"));
    }

    @Test
    void settingsAndExportAreScoped() throws Exception {
        doGet(alice, "/api/settings")
            .andExpect(jsonPath("$.plan.ownerName").value("Alice"))
            .andExpect(jsonPath("$.accounts", hasSize(2)));
        doGet(alice, "/api/export")
            .andExpect(jsonPath("$.plan.ownerName").value("Alice"))
            .andExpect(jsonPath("$.accounts", hasSize(2)))
            .andExpect(jsonPath("$.transactions", hasSize(1)));
        doGet(bob, "/api/export")
            .andExpect(jsonPath("$.plan.ownerName").value("Bob"))
            .andExpect(jsonPath("$.transactions[0].name").value("Bob commute"));
    }

    @Test
    void insightsAndAffordAreScoped() throws Exception {
        doGet(alice, "/api/insights").andExpect(status().isOk());
        // A user with no goals still gets a real answer — the goal-delay part is
        // simply absent rather than 404-ing the whole request. Bob has no goals.
        doPost(bob, "/api/afford/preview", "{\"amount\":100}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.safeBefore").value(955.00))
            .andExpect(jsonPath("$.safeAfter").value(855.00))
            .andExpect(jsonPath("$.verdict").value("YES"))
            .andExpect(jsonPath("$.goal").doesNotExist());
    }

    @Test
    void aGoalLessUserCanStillBuyAndWait() throws Exception {
        // buy() used to 404 before recording anything, so "Buy Anyway" was dead
        // for any user without goals — which every new account was, until
        // POST /api/goals existed. Bob still has none, so this covers that path.
        doPost(bob, "/api/afford/buy", "{\"amount\":20}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transaction.amount").value(20.00))
            .andExpect(jsonPath("$.goal").doesNotExist())
            .andExpect(jsonPath("$.safeToSpend").value(935.00));

        doPost(bob, "/api/afford/wait", "{\"amount\":60}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.weeks").value(3));
    }

    @Test
    void spendingIntoAnotherUsersAccountIsImpossible() throws Exception {
        // Bob learns one of Alice's account ids and tries to spend from it.
        String aliceExport = doGet(alice, "/api/export").andReturn().getResponse().getContentAsString();
        long aliceAccountId = com.jayway.jsonpath.JsonPath.parse(aliceExport)
            .read("$.accounts[0].id", Long.class);

        doPost(bob, "/api/transactions",
                "{\"amount\":50,\"category\":\"Other\",\"accountId\":" + aliceAccountId + "}")
            .andExpect(status().isNotFound());

        // Alice's balance is untouched.
        doGet(alice, "/api/summary").andExpect(jsonPath("$.money.total").value(3780.00));
    }

    @Test
    void aSecondSetupForTheSameUserStillConflictsButANewUserIsFree() throws Exception {
        doPost(alice, "/api/setup", setup("Alice2", "1000", """
            {"code": "X", "name": "X", "kind": "BILLS", "balance": 1},
            {"code": "Y", "name": "Y", "kind": "SPENDING", "balance": 1}"""))
            .andExpect(status().isConflict());

        MockHttpSession cara = register("cara@example.com", "caras-password");
        doGet(cara, "/api/setup").andExpect(jsonPath("$.configured").value(false));
        doGet(cara, "/api/summary").andExpect(status().isNotFound());
    }
}
