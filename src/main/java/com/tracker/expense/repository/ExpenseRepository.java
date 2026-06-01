package com.tracker.expense.repository;

import com.tracker.expense.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    
    // STRICT DATA ISOLATION: Queries are filtered on user ID to prevent cross-tenant leakages
    List<Expense> findByUserIdOrderByExpenseDateDesc(Long userId);
    
    @Query("SELECT e FROM Expense e WHERE e.user.id = :userId AND e.expenseDate BETWEEN :start AND :end ORDER BY e.expenseDate DESC")
    List<Expense> findByUserIdAndDateRange(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT e FROM Expense e WHERE e.user.id = :userId AND e.category.id = :categoryId ORDER BY e.expenseDate DESC")
    List<Expense> findByUserIdAndCategory(@Param("userId") Long userId, @Param("categoryId") Long categoryId);

    // Advanced dynamic criteria filter (Native PostgreSQL Query with explicit casts to prevent parameter type-resolution errors)
    @Query(value = "SELECT * FROM expenses e WHERE e.user_id = :userId " +
           "AND (cast(:categoryId as bigint) IS NULL OR e.category_id = :categoryId) " +
           "AND (cast(:start as date) IS NULL OR e.expense_date >= cast(:start as date)) " +
           "AND (cast(:end as date) IS NULL OR e.expense_date <= cast(:end as date)) " +
           "AND (:search IS NULL OR LOWER(e.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY e.expense_date DESC", nativeQuery = true)
    List<Expense> filterExpenses(@Param("userId") Long userId,
                                 @Param("categoryId") Long categoryId,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end,
                                 @Param("search") String search);
}
