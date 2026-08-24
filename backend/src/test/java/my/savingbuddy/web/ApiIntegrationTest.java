package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
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
    @Autowired MockMvc mvc;

    @Test @Order(1)
    void summaryReflectsSeededHousehold() throws Exception {
        mvc.perform(get("/api/summary"))
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
        mvc.perform(get("/api/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spentThisMonth").value(944.00))
            .andExpect(jsonPath("$.receivedSincePayday").value(4500.00))
            .andExpect(jsonPath("$.lastPayday").value("2026-07-25"))
            .andExpect(jsonPath("$.transactions", hasSize(13)));

        mvc.perform(get("/api/transactions").param("kind", "INCOME"))
            .andExpect(jsonPath("$.transactions", hasSize(1)))
            .andExpect(jsonPath("$.transactions[0].name", startsWith("Salary")));
    }

    @Test @Order(3)
    void insightsComputeAveragesFromHistory() throws Exception {
        mvc.perform(get("/api/insights"))
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
        mvc.perform(post("/api/afford/preview").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":399}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verdict").value("YES"))
            .andExpect(jsonPath("$.safeAfter").value(1027.00))
            .andExpect(jsonPath("$.savedAfter").value(1601.00))
            .andExpect(jsonPath("$.goal.name").value("Japan Trip"))
            .andExpect(jsonPath("$.goal.currentMonth").value("2027-03"))
            .andExpect(jsonPath("$.goal.newMonth").value("2027-04"))
            .andExpect(jsonPath("$.goal.delayMonths").value(1))
            .andExpect(jsonPath("$.waitPlan.weekly").value(133.00));

        mvc.perform(post("/api/afford/preview").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1500}"))
            .andExpect(jsonPath("$.verdict").value("NO"))
            .andExpect(jsonPath("$.shortfall").value(74.00));

        mvc.perform(post("/api/afford/preview").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":6000}"))
            .andExpect(jsonPath("$.goal.stalls").value(true))
            .andExpect(jsonPath("$.goal.newMonth").doesNotExist());

        mvc.perform(get("/api/summary")).andExpect(jsonPath("$.safeToSpend.amount").value(1426.00));
    }

    @Test @Order(5)
    void validationRejectsBadAmounts() throws Exception {
        mvc.perform(post("/api/afford/preview").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-5}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"));
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("not json"))
            .andExpect(status().isBadRequest());
    }

    @Test @Order(6)
    void addingAnExpenseLowersSafeToSpend() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":26,\"category\":\"Groceries\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transaction.name").value("Groceries"))
            .andExpect(jsonPath("$.transaction.accountName").value("Hong Leong Bank"))
            .andExpect(jsonPath("$.safeToSpend").value(1400.00))
            .andExpect(jsonPath("$.daily").value(140.00));

        mvc.perform(get("/api/summary"))
            .andExpect(jsonPath("$.safeToSpend.amount").value(1400.00))
            .andExpect(jsonPath("$.money.spending").value(1974.00))
            .andExpect(jsonPath("$.recent[0].name").value("Groceries"));
    }

    @Test @Order(7)
    void buyingAnywayDelaysTheFlexibleGoal() throws Exception {
        mvc.perform(post("/api/afford/buy").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":700}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.safeToSpend").value(700.00))
            .andExpect(jsonPath("$.goal.name").value("Japan Trip"))
            .andExpect(jsonPath("$.goal.delayMonths").value(1))
            .andExpect(jsonPath("$.goal.status").value("DELAYED"))
            .andExpect(jsonPath("$.goal.effectiveMonth").value("2027-04"));
    }

    @Test @Order(8)
    void waitAndSaveCreatesAPlan() throws Exception {
        mvc.perform(post("/api/afford/wait").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":300}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.weeks").value(3))
            .andExpect(jsonPath("$.weeklyAmount").value(100.00));
    }
}
