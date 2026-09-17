package com.tracker.expense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.expense.model.Expense;
import com.tracker.expense.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private static final Logger logger = LoggerFactory.getLogger(AiChatService.class);

    @Autowired
    private ExpenseService expenseService;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> processUserQuery(User user, String userQuery) {
        String baseCurrency = user.getBaseCurrency() != null ? user.getBaseCurrency() : "INR";
        FinancialContext context = buildFinancialContext(user.getId(), baseCurrency);

        String aiResponse;
        if (StringUtils.hasText(geminiApiKey)) {
            try {
                aiResponse = callGeminiApi(userQuery, context);
            } catch (Exception ex) {
                logger.warn("Failed to call Gemini API, falling back to local analytics engine: {}", ex.getMessage());
                aiResponse = generateLocalAnalyticsAnswer(userQuery, context);
            }
        } else {
            aiResponse = generateLocalAnalyticsAnswer(userQuery, context);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("response", aiResponse);
        result.put("suggestions", Arrays.asList(
                "Why did I spend more this month?",
                "What is my daily spending average?",
                "How can I cut expenses this month?",
                "Show my recent transactions"
        ));
        return result;
    }

    private FinancialContext buildFinancialContext(Long userId, String baseCurrency) {
        List<Expense> allExpenses = expenseService.getAllExpensesForUser(userId);

        LocalDate now = LocalDate.now();
        YearMonth currentYM = YearMonth.from(now);
        YearMonth previousYM = currentYM.minusMonths(1);

        BigDecimal currentMonthTotal = BigDecimal.ZERO;
        BigDecimal previousMonthTotal = BigDecimal.ZERO;

        Map<String, BigDecimal> currentCatSpend = new HashMap<>();
        Map<String, BigDecimal> previousCatSpend = new HashMap<>();

        List<Expense> currentMonthExpenses = new ArrayList<>();

        for (Expense e : allExpenses) {
            if (e.getExpenseDate() == null) continue;
            YearMonth expYM = YearMonth.from(e.getExpenseDate());
            BigDecimal cost = e.getConvertedAmountBase() != null ? e.getConvertedAmountBase() : e.getAmount();
            String catName = (e.getCategory() != null && StringUtils.hasText(e.getCategory().getName())) 
                    ? e.getCategory().getName() : "Uncategorized";

            if (expYM.equals(currentYM)) {
                currentMonthTotal = currentMonthTotal.add(cost);
                currentCatSpend.put(catName, currentCatSpend.getOrDefault(catName, BigDecimal.ZERO).add(cost));
                currentMonthExpenses.add(e);
            } else if (expYM.equals(previousYM)) {
                previousMonthTotal = previousMonthTotal.add(cost);
                previousCatSpend.put(catName, previousCatSpend.getOrDefault(catName, BigDecimal.ZERO).add(cost));
            }
        }

        // Sort current month top expenses
        currentMonthExpenses.sort((a, b) -> b.getConvertedAmountBase().compareTo(a.getConvertedAmountBase()));
        List<Expense> topCurrentExpenses = currentMonthExpenses.stream().limit(5).collect(Collectors.toList());

        return new FinancialContext(
                baseCurrency,
                currentYM.toString(),
                previousYM.toString(),
                currentMonthTotal,
                previousMonthTotal,
                currentCatSpend,
                previousCatSpend,
                topCurrentExpenses,
                allExpenses
        );
    }

    private String callGeminiApi(String userQuery, FinancialContext context) throws Exception {
        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                geminiModel, geminiApiKey);

        String systemPrompt = String.format(
                "You are Fintrack AI, a helpful, friendly personal financial advisor. Answer the user's prompt concisely based on their spending data.\n" +
                "Base Currency: %s\n" +
                "Current Month (%s) Total Spend: %s %s\n" +
                "Previous Month (%s) Total Spend: %s %s\n" +
                "Current Month Category Breakdown: %s\n" +
                "Previous Month Category Breakdown: %s\n" +
                "Top Recent Transactions: %s\n" +
                "Be direct, polite, clear, and highlight exact numbers in their base currency (%s).",
                context.baseCurrency,
                context.currentMonthStr, context.baseCurrency, context.currentMonthTotal.setScale(2, RoundingMode.HALF_UP),
                context.previousMonthStr, context.baseCurrency, context.previousMonthTotal.setScale(2, RoundingMode.HALF_UP),
                formatCategoryMap(context.currentCatSpend, context.baseCurrency),
                formatCategoryMap(context.previousCatSpend, context.baseCurrency),
                formatTopExpenses(context.topCurrentExpenses, context.baseCurrency),
                context.baseCurrency
        );

        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> contentObj = new HashMap<>();

        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> part1 = new HashMap<>();
        part1.put("text", systemPrompt + "\n\nUser Question: " + userQuery);
        parts.add(part1);

        contentObj.put("parts", parts);
        contents.add(contentObj);
        requestBody.put("contents", contents);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String responseJson = restTemplate.postForObject(url, entity, String.class);

        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
            JsonNode partsNode = candidates.get(0).path("content").path("parts");
            if (partsNode.isArray() && partsNode.size() > 0) {
                return partsNode.get(0).path("text").asText();
            }
        }
        throw new RuntimeException("Unexpected response payload structure from Gemini API");
    }

    private String generateLocalAnalyticsAnswer(String query, FinancialContext ctx) {
        String q = query.toLowerCase();

        BigDecimal diff = ctx.currentMonthTotal.subtract(ctx.previousMonthTotal);
        boolean spentMore = diff.compareTo(BigDecimal.ZERO) > 0;

        // 1. Daily Average / Daily Pace Queries
        if (q.contains("daily") || q.contains("per day") || q.contains("pace") || q.contains("average spend")) {
            LocalDate now = LocalDate.now();
            int dayOfMonth = now.getDayOfMonth();
            int lengthOfMonth = now.lengthOfMonth();

            BigDecimal dailyAvg = dayOfMonth > 0 
                    ? ctx.currentMonthTotal.divide(BigDecimal.valueOf(dayOfMonth), 2, RoundingMode.HALF_UP) 
                    : BigDecimal.ZERO;
            BigDecimal projectedTotal = dailyAvg.multiply(BigDecimal.valueOf(lengthOfMonth)).setScale(2, RoundingMode.HALF_UP);

            return String.format(
                    "📊 **Daily Spending Rate (%s)**\n\n" +
                    "• **Daily Average:** You are spending **%s %.2f / day** (Day %d of %d).\n" +
                    "• **Current Month Total:** %s %.2f\n" +
                    "• **Projected Month-End Total:** **%s %.2f** if this daily pace continues.",
                    ctx.currentMonthStr, ctx.baseCurrency, dailyAvg, dayOfMonth, lengthOfMonth,
                    ctx.baseCurrency, ctx.currentMonthTotal, ctx.baseCurrency, projectedTotal
            );
        }

        // 2. Specific Category Specific Search (e.g. "how much did I spend on Food?")
        String matchedCategory = findMatchingCategoryInQuery(q, ctx);
        if (matchedCategory != null) {
            BigDecimal currCat = ctx.currentCatSpend.getOrDefault(matchedCategory, BigDecimal.ZERO);
            BigDecimal prevCat = ctx.previousCatSpend.getOrDefault(matchedCategory, BigDecimal.ZERO);
            BigDecimal catDiff = currCat.subtract(prevCat);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("🏷️ **%s Spending Breakdown (%s)**\n\n", matchedCategory, ctx.currentMonthStr));
            sb.append(String.format("• **Current Month:** %s %.2f\n", ctx.baseCurrency, currCat));
            sb.append(String.format("• **Previous Month:** %s %.2f\n", ctx.baseCurrency, prevCat));

            if (catDiff.compareTo(BigDecimal.ZERO) > 0) {
                sb.append(String.format("• **Delta:** Increased by **+%s %.2f** compared to last month.\n", ctx.baseCurrency, catDiff));
            } else if (catDiff.compareTo(BigDecimal.ZERO) < 0) {
                sb.append(String.format("• **Delta:** Reduced by **-%s %.2f** compared to last month.\n", ctx.baseCurrency, catDiff.abs()));
            } else {
                sb.append("• **Delta:** Spending remains identical to last month.\n");
            }

            return sb.toString();
        }

        // 3. Lowest Spending Category Queries
        if (q.contains("lowest") || q.contains("least") || q.contains("smallest") || q.contains("minimum")) {
            if (ctx.currentCatSpend.isEmpty()) {
                return String.format("No expenses recorded yet for current month (%s).", ctx.currentMonthStr);
            }
            Map.Entry<String, BigDecimal> lowest = ctx.currentCatSpend.entrySet().stream()
                    .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                    .min(Map.Entry.comparingByValue())
                    .orElse(null);

            if (lowest != null) {
                return String.format("Your lowest spending category for **%s** is **%s** at **%s %.2f**.",
                        ctx.currentMonthStr, lowest.getKey(), ctx.baseCurrency, lowest.getValue());
            }
        }

        // 4. Recent Transactions / Latest Expenses Queries
        if (q.contains("recent") || q.contains("latest") || q.contains("transaction") || q.contains("history")) {
            if (ctx.allExpenses.isEmpty()) {
                return "You have no recorded expenses in your account.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("📋 **Your Latest Transactions:**\n\n");
            int count = Math.min(5, ctx.allExpenses.size());
            for (int i = 0; i < count; i++) {
                Expense exp = ctx.allExpenses.get(i);
                String catName = exp.getCategory() != null ? exp.getCategory().getName() : "Uncategorized";
                sb.append(String.format("• **%s** — %s (%s): %s %.2f\n",
                        exp.getExpenseDate(), exp.getDescription(), catName, ctx.baseCurrency, exp.getConvertedAmountBase()));
            }
            return sb.toString();
        }

        // 5. Cost-Cutting / Saving / Budget Advice Queries
        if (q.contains("save") || q.contains("cut") || q.contains("reduce") || q.contains("tips") || q.contains("advice") || q.contains("budget")) {
            if (ctx.currentCatSpend.isEmpty()) {
                return "To start saving, record your daily expenses to establish a spending baseline.";
            }
            Map.Entry<String, BigDecimal> top = ctx.currentCatSpend.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            BigDecimal topAmt = top != null ? top.getValue() : BigDecimal.ZERO;
            BigDecimal potentialSavings = topAmt.multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);

            StringBuilder sb = new StringBuilder();
            sb.append("💡 **Smart Budgeting & Savings Insights:**\n\n");
            sb.append(String.format("1. **Focus on your top category:** You spent **%s %.2f** on **%s** this month.\n",
                    ctx.baseCurrency, topAmt, top != null ? top.getKey() : "Discretionary items"));
            sb.append(String.format("2. **Potential 20%% Savings:** Cutting back just 20%% on %s would save you **%s %.2f** this month!\n",
                    top != null ? top.getKey() : "this category", ctx.baseCurrency, potentialSavings));
            sb.append(String.format("3. **Month-over-Month Target:** Your current total spend is **%s %.2f**. Aiming to keep it below **%s %.2f** next month will build a healthy surplus.",
                    ctx.baseCurrency, ctx.currentMonthTotal, ctx.baseCurrency, ctx.currentMonthTotal.multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP)));

            return sb.toString();
        }

        // 6. Why / Increase / Compare Queries
        if (q.contains("why") || q.contains("more") || q.contains("compare") || q.contains("increase") || q.contains("higher")) {
            StringBuilder sb = new StringBuilder();
            if (spentMore) {
                sb.append(String.format("You spent **%s %.2f more** in %s (%s %.2f) compared to %s (%s %.2f).\n\n",
                        ctx.baseCurrency, diff, ctx.currentMonthStr, ctx.baseCurrency, ctx.currentMonthTotal,
                        ctx.previousMonthStr, ctx.baseCurrency, ctx.previousMonthTotal));
                sb.append("**Key Drivers of the Increase:**\n");

                Set<String> allCats = new HashSet<>();
                allCats.addAll(ctx.currentCatSpend.keySet());
                allCats.addAll(ctx.previousCatSpend.keySet());

                List<CategoryDelta> deltas = new ArrayList<>();
                for (String cat : allCats) {
                    BigDecimal curr = ctx.currentCatSpend.getOrDefault(cat, BigDecimal.ZERO);
                    BigDecimal prev = ctx.previousCatSpend.getOrDefault(cat, BigDecimal.ZERO);
                    BigDecimal d = curr.subtract(prev);
                    if (d.compareTo(BigDecimal.ZERO) > 0) {
                        deltas.add(new CategoryDelta(cat, d, curr, prev));
                    }
                }
                deltas.sort((a, b) -> b.delta.compareTo(a.delta));

                if (deltas.isEmpty()) {
                    sb.append("• Overall transaction volume increased across multiple smaller items.");
                } else {
                    for (CategoryDelta cd : deltas) {
                        sb.append(String.format("• **%s**: Spent %s %.2f (vs %s %.2f last month, +%s %.2f)\n",
                                cd.categoryName, ctx.baseCurrency, cd.current, ctx.baseCurrency, cd.previous, ctx.baseCurrency, cd.delta));
                    }
                }
            } else {
                BigDecimal absDiff = diff.abs();
                sb.append(String.format("Great news! You spent **%s %.2f LESS** in %s (%s %.2f) compared to %s (%s %.2f).",
                        ctx.baseCurrency, absDiff, ctx.currentMonthStr, ctx.baseCurrency, ctx.currentMonthTotal,
                        ctx.previousMonthStr, ctx.baseCurrency, ctx.previousMonthTotal));
            }
            return sb.toString();
        }

        // 7. Top Category Queries
        if (q.contains("top") || q.contains("category") || q.contains("where") || q.contains("highest") || q.contains("most")) {
            if (ctx.currentCatSpend.isEmpty()) {
                return String.format("No expenses recorded yet for current month (%s).", ctx.currentMonthStr);
            }
            Map.Entry<String, BigDecimal> top = ctx.currentCatSpend.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Your top spending category for **%s** is **%s** at **%s %.2f**.",
                    ctx.currentMonthStr, top.getKey(), ctx.baseCurrency, top.getValue()));

            if (!ctx.topCurrentExpenses.isEmpty()) {
                sb.append("\n\n**Largest Transactions this month:**\n");
                for (Expense exp : ctx.topCurrentExpenses) {
                    sb.append(String.format("• %s — %s: %s %.2f (%s)\n",
                            exp.getExpenseDate(), exp.getDescription(), ctx.baseCurrency, exp.getConvertedAmountBase(), exp.getCategory().getName()));
                }
            }
            return sb.toString();
        }

        // 8. Generic Summary
        return String.format("In **%s**, your total spend is **%s %.2f** across %d categories.\n" +
                        "In comparison, you spent **%s %.2f** in **%s**.\n\n" +
                        "💡 *Try asking:* 'What is my daily average?', 'How much did I spend on Food?', or 'How can I save money?'",
                ctx.currentMonthStr, ctx.baseCurrency, ctx.currentMonthTotal, ctx.currentCatSpend.size(),
                ctx.baseCurrency, ctx.previousMonthTotal, ctx.previousMonthStr);
    }

    private String findMatchingCategoryInQuery(String queryLower, FinancialContext ctx) {
        Set<String> categories = new HashSet<>();
        categories.addAll(ctx.currentCatSpend.keySet());
        categories.addAll(ctx.previousCatSpend.keySet());

        for (String cat : categories) {
            if (queryLower.contains(cat.toLowerCase())) {
                return cat;
            }
        }
        return null;
    }

    private String formatCategoryMap(Map<String, BigDecimal> map, String currency) {
        if (map.isEmpty()) return "None";
        return map.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + currency + " " + entry.getValue().setScale(2, RoundingMode.HALF_UP))
                .collect(Collectors.joining(", "));
    }

    private String formatTopExpenses(List<Expense> expenses, String currency) {
        if (expenses.isEmpty()) return "None";
        return expenses.stream()
                .map(e -> e.getDescription() + " (" + e.getCategory().getName() + "): " + currency + " " + e.getConvertedAmountBase())
                .collect(Collectors.joining("; "));
    }

    private static class FinancialContext {
        final String baseCurrency;
        final String currentMonthStr;
        final String previousMonthStr;
        final BigDecimal currentMonthTotal;
        final BigDecimal previousMonthTotal;
        final Map<String, BigDecimal> currentCatSpend;
        final Map<String, BigDecimal> previousCatSpend;
        final List<Expense> topCurrentExpenses;
        final List<Expense> allExpenses;

        FinancialContext(String baseCurrency, String currentMonthStr, String previousMonthStr,
                         BigDecimal currentMonthTotal, BigDecimal previousMonthTotal,
                         Map<String, BigDecimal> currentCatSpend, Map<String, BigDecimal> previousCatSpend,
                         List<Expense> topCurrentExpenses, List<Expense> allExpenses) {
            this.baseCurrency = baseCurrency;
            this.currentMonthStr = currentMonthStr;
            this.previousMonthStr = previousMonthStr;
            this.currentMonthTotal = currentMonthTotal;
            this.previousMonthTotal = previousMonthTotal;
            this.currentCatSpend = currentCatSpend;
            this.previousCatSpend = previousCatSpend;
            this.topCurrentExpenses = topCurrentExpenses;
            this.allExpenses = allExpenses;
        }
    }

    private static class CategoryDelta {
        final String categoryName;
        final BigDecimal delta;
        final BigDecimal current;
        final BigDecimal previous;

        CategoryDelta(String categoryName, BigDecimal delta, BigDecimal current, BigDecimal previous) {
            this.categoryName = categoryName;
            this.delta = delta;
            this.current = current;
            this.previous = previous;
        }
    }
}
