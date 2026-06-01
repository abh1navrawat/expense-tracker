package com.tracker.expense.service;

import com.tracker.expense.model.Category;
import com.tracker.expense.model.Expense;
import com.tracker.expense.model.ReportJob;
import com.tracker.expense.model.User;
import com.tracker.expense.repository.CategoryRepository;
import com.tracker.expense.repository.ExpenseRepository;
import com.tracker.expense.repository.ReportJobRepository;
import com.tracker.expense.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExchangeRateService exchangeRateService;

    // --- Expense Actions ---

    public Expense saveExpense(Expense expense, User user) {
        expense.setUser(user);
        
        // Automated Currency Conversion: Converts expense cost -> user's profile base currency
        BigDecimal convertedCost = exchangeRateService.convertCurrency(
                expense.getAmount(),
                expense.getCurrency(),
                user.getBaseCurrency()
        );
        expense.setConvertedAmountBase(convertedCost);

        return expenseRepository.save(expense);
    }

    public List<Expense> getAllExpensesForUser(Long userId) {
        return expenseRepository.findByUserIdOrderByExpenseDateDesc(userId);
    }

    public List<Expense> getFilteredExpensesForUser(Long userId, Long categoryId, LocalDate start, LocalDate end, String search) {
        return expenseRepository.filterExpenses(userId, categoryId, start, end, search);
    }

    public Optional<Expense> getExpenseByIdAndUser(Long id, Long userId) {
        return expenseRepository.findById(id)
                .filter(e -> e.getUser().getId().equals(userId));
    }

    public void deleteExpense(Long id, Long userId) {
        getExpenseByIdAndUser(id, userId).ifPresent(e -> expenseRepository.delete(e));
    }

    // --- Category Actions ---

    public List<Category> getVisibleCategoriesForUser(Long userId) {
        return categoryRepository.findAllVisibleToUser(userId);
    }

    public Category saveCustomCategory(String name, User user) {
        Optional<Category> existing = categoryRepository.findByNameAndUserId(name, user.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Check if matching system default categories
        Optional<Category> defaultCat = categoryRepository.findByNameAndUserIsNull(name);
        if (defaultCat.isPresent()) {
            return defaultCat.get();
        }

        return categoryRepository.save(new Category(name, user));
    }

    // --- User & Account Deletion Actions (Cascaded Purges) ---

    @Autowired
    private ReportJobRepository reportJobRepository;

    @jakarta.transaction.Transactional
    public void deleteUserAccount(Long targetUserId) {
        // 1. Purge all reporting jobs belonging to this user
        List<ReportJob> userReports = reportJobRepository.findByUserIdOrderByDateCreatedDesc(targetUserId);
        reportJobRepository.deleteAll(userReports);

        // 2. Purge all logged expenses belonging to this user
        List<Expense> userExpenses = expenseRepository.findByUserIdOrderByExpenseDateDesc(targetUserId);
        expenseRepository.deleteAll(userExpenses);

        // 3. Purge all custom category metadata created by this user
        List<Category> userCategories = categoryRepository.findAllVisibleToUser(targetUserId);
        userCategories.stream()
            .filter(c -> c.getUser() != null && c.getUser().getId().equals(targetUserId))
            .forEach(c -> categoryRepository.delete(c));

        // 4. Finally delete the user identity record
        userRepository.deleteById(targetUserId);
    }

    // --- Statistics Aggregation for Charting ---

    public Map<String, Object> getDashboardStats(Long userId, String baseCurrency) {
        List<Expense> expenses = getAllExpensesForUser(userId);

        BigDecimal totalSpend = BigDecimal.ZERO;
        BigDecimal currentMonthSpend = BigDecimal.ZERO;
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);

        Map<String, BigDecimal> categoryBreakdown = new HashMap<>();
        Map<String, BigDecimal> monthlyTrend = new TreeMap<>(); // SortedTreeMap maps dates sequentially

        for (Expense e : expenses) {
            BigDecimal cost = e.getConvertedAmountBase();
            totalSpend = totalSpend.add(cost);

            // Month spend calculation
            if (!e.getExpenseDate().isBefore(startOfMonth) && !e.getExpenseDate().isAfter(now)) {
                currentMonthSpend = currentMonthSpend.add(cost);
            }

            // Category totals accumulation
            String catName = e.getCategory().getName();
            categoryBreakdown.put(catName, categoryBreakdown.getOrDefault(catName, BigDecimal.ZERO).add(cost));

            // Monthly billing trends (Format: YYYY-MM)
            String yearMonthStr = e.getExpenseDate().getYear() + "-" + String.format("%02d", e.getExpenseDate().getMonthValue());
            monthlyTrend.put(yearMonthStr, monthlyTrend.getOrDefault(yearMonthStr, BigDecimal.ZERO).add(cost));
        }

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalSpend", totalSpend);
        statistics.put("currentMonthSpend", currentMonthSpend);
        statistics.put("baseCurrency", baseCurrency);
        statistics.put("categoryBreakdown", categoryBreakdown);
        statistics.put("monthlyTrend", monthlyTrend);
        statistics.put("recentExpenses", expenses.size() > 5 ? expenses.subList(0, 5) : expenses);

        return statistics;
    }
}
