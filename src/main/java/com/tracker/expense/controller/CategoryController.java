package com.tracker.expense.controller;

import com.tracker.expense.model.Category;
import com.tracker.expense.model.User;
import com.tracker.expense.security.SecurityUtils;
import com.tracker.expense.service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<List<Category>> getCategories() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(expenseService.getVisibleCategoriesForUser(userId));
    }

    @PostMapping
    public ResponseEntity<Category> createCategory(@RequestParam("name") String name) {
        User user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        Category created = expenseService.saveCustomCategory(name, user);
        return ResponseEntity.ok(created);
    }
}
