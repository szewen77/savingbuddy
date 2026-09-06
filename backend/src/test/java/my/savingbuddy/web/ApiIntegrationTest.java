package my.savingbuddy.web;

import com.jayway.jsonpath.JsonPath;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Walks the seeded household through the API on the mockup's date (22 Aug 2026). */
@ActiveProfiles("demo")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest extends ApiTestBase {

    @BeforeAll
    void authenticate() throws Exception {
        session = login(DEMO_EMAIL, DEMO_PASSWORD);
    }
    @Autowired MockMvc mvc;

    @Test @Order(1)
    void summaryReflectsSeededHousehold() throws Exception {
        doGet("/api/summary")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.firstName").value("Sze"))
            .andExpect(jsonPath("$.profile.daysToPayday").value(3))
            .andExpect(jsonPath("$.profile.monthLabel").value("August"))
            .andExpect(jsonPath("$.safeToSpend.amount").value(1426.00))
            .andExpect(jsonPath("$.safeToSpend.spentThisMonth").value(574.00))
            .andExpect(jsonPath("$.safeToSpend.daysRemaining").value(10))
            .andExpect(jsonPath("$.safeToSpend.daily").value(142.60))
            .andExpect(jsonPath("$.savings.saved").value(2000.00))
            .andExpect(jsonPath("$.savings.target").value(2500.00))
            .andExpect(jsonPath("$.money.total").value(21700.00))
            .andExpect(jsonPath("$.money.available").value(1426.00))
            .andExpect(jsonPath("$.bills.total").value(6))
            .andExpect(jsonPath("$.bills.remaining").value(1200.00))
            .andExpect(jsonPath("$.bills.items[?(@.paid == false)].name", containsInAnyOrder("PTPTN", "Car Loan", "Insurance", "Utilities")))
            .andExpect(jsonPath("$.goals[0].name").value("Emergency Fund"))
            .andExpect(jsonPath("$.goals[0].status").value("ON_TRACK"))
            .andExpect(jsonPath("$.goals[1].monthsAtPace").value(7))
            .andExpect(jsonPath("$.goals[2].status").value("BEHIND"))
            .andExpect(jsonPath("$.goals[2].behindBy").value(600.00))
            .andExpect(jsonPath("$.goals[2].extraMonthly").value(150.00))
            .andExpect(jsonPath("$.recent", hasSize(4)));
    }

    @Test @Order(2)
    void activityGroupsAndFilters() throws Exception {
        doGet("/api/transactions")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spentThisMonth").value(944.00))
            .andExpect(jsonPath("$.receivedSincePayday").value(4500.00))
            .andExpect(jsonPath("$.lastPayday").value("2026-07-25"))
            .andExpect(jsonPath("$.transactions", hasSize(13)));

        doGet("/api/transactions", "kind", "INCOME")
            .andExpect(jsonPath("$.transactions", hasSize(1)))
            .andExpect(jsonPath("$.transactions[0].name", startsWith("Salary")));
    }

    @Test @Order(3)
    void insightsComputeAveragesFromHistory() throws Exception {
        doGet("/api/insights")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.savingRate", closeTo(0.444, 0.001)))
            .andExpect(jsonPath("$.risingStreak").value(4))
            .andExpect(jsonPath("$.months", hasSize(6)))
            .andExpect(jsonPath("$.months[5].current").value(true))
            .andExpect(jsonPath("$.months[5].label").value("Aug"))
            .andExpect(jsonPath("$.categories[0].name").value("Eating out"))
            .andExpect(jsonPath("$.categories[0].amount").value(244.00))
            .andExpect(jsonPath("$.categories[0].delta").value(84.00))
            .andExpect(jsonPath("$.categories[2].delta").value(-40.00))
            .andExpect(jsonPath("$.observations", hasSize(3)));
    }

    @Test @Order(4)
    void affordPreviewShowsImpactWithoutWriting() throws Exception {
        doPost("/api/afford/preview", "{\"amount\":399}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verdict").value("YES"))
            .andExpect(jsonPath("$.safeAfter").value(1027.00))
            .andExpect(jsonPath("$.savedAfter").value(1601.00))
            .andExpect(jsonPath("$.goal.name").value("Japan Trip"))
            .andExpect(jsonPath("$.goal.currentMonth").value("2027-03"))
            .andExpect(jsonPath("$.goal.newMonth").value("2027-04"))
            .andExpect(jsonPath("$.goal.delayMonths").value(1))
            .andExpect(jsonPath("$.waitPlan.weekly").value(133.00));

        doPost("/api/afford/preview", "{\"amount\":1500}")
            .andExpect(jsonPath("$.verdict").value("NO"))
            .andExpect(jsonPath("$.shortfall").value(74.00));

        doPost("/api/afford/preview", "{\"amount\":6000}")
            .andExpect(jsonPath("$.goal.stalls").value(true))
            .andExpect(jsonPath("$.goal.newMonth").doesNotExist());

        doGet("/api/summary").andExpect(jsonPath("$.safeToSpend.amount").value(1426.00));
    }

    @Test @Order(5)
    void validationRejectsBadAmounts() throws Exception {
        doPost("/api/afford/preview", "{\"amount\":-5}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"));
        doPost("/api/transactions", "{\"amount\":10}")
            .andExpect(status().isBadRequest());
        doPost("/api/transactions", "not json")
            .andExpect(status().isBadRequest());
    }

    @Test @Order(6)
    void addingAnExpenseLowersSafeToSpend() throws Exception {
        doPost("/api/transactions", "{\"amount\":26,\"category\":\"Groceries\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transaction.name").value("Groceries"))
            .andExpect(jsonPath("$.transaction.accountName").value("Hong Leong Bank"))
            .andExpect(jsonPath("$.safeToSpend").value(1400.00))
            .andExpect(jsonPath("$.daily").value(140.00));

        doGet("/api/summary")
            .andExpect(jsonPath("$.safeToSpend.amount").value(1400.00))
            .andExpect(jsonPath("$.money.spending").value(1974.00))
            .andExpect(jsonPath("$.recent[0].name").value("Groceries"));
    }

    @Test @Order(7)
    void buyingAnywayDelaysTheFlexibleGoal() throws Exception {
        doPost("/api/afford/buy", "{\"amount\":700}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.safeToSpend").value(700.00))
            .andExpect(jsonPath("$.goal.name").value("Japan Trip"))
            .andExpect(jsonPath("$.goal.delayMonths").value(1))
            .andExpect(jsonPath("$.goal.status").value("DELAYED"))
            .andExpect(jsonPath("$.goal.effectiveMonth").value("2027-04"));
    }

    @Test @Order(8)
    void waitAndSaveCreatesAPlan() throws Exception {
        doPost("/api/afford/wait", "{\"amount\":300}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.weeks").value(3))
            .andExpect(jsonPath("$.weeklyAmount").value(100.00));
    }

    /**
     * An expense may name any account the user owns, so the month's spending is
     * attributed to the account the money actually left. Safe to Spend is
     * allowance-based and stays global — it falls by the same RM45 either way.
     *
     * <p>The pairing is the point: {@code spentThisMonth} and the spending
     * account's {@code reserved} must now be allowed to disagree. Before, every
     * expense necessarily came from the spending account, so reporting the whole
     * month's total against it happened to be true.
     */
    @Test @Order(9)
    void spendingFromAnotherAccountIsNotAttributedToTheSpendingAccount() throws Exception {
        String before = doGet("/api/summary").andReturn().getResponse().getContentAsString();
        int billsAccountId = JsonPath.<java.util.List<Integer>>read(
            before, "$.accounts[?(@.name=='Public Bank')].id").get(0);

        doPost("/api/transactions",
                "{\"amount\":45,\"category\":\"Haircut\",\"accountId\":" + billsAccountId + "}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transaction.accountName").value("Public Bank"))
            .andExpect(jsonPath("$.transaction.category").value("Haircut"))
            .andExpect(jsonPath("$.safeToSpend").value(655.00));

        doGet("/api/summary")
            .andExpect(jsonPath("$.safeToSpend.spentThisMonth").value(1345.00))
            .andExpect(jsonPath("$.safeToSpend.amount").value(655.00))
            // The ringgit left Public Bank.
            .andExpect(jsonPath("$.accounts[0].name").value("Public Bank"))
            .andExpect(jsonPath("$.accounts[0].balance").value(5955.00))
            // ...so Hong Leong reports only its own 1300, not the full 1345.
            .andExpect(jsonPath("$.accounts[2].name").value("Hong Leong Bank"))
            .andExpect(jsonPath("$.accounts[2].balance").value(1274.00))
            .andExpect(jsonPath("$.accounts[2].reserved").value(1300.00));
    }
}
