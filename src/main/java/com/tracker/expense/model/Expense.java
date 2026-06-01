package com.tracker.expense.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "converted_amount_base", nullable = false)
    private BigDecimal convertedAmountBase;

    @Column(nullable = false)
    private String description;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    // Constructors
    public Expense() {}

    public Expense(User user, Category category, BigDecimal amount, String currency, BigDecimal convertedAmountBase, String description, LocalDate expenseDate) {
        this.user = user;
        this.category = category;
        this.amount = amount;
        this.currency = currency;
        this.convertedAmountBase = convertedAmountBase;
        this.description = description;
        this.expenseDate = expenseDate;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getConvertedAmountBase() { return convertedAmountBase; }
    public void setConvertedAmountBase(BigDecimal convertedAmountBase) { this.convertedAmountBase = convertedAmountBase; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
}
