package com.tracker.expense.service;

import com.tracker.expense.model.Category;
import com.tracker.expense.model.Expense;
import com.tracker.expense.model.Role;
import com.tracker.expense.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AiChatServiceTest {

    @Mock
    private ExpenseService expenseService;

    @InjectMocks
    private AiChatService aiChatService;

    private User testUser;
    private Category catClothes;
    private Category catFood;
    private Category catTravel;

    @BeforeEach
    void setUp() {
        testUser = new User("test@tracker.com", "password", "INR", Role.ROLE_USER);
        testUser.setId(100L);

        catClothes = new Category("Clothes", testUser);
        catClothes.setId(1L);

        catFood = new Category("Food", testUser);
        catFood.setId(2L);

        catTravel = new Category("Travel", testUser);
        catTravel.setId(3L);
    }

    @Test
    @DisplayName("Test Case 1: 'Why did I spend more this month?' when spend increased due to Clothes")
    void testWhyDidISpendMore_WithIncreasedSpend() {
        LocalDate currentMonthDate = LocalDate.now();
        LocalDate prevMonthDate = currentMonthDate.minusMonths(1);

        // Current month expenses: Clothes = 1500, Food = 500 (Total = 2000)
        Expense exp1 = new Expense(testUser, catClothes, new BigDecimal("1500.00"), "INR", new BigDecimal("1500.00"), "Winter Jacket", currentMonthDate);
        Expense exp2 = new Expense(testUser, catFood, new BigDecimal("500.00"), "INR", new BigDecimal("500.00"), "Groceries", currentMonthDate);

        // Previous month expenses: Food = 300 (Total = 300)
        Expense exp3 = new Expense(testUser, catFood, new BigDecimal("300.00"), "INR", new BigDecimal("300.00"), "Groceries", prevMonthDate);

        when(expenseService.getAllExpensesForUser(100L)).thenReturn(Arrays.asList(exp1, exp2, exp3));

        Map<String, Object> result = aiChatService.processUserQuery(testUser, "Why did I spend more this month?");

        assertNotNull(result);
        String response = (String) result.get("response");
        assertNotNull(response);

        assertTrue(response.contains("more"), "Response should indicate spending more");
        assertTrue(response.contains("1700.00"), "Response should calculate total spend increase of 1700.00");
        assertTrue(response.contains("Clothes"), "Response should highlight Clothes as the primary delta category");
        assertTrue(response.contains("1500.00"), "Response should show Clothes amount of 1500.00");
    }

    @Test
    @DisplayName("Test Case 2: 'Why did I spend more this month?' when spending actually decreased")
    void testWhyDidISpendMore_WithDecreasedSpend() {
        LocalDate currentMonthDate = LocalDate.now();
        LocalDate prevMonthDate = currentMonthDate.minusMonths(1);

        Expense exp1 = new Expense(testUser, catTravel, new BigDecimal("200.00"), "INR", new BigDecimal("200.00"), "Bus ticket", currentMonthDate);
        Expense exp2 = new Expense(testUser, catClothes, new BigDecimal("2000.00"), "INR", new BigDecimal("2000.00"), "Designer Suit", prevMonthDate);

        when(expenseService.getAllExpensesForUser(100L)).thenReturn(Arrays.asList(exp1, exp2));

        Map<String, Object> result = aiChatService.processUserQuery(testUser, "Why did I spend more this month?");

        assertNotNull(result);
        String response = (String) result.get("response");

        assertTrue(response.contains("LESS"), "Response should acknowledge reduced spending");
        assertTrue(response.contains("1800.00"), "Response should calculate 1800.00 savings");
    }

    @Test
    @DisplayName("Test Case 3: 'What is my top spending category?'")
    void testTopCategoryQuery() {
        LocalDate currentMonthDate = LocalDate.now();

        Expense exp1 = new Expense(testUser, catFood, new BigDecimal("800.00"), "INR", new BigDecimal("800.00"), "Dinner out", currentMonthDate);
        Expense exp2 = new Expense(testUser, catClothes, new BigDecimal("2500.00"), "INR", new BigDecimal("2500.00"), "Leather Boots", currentMonthDate);

        when(expenseService.getAllExpensesForUser(100L)).thenReturn(Arrays.asList(exp1, exp2));

        Map<String, Object> result = aiChatService.processUserQuery(testUser, "What is my top spending category?");

        assertNotNull(result);
        String response = (String) result.get("response");

        assertTrue(response.contains("top spending category"), "Response should contain top spending category text");
        assertTrue(response.contains("Clothes"), "Response should identify Clothes as the highest category");
        assertTrue(response.contains("2500.00"), "Response should state the Clothes cost 2500.00");
    }

    @Test
    @DisplayName("Test Case 4: Daily average & spending pace ('What is my daily average?')")
    void testDailyAverageQuery() {
        LocalDate currentMonthDate = LocalDate.now();
        Expense exp1 = new Expense(testUser, catFood, new BigDecimal("3000.00"), "INR", new BigDecimal("3000.00"), "Monthly Groceries", currentMonthDate);

        when(expenseService.getAllExpensesForUser(100L)).thenReturn(Collections.singletonList(exp1));

        Map<String, Object> result = aiChatService.processUserQuery(testUser, "What is my daily average?");

        assertNotNull(result);
        String response = (String) result.get("response");

        assertTrue(response.contains("Daily Spending Rate"), "Response should contain daily spending rate title");
        assertTrue(response.contains("INR"), "Response should output base currency INR");
        assertTrue(response.contains("Projected Month-End Total"), "Response should provide month-end projection");
    }

    @Test
    @DisplayName("Test Case 5: Category-specific query ('How much did I spend on Food?')")
    void testCategorySpecificQuery() {
        LocalDate currentMonthDate = LocalDate.now();
        LocalDate prevMonthDate = currentMonthDate.minusMonths(1);

        Expense exp1 = new Expense(testUser, catFood, new BigDecimal("1200.00"), "INR", new BigDecimal("1200.00"), "Restaurant", currentMonthDate);
        Expense exp2 = new Expense(testUser, catFood, new BigDecimal("800.00"), "INR", new BigDecimal("800.00"), "Supermarket", prevMonthDate);

        when(expenseService.getAllExpensesForUser(100L)).thenReturn(Arrays.asList(exp1, exp2));

        Map<String, Object> result = aiChatService.processUserQuery(testUser, "How much did I spend on Food?");

        assertNotNull(result);
        String response = (String) result.get("response");

        assertTrue(response.contains("Food Spending Breakdown"), "Response title should mention Food category");
        assertTrue(response.contains("1200.00"), "Response should state current month Food spend as 1200.00");
        assertTrue(response.contains("800.00"), "Response should state previous month Food spend as 800.00");
        assertTrue(response.contains("400.00"), "Response should state 400.00 delta increase");
    }

    @Test
    @DisplayName("Test Case 6: Lowest spending category ('What is my lowest expense category?')")
    void testLowestCategoryQuery() {
        LocalDate currentMonthDate = LocalDate.now();

        Expense exp1 = new Expense(testUser, catClothes, new BigDecimal("5000.00"), "INR", new BigDecimal("5000.00"), "Suit", currentMonthDate);
        Expense exp2 = new Expense(testUser, catTravel, new BigDecimal("150.00"), "INR", new BigDecimal("150.00"), "Metro Pass", currentMonthDate);

        when(expenseService.getAllExpensesForUser(100L)).thenReturn(Arrays.asList(exp1, exp2));

        Map<String, Object> result = aiChatService.processUserQuery(testUser, "What is my lowest expense category?");

        assertNotNull(result);
        String response = (String) result.get("response");

        assertTrue(response.contains("lowest spending category"), "Response should mention lowest spending category");
        assertTrue(response.contains("Travel"), "Response should identify Travel as lowest");
        assertTrue(response.contains("150.00"), "Response should show 150.00");
    }

    @Test
    @DisplayName("Test Case 7: Cost-cutting & saving advice ('How can I save money?')")
    void testSavingAdviceQuery() {
        LocalDate currentMonthDate = LocalDate.now();

        Expense exp1 = new Expense(testUser, catClothes, new BigDecimal("4000.00"), "INR", new BigDecimal("4000.00"), "Apparel", currentMonthDate);

        when(expenseService.getAllExpensesForUser(100L)).thenReturn(Collections.singletonList(exp1));

        Map<String, Object> result = aiChatService.processUserQuery(testUser, "How can I cut expenses and save money?");

        assertNotNull(result);
        String response = (String) result.get("response");

        assertTrue(response.contains("Smart Budgeting & Savings Insights"), "Response title should mention savings insights");
        assertTrue(response.contains("Clothes"), "Response should highlight top spending category Clothes");
        assertTrue(response.contains("800.00"), "Response should calculate 20% potential savings of 800.00");
    }

    @Test
    @DisplayName("Test Case 8: Recent transactions query ('Show recent transactions')")
    void testRecentTransactionsQuery() {
        LocalDate currentMonthDate = LocalDate.now();

        Expense exp1 = new Expense(testUser, catFood, new BigDecimal("250.00"), "INR", new BigDecimal("250.00"), "Lunch", currentMonthDate);

        when(expenseService.getAllExpensesForUser(100L)).thenReturn(Collections.singletonList(exp1));

        Map<String, Object> result = aiChatService.processUserQuery(testUser, "Show my recent transactions");

        assertNotNull(result);
        String response = (String) result.get("response");

        assertTrue(response.contains("Your Latest Transactions"), "Response should contain latest transactions title");
        assertTrue(response.contains("Lunch"), "Response should list Lunch item");
        assertTrue(response.contains("250.00"), "Response should display transaction amount");
    }

    @Test
    @DisplayName("Test Case 9: Empty expenses scenario")
    void testEmptyExpenses() {
        when(expenseService.getAllExpensesForUser(100L)).thenReturn(Collections.emptyList());

        Map<String, Object> result = aiChatService.processUserQuery(testUser, "What is my top spending category?");

        assertNotNull(result);
        String response = (String) result.get("response");

        assertTrue(response.contains("No expenses recorded"), "Response should gracefully handle empty expenses");
    }
}
