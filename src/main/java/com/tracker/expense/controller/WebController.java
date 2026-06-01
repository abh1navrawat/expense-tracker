package com.tracker.expense.controller;

import com.tracker.expense.model.Category;
import com.tracker.expense.model.User;
import com.tracker.expense.security.SecurityUtils;
import com.tracker.expense.service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class WebController {

    @Autowired
    private ExpenseService expenseService;

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        User user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }
        
        List<Category> categories = expenseService.getVisibleCategoriesForUser(user.getId());
        model.addAttribute("categories", categories);
        model.addAttribute("user", user);
        model.addAttribute("activeTab", "dashboard");

        return "dashboard";
    }

    @GetMapping("/expenses")
    public String expenses(Model model) {
        User user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }

        List<Category> categories = expenseService.getVisibleCategoriesForUser(user.getId());
        model.addAttribute("categories", categories);
        model.addAttribute("user", user);
        model.addAttribute("activeTab", "expenses");

        return "expenses";
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        User user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        model.addAttribute("activeTab", "analytics");

        return "analytics";
    }

    @PostMapping("/account/delete")
    public String deleteOwnAccount(jakarta.servlet.http.HttpServletResponse response) {
        User user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }

        // Execute dynamic purge of all transaction aggregates, reporting items and account properties
        expenseService.deleteUserAccount(user.getId());

        // Flush active cookies
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt_token", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return "redirect:/register?deleted=true";
    }
}
