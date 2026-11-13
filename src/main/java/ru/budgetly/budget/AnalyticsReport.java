package ru.budgetly.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsReport(
        String period,
        LocalDate from,
        LocalDate to,
        int days,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net,
        Change incomeChange,
        Change expenseChange,
        Change netChange,
        BigDecimal savingsRate,
        BigDecimal avgExpensePerDay,
        int operationCount,
        int expenseCount,
        int expenseDays,
        BigDecimal weekdayExpense,
        BigDecimal weekendExpense,
        int weekendShare,
        BigDecimal limitLeft,
        BigDecimal runwayDays,
        BigDecimal avgCheck,
        BigDecimal limit,
        int limitShare,
        boolean overLimit,
        BigDecimal previousIncome,
        BigDecimal previousExpense,
        BigDecimal projectedExpense,
        String goalTitle,
        List<Slice> expenseCategories,
        List<Slice> incomeCategories,
        List<Slice> tags,
        List<Bucket> timeline,
        List<MonthLine> months,
        List<Slice> weekdays,
        List<Highlight> largest,
        List<String> insights
) {
    public record Change(String text, String tone) {
    }

    public record Slice(String name, BigDecimal amount, int share, int bar) {
    }

    public record Bucket(String label, BigDecimal income, BigDecimal expense, int incomeBar, int expenseBar) {
    }

    public record Highlight(LocalDate date, String category, String comment, BigDecimal amount) {
    }

    public record MonthLine(String label, BigDecimal income, BigDecimal expense, BigDecimal net) {
    }
}
