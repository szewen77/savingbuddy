package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A fresh install — no demo profile, so the database starts empty and must be
 * configured before the app has anything to show.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SetupIntegrationTest extends ApiTestBase {
    @Autowired MockMvc mvc;

    private static final String VALID = """
        {
          "ownerName": "Amir", "employer": "Padu Tech", "payday": 28,
          "salary": 6000, "billsAllocation": 1500, "savingsTarget": 2000, "spendingAllowance": 2500,
          "accounts": [
            {"code": "MBB", "name": "Maybank", "kind": "BILLS", "balance": 3000},
            {"code": "CIMB", "name": "CIMB", "kind": "SAVINGS", "balance": 9000},
            {"code": "RHB", "name": "RHB", "kind": "SPENDING", "balance": 2500}
          ]
        }""";

    @Test @Order(1)
    void aFreshInstallReportsItselfUnconfigured() throws Exception {
        mvc.perform(get("/api/setup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(false))
            .andExpect(jsonPath("$.ownerName").doesNotExist());
    }

    @Test @Order(2)
    void summaryIsUnavailableUntilConfigured() throws Exception {
        mvc.perform(get("/api/summary"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message", containsString("plan")));
    }

    @Test @Order(3)
    void exportOfAnEmptyInstallIsStillValid() throws Exception {
        mvc.perform(get("/api/export"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.app").value("savingbuddy"))
            .andExpect(jsonPath("$.plan").doesNotExist())
            .andExpect(jsonPath("$.accounts").isEmpty());
    }

    @Test @Order(4)
    void setupRequiresExactlyOneSpendingAccount() throws Exception {
        String noSpending = VALID.replace("\"kind\": \"SPENDING\"", "\"kind\": \"SAVINGS\"");
        mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON).content(noSpending))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Exactly one account must be marked SPENDING")));

        String twoSpending = VALID.replace("\"kind\": \"SAVINGS\"", "\"kind\": \"SPENDING\"");
        mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON).content(twoSpending))
            .andExpect(status().isBadRequest());
    }

    @Test @Order(5)
    void setupRejectsMissingAndOutOfRangeFields() throws Exception {
        mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("\"payday\": 28", "\"payday\": 45")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("\"ownerName\": \"Amir\"", "\"ownerName\": \"\"")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replaceAll("\\{\"code\".*?\\}(,)?", "").replace("\"accounts\": [\n            \n          ]", "\"accounts\": []")))
            .andExpect(status().isBadRequest());
    }

    @Test @Order(6)
    void setupValidatesFieldsInsideEachAccount() throws Exception {
        // Nested constraints only fire because @Valid sits on the list's type argument.
        mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("\"name\": \"Maybank\"", "\"name\": \"\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*]", hasItem(containsString("accounts[0].name"))));

        mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("\"balance\": 3000", "\"balance\": -50")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*]", hasItem(containsString("accounts[0].balance"))));
    }

    @Test @Order(7)
    void configuringMakesTheAppUsable() throws Exception {
        mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.ownerName").value("Amir"));

        mvc.perform(get("/api/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.name").value("Amir"))
            .andExpect(jsonPath("$.profile.firstName").value("Amir"))
            .andExpect(jsonPath("$.profile.payday").value(28))
            // Nothing spent yet, so the whole allowance is available.
            .andExpect(jsonPath("$.safeToSpend.amount").value(2500.00))
            .andExpect(jsonPath("$.money.total").value(14500.00))
            .andExpect(jsonPath("$.goals").isEmpty())
            .andExpect(jsonPath("$.bills.items").isEmpty());
    }

    @Test @Order(8)
    void aSecondSetupIsRejected() throws Exception {
        mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("already set up")));
    }

    @Test @Order(9)
    void spendingIsRecordedAgainstTheConfiguredAccount() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":150,\"category\":\"Groceries\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transaction.accountName").value("RHB"))
            .andExpect(jsonPath("$.safeToSpend").value(2350.00));
    }

    @Test @Order(10)
    void exportContainsTheConfiguredDataAndDownloadsAsAFile() throws Exception {
        mvc.perform(get("/api/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("savingbuddy-2026-08-22.json")))
            .andExpect(jsonPath("$.schemaVersion").value(1))
            .andExpect(jsonPath("$.plan.ownerName").value("Amir"))
            .andExpect(jsonPath("$.plan.payday").value(28))
            .andExpect(jsonPath("$.accounts.length()").value(3))
            .andExpect(jsonPath("$.transactions.length()").value(1))
            .andExpect(jsonPath("$.transactions[0].accountName").value("RHB"));
    }
}
