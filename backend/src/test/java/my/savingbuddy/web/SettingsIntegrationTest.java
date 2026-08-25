package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Editing the configuration after onboarding, against the seeded demo household. */
@ActiveProfiles("demo")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettingsIntegrationTest extends ApiTestBase {

    @BeforeAll
    void authenticate() throws Exception {
        session = login(DEMO_EMAIL, DEMO_PASSWORD);
    }
    @Autowired MockMvc mvc;

    /** Demo accounts: 1=Public Bank (BILLS), 2=CIMB (SAVINGS), 3=Hong Leong (SPENDING). */
    private static final String ALL_THREE = """
        {"id": 1, "code": "PB", "name": "Public Bank", "kind": "BILLS", "balance": 6000},
        {"id": 2, "code": "CIMB", "name": "CIMB", "kind": "SAVINGS", "balance": 13700},
        {"id": 3, "code": "HL", "name": "Hong Leong Bank", "kind": "SPENDING", "balance": 2000}""";

    private static String body(String owner, int payday, String spendingAllowance, String accounts) {
        return """
            {
              "ownerName": "%s", "employer": "Kitaro Sdn Bhd", "payday": %d,
              "salary": 4500, "billsAllocation": 1200, "savingsTarget": 2500, "spendingAllowance": %s,
              "accounts": [%s]
            }""".formatted(owner, payday, spendingAllowance, accounts);
    }

    @Test @Order(1)
    void settingsExposeTheCurrentConfigurationAndAccountUsage() throws Exception {
        doGet("/api/settings")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.ownerName").value("Sze Yin"))
            .andExpect(jsonPath("$.plan.payday").value(25))
            .andExpect(jsonPath("$.plan.spendingAllowance").value(2000.00))
            .andExpect(jsonPath("$.accounts", hasSize(3)))
            // Accounts carrying history cannot be removed, and say so.
            .andExpect(jsonPath("$.accounts[0].name").value("Public Bank"))
            .andExpect(jsonPath("$.accounts[0].billCount").value(6))
            .andExpect(jsonPath("$.accounts[0].removable").value(false))
            .andExpect(jsonPath("$.accounts[1].name").value("CIMB"))
            .andExpect(jsonPath("$.accounts[1].transactionCount").value(0))
            .andExpect(jsonPath("$.accounts[1].removable").value(true));
    }

    @Test @Order(2)
    void updatingThePlanFlowsThroughToTheRestOfTheApp() throws Exception {
        doPut("/api/settings", body("Sze Yin Lee", 28, "3000", ALL_THREE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.ownerName").value("Sze Yin Lee"))
            .andExpect(jsonPath("$.plan.payday").value(28));

        // Safe to Spend is derived from the allowance, so raising it moves immediately.
        doGet("/api/summary")
            .andExpect(jsonPath("$.profile.name").value("Sze Yin Lee"))
            .andExpect(jsonPath("$.profile.payday").value(28))
            .andExpect(jsonPath("$.safeToSpend.allowance").value(3000.00))
            .andExpect(jsonPath("$.safeToSpend.amount").value(2426.00));
    }

    @Test @Order(3)
    void renamingAnAccountUpdatesItEverywhere() throws Exception {
        String renamed = ALL_THREE.replace("\"name\": \"Hong Leong Bank\"", "\"name\": \"HLB Everyday\"");
        doPut("/api/settings", body("Sze Yin Lee", 28, "3000", renamed))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accounts[2].name").value("HLB Everyday"));

        doGet("/api/transactions")
            .andExpect(jsonPath("$.transactions[0].accountName").value("HLB Everyday"));
    }

    @Test @Order(4)
    void thePurposeRulesAreEnforced() throws Exception {
        String noSpending = ALL_THREE.replace("\"kind\": \"SPENDING\"", "\"kind\": \"SAVINGS\"");
        doPut("/api/settings", body("Sze Yin Lee", 28, "3000", noSpending))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Exactly one account must be marked SPENDING")));

        String noBills = ALL_THREE.replace("\"kind\": \"BILLS\"", "\"kind\": \"SAVINGS\"");
        doPut("/api/settings", body("Sze Yin Lee", 28, "3000", noBills))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("At least one account must be marked BILLS")));
    }

    @Test @Order(5)
    void anAccountWithHistoryCannotBeSilentlyDropped() throws Exception {
        // Omitting Public Bank would orphan six bills.
        String withoutBillsAccount = """
            {"id": 2, "code": "CIMB", "name": "CIMB", "kind": "BILLS", "balance": 13700},
            {"id": 3, "code": "HL", "name": "HLB Everyday", "kind": "SPENDING", "balance": 2000}""";
        doPut("/api/settings", body("Sze Yin Lee", 28, "3000", withoutBillsAccount))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Public Bank")))
            .andExpect(jsonPath("$.message", containsString("cannot be removed")));

        doGet("/api/settings").andExpect(jsonPath("$.accounts", hasSize(3)));
    }

    @Test @Order(6)
    void anUnusedAccountCanBeRemovedAndANewOneAdded() throws Exception {
        String swapped = """
            {"id": 1, "code": "PB", "name": "Public Bank", "kind": "BILLS", "balance": 6000},
            {"id": 3, "code": "HL", "name": "HLB Everyday", "kind": "SPENDING", "balance": 2000},
            {"code": "MAE", "name": "MAE Savings", "kind": "SAVINGS", "balance": 500}""";
        doPut("/api/settings", body("Sze Yin Lee", 28, "3000", swapped))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accounts", hasSize(3)))
            .andExpect(jsonPath("$.accounts[2].name").value("MAE Savings"))
            .andExpect(jsonPath("$.accounts[2].balance").value(500.00));

        doGet("/api/summary")
            .andExpect(jsonPath("$.money.savings").value(500.00));
    }
}
