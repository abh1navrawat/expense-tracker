package com.tracker.expense.controller;

import com.tracker.expense.model.Category;
import com.tracker.expense.model.Expense;
import com.tracker.expense.model.User;
import com.tracker.expense.repository.CategoryRepository;
import com.tracker.expense.security.SecurityUtils;
import com.tracker.expense.service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private CategoryRepository categoryRepository;

    @GetMapping
    public ResponseEntity<List<Expense>> getExpenses(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(value = "search", required = false) String search) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(expenseService.getFilteredExpensesForUser(userId, categoryId, start, end, search));
    }

    @PostMapping
    public ResponseEntity<?> createExpense(
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("currency") String currency,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam("description") String description,
            @RequestParam("expenseDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseDate) {
        User user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Category category = categoryRepository.findById(categoryId).orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body("The requested Category was not found.");
        }

        Expense expense = new Expense(
                user,
                category,
                amount,
                currency.toUpperCase(),
                BigDecimal.ZERO, // Service layer will automatically perform REST conversion
                description,
                expenseDate
        );

        Expense saved = expenseService.saveExpense(expense, user);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteExpense(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        expenseService.deleteExpense(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        User user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(expenseService.getDashboardStats(user.getId(), user.getBaseCurrency()));
    }
}
