package com.tracker.expense.repository;

import com.tracker.expense.model.Category;
import com.tracker.expense.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    // Retrieve system default categories (user_id is NULL) and custom user categories (user_id = user)
    @Query("SELECT c FROM Category c WHERE c.user IS NULL OR c.user.id = :userId")
    List<Category> findAllVisibleToUser(@Param("userId") Long userId);
    
    Optional<Category> findByNameAndUserId(String name, Long userId);
    
    Optional<Category> findByNameAndUserIsNull(String name);
}
