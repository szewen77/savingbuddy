package my.savingbuddy.web;

import jakarta.validation.Valid;
import my.savingbuddy.api.Dtos.*;
import my.savingbuddy.domain.TransactionKind;
import my.savingbuddy.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) { this.transactions = transactions; }

    @GetMapping
    public ActivityResponse list(@RequestParam(required = false) TransactionKind kind) {
        return transactions.activity(kind);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddExpenseResponse add(@Valid @RequestBody AddExpenseRequest request) {
        return transactions.addExpense(request);
    }
}
