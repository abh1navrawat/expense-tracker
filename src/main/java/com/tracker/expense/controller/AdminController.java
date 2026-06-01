package com.tracker.expense.controller;

import com.tracker.expense.model.Role;
import com.tracker.expense.model.User;
import com.tracker.expense.repository.UserRepository;
import com.tracker.expense.repository.ExpenseRepository;
import com.tracker.expense.security.SecurityUtils;
import com.tracker.expense.service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseService expenseService;

    @GetMapping("/admin")
    public String adminRoot() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        User user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }

        // Fetch administrative details
        List<User> allUsers = userRepository.findAll();
        long totalUsers = allUsers.size();
        
        // Accumulate overall platform expenses
        double totalPlatformExpenses = expenseRepository.findAll().stream()
                .mapToDouble(expense -> expense.getConvertedAmountBase().doubleValue())
                .sum();

        model.addAttribute("user", user);
        model.addAttribute("users", allUsers);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalPlatformExpenses", totalPlatformExpenses);
        model.addAttribute("activeTab", "admin");

        return "admin";
    }

    @PostMapping("/admin/users/{id}/delete")
    public String adminDeleteUser(@PathVariable("id") Long targetUserId, RedirectAttributes redirectAttributes) {
        User currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Enforce safety: Admin cannot delete their own active account
        if (currentUser.getId().equals(targetUserId)) {
            redirectAttributes.addFlashAttribute("error", "You cannot delete your own administrative account.");
            return "redirect:/admin/dashboard";
        }

        userRepository.findById(targetUserId).ifPresent(targetUser -> {
            expenseService.deleteUserAccount(targetUserId);
            redirectAttributes.addFlashAttribute("success", "Account for " + targetUser.getEmail() + " was deleted successfully.");
        });

        return "redirect:/admin/dashboard";
    }
}
