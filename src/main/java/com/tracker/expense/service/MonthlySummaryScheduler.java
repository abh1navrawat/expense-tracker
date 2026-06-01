package com.tracker.expense.service;

import com.tracker.expense.model.Expense;
import com.tracker.expense.model.User;
import com.tracker.expense.repository.ExpenseRepository;
import com.tracker.expense.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class MonthlySummaryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MonthlySummaryScheduler.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private MailService mailService;

    // Cron expression: runs on the 1st of every month at midnight (0 0 0 1 * *)
    @Scheduled(cron = "0 0 0 1 * *")
    public void runMonthlySummaries() {
        logger.info("Executing scheduled monthly summaries aggregation job...");
        
        LocalDate now = LocalDate.now();
        LocalDate firstOfPreviousMonth = now.minusMonths(1).withDayOfMonth(1);
        LocalDate lastOfPreviousMonth = now.minusMonths(1).withDayOfMonth(now.minusMonths(1).lengthOfMonth());

        List<User> activeUsers = userRepository.findAll();

        for (User user : activeUsers) {
            try {
                // Fetch previous month's expenses for the user using securing range queries
                List<Expense> expenses = expenseRepository.findByUserIdAndDateRange(
                        user.getId(),
                        firstOfPreviousMonth,
                        lastOfPreviousMonth
                );

                if (expenses.isEmpty()) {
                    logger.info("Skipping monthly summary email for user {}: No expenses logged.", user.getEmail());
                    continue;
                }

                // Compute aggregates
                BigDecimal totalSpend = BigDecimal.ZERO;
                Map<String, BigDecimal> categoryBreakdown = new HashMap<>();

                for (Expense e : expenses) {
                    totalSpend = totalSpend.add(e.getConvertedAmountBase());
                    String catName = e.getCategory().getName();
                    categoryBreakdown.put(catName, categoryBreakdown.getOrDefault(catName, BigDecimal.ZERO).add(e.getConvertedAmountBase()));
                }

                // Build HTML email template
                StringBuilder htmlBuilder = new StringBuilder();
                htmlBuilder.append("<html><body style='font-family: Arial, sans-serif; color: #333;'>");
                htmlBuilder.append("<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 8px;'>");
                htmlBuilder.append("<h2 style='color: #0d9488; border-bottom: 2px solid #0d9488; padding-bottom: 10px;'>Monthly Financial Summary</h2>");
                htmlBuilder.append("<p>Hello,</p>");
                htmlBuilder.append("<p>Here is your financial aggregation summary for the month of <strong>")
                        .append(firstOfPreviousMonth.getMonth().name()).append(" ").append(firstOfPreviousMonth.getYear())
                        .append("</strong>.</p>");
                
                htmlBuilder.append("<div style='background-color: #f0fdfa; padding: 15px; border-radius: 6px; margin: 20px 0;'>");
                htmlBuilder.append("<h3 style='margin: 0; color: #115e59;'>Total Spend: ")
                        .append(user.getBaseCurrency()).append(" ").append(totalSpend.setScale(2, RoundingMode.HALF_UP).toString())
                        .append("</h3>");
                htmlBuilder.append("</div>");

                htmlBuilder.append("<h3>Category Breakdown:</h3>");
                htmlBuilder.append("<table style='width: 100%; border-collapse: collapse;'>");
                htmlBuilder.append("<tr style='background-color: #f3f4f6;'>");
                htmlBuilder.append("<th style='text-align: left; padding: 10px; border-bottom: 1px solid #ddd;'>Category</th>");
                htmlBuilder.append("<th style='text-align: right; padding: 10px; border-bottom: 1px solid #ddd;'>Amount Spent</th>");
                htmlBuilder.append("</tr>");

                for (Map.Entry<String, BigDecimal> entry : categoryBreakdown.entrySet()) {
                    htmlBuilder.append("<tr>");
                    htmlBuilder.append("<td style='padding: 10px; border-bottom: 1px solid #ddd;'>").append(entry.getKey()).append("</td>");
                    htmlBuilder.append("<td style='padding: 10px; text-align: right; border-bottom: 1px solid #ddd;'>")
                            .append(user.getBaseCurrency()).append(" ").append(entry.getValue().setScale(2, RoundingMode.HALF_UP).toString())
                            .append("</td>");
                    htmlBuilder.append("</tr>");
                }
                htmlBuilder.append("</table>");

                htmlBuilder.append("<p style='margin-top: 30px; font-size: 12px; color: #777;'>Sent automatically by Expense Tracker with Reporting. To modify settings, access your profile.</p>");
                htmlBuilder.append("</div></body></html>");

                // Dispatch Email
                String subject = "Monthly Financial Report - " + firstOfPreviousMonth.getMonth().name() + " " + firstOfPreviousMonth.getYear();
                mailService.sendMonthlySummaryHtml(user.getEmail(), subject, htmlBuilder.toString());

            } catch (Exception e) {
                logger.error("Error executing monthly summary for user: {}", user.getEmail(), e);
            }
        }
    }
}
