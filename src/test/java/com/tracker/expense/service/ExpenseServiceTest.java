package com.tracker.expense.service;

import com.tracker.expense.model.Category;
import com.tracker.expense.model.Expense;
import com.tracker.expense.model.Role;
import com.tracker.expense.model.User;
import com.tracker.expense.repository.CategoryRepository;
import com.tracker.expense.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private ExpenseService expenseService;

    private User mockUser;
    private Category mockCategory;

    @BeforeEach
    public void setup() {
        mockUser = new User("user@tracker.com", "password", "USD", Role.ROLE_USER);
        mockUser.setId(1L);
        mockCategory = new Category("Food", null);
        mockCategory.setId(10L);
    }

    @Test
    public void testSaveExpenseWithCurrencyConversion() {
        // Arrange
        // Suppose the user enters a cost of 100 EUR. The base currency is USD.
        // We mock exchangeRateService to convert 100 EUR to 108.50 USD.
        BigDecimal enteredAmount = new BigDecimal("100.00");
        String enteredCurrency = "EUR";
        BigDecimal mockConvertedAmount = new BigDecimal("108.50");

        Expense inputExpense = new Expense(
                null,
                mockCategory,
                enteredAmount,
                enteredCurrency,
                BigDecimal.ZERO, // converted amount base will be calculated by service
                "Business Lunch in Paris",
                LocalDate.now()
        );

        // Mock Currency Converter API call
        when(exchangeRateService.convertCurrency(enteredAmount, enteredCurrency, "USD"))
                .thenReturn(mockConvertedAmount);

        // Mock Persistence
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(101L); // Simulate database PK sequence generation
            return saved;
        });

        // Act
        Expense savedExpense = expenseService.saveExpense(inputExpense, mockUser);

        // Assert
        assertNotNull(savedExpense);
        assertEquals(101L, savedExpense.getId());
        assertEquals(mockUser, savedExpense.getUser());
        assertEquals(mockCategory, savedExpense.getCategory());
        assertEquals(enteredAmount, savedExpense.getAmount());
        assertEquals(enteredCurrency, savedExpense.getCurrency());
        
        // Assert that the service layer successfully called exchangeRateService and populated convertedAmountBase
        assertEquals(mockConvertedAmount, savedExpense.getConvertedAmountBase());
    }
}
