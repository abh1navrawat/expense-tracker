package com.tracker.expense;

import com.tracker.expense.model.Category;
import com.tracker.expense.model.Role;
import com.tracker.expense.model.User;
import com.tracker.expense.repository.CategoryRepository;
import com.tracker.expense.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication
public class ExpenseTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseTrackerApplication.class, args);
    }

    @Bean
    public CommandLineRunner initData(UserRepository userRepository, CategoryRepository categoryRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Seed global system-default categories (user_id is NULL)
            List<String> systemDefaults = Arrays.asList("Food", "Rent", "Travel", "Utilities", "Entertainment", "Healthcare", "Salary");
            for (String catName : systemDefaults) {
                if (categoryRepository.findByNameAndUserIsNull(catName).isEmpty()) {
                    categoryRepository.save(new Category(catName, null));
                }
            }

            // 2. Seed standard users (Username = email)
            userRepository.findByEmail("user@tracker.com").ifPresentOrElse(
                user -> {
                    if ("USD".equals(user.getBaseCurrency())) {
                        user.setBaseCurrency("INR");
                        userRepository.save(user);
                    }
                },
                () -> {
                    User standardUser = new User(
                            "user@tracker.com",
                            passwordEncoder.encode("password"),
                            "INR",
                            Role.ROLE_USER
                    );
                    userRepository.save(standardUser);
                }
            );

            userRepository.findByEmail("admin@tracker.com").ifPresentOrElse(
                admin -> {
                    if ("USD".equals(admin.getBaseCurrency())) {
                        admin.setBaseCurrency("INR");
                        userRepository.save(admin);
                    }
                },
                () -> {
                    User adminUser = new User(
                            "admin@tracker.com",
                            passwordEncoder.encode("password"),
                            "INR",
                            Role.ROLE_ADMIN
                    );
                    userRepository.save(adminUser);
                }
            );
        };
    }
}
