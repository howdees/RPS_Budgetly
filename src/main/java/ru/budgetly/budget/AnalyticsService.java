package ru.budgetly.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.budgetly.user.AppUser;

@Service
public class AnalyticsService {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", RU);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("LLL yy", RU);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String[] WEEKDAYS = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    private final BudgetService budgetService;

    public AnalyticsService(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Transactional(readOnly = true)
    public AnalyticsReport build(AppUser user, String period, LocalDate requestedFrom, LocalDate requestedTo) {
        List<MoneyTransaction> all = budgetService.listTransactions(user);
        Range range = resolve(period, requestedFrom, requestedTo, all);
        List<MoneyTransaction> current = between(all, range.from(), range.to());
        long length = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        LocalDate prevTo = range.from().minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(length - 1);
        List<MoneyTransaction> previous = between(all, prevFrom, prevTo);

        BigDecimal income = sum(current, CategoryType.INCOME);
        BigDecimal expense = sum(current, CategoryType.EXPENSE);
        BigDecimal net = income.subtract(expense);
        BigDecimal prevIncome = sum(previous, CategoryType.INCOME);
        BigDecimal prevExpense = sum(previous, CategoryType.EXPENSE);
        BigDecimal prevNet = prevIncome.subtract(prevExpense);

        BudgetProfile profile = budgetService.findProfile(user).orElse(null);
        BigDecimal limit = "month".equals(range.period()) && profile != null ? profile.getMonthlyLimit() : null;
        int limitShare = 0;
        boolean overLimit = false;
        if (limit != null && limit.signum() > 0) {
            limitShare = percent(expense, limit);
            overLimit = expense.compareTo(limit) > 0;
        }

        List<AnalyticsReport.Slice> expenseCategories = slices(current, CategoryType.EXPENSE, true);
        List<AnalyticsReport.Slice> incomeCategories = slices(current, CategoryType.INCOME, true);
        List<AnalyticsReport.Slice> tags = tagSlices(current);
        List<AnalyticsReport.Bucket> timeline = timeline(current, range.from(), range.to());
        List<AnalyticsReport.MonthLine> months = months(current, range.from(), range.to());
        List<AnalyticsReport.Slice> weekdays = weekdays(current);
        List<AnalyticsReport.Highlight> largest = largest(current);
        int expenseCount = count(current, CategoryType.EXPENSE);
        SpendSplit split = spendSplit(current);
        BigDecimal perDay = length == 0 ? BigDecimal.ZERO : expense.divide(BigDecimal.valueOf(length), 2, RoundingMode.HALF_UP);
        BigDecimal runway = perDay.signum() > 0 && net.signum() > 0
                ? net.divide(perDay, 1, RoundingMode.HALF_UP)
                : null;
        BigDecimal limitLeft = limit == null ? null : limit.subtract(expense);

        AnalyticsReport report = new AnalyticsReport(
                range.period(),
                range.from(),
                range.to(),
                (int) length,
                income,
                expense,
                net,
                change(income, prevIncome, true),
                change(expense, prevExpense, false),
                change(net, prevNet, true),
                rate(net, income),
                perDay,
                current.size(),
                expenseCount,
                split.days(),
                split.weekday(),
                split.weekend(),
                percent(split.weekend(), expense),
                limitLeft,
                runway,
                expenseCount == 0 ? BigDecimal.ZERO : expense.divide(BigDecimal.valueOf(expenseCount), 2, RoundingMode.HALF_UP),
                limit,
                Math.min(limitShare, 100),
                overLimit,
                prevIncome,
                prevExpense,
                projectMonth(range, expense, (int) length),
                profile == null ? null : profile.getGoalTitle(),
                expenseCategories,
                incomeCategories,
                tags,
                timeline,
                months,
                weekdays,
                largest,
                List.of()
        );
        return withInsights(report);
    }

    private AnalyticsReport withInsights(AnalyticsReport report) {
        List<String> lines = new ArrayList<>();
        if (report.operationCount() == 0) {
            lines.add("За выбранный период операций нет. Смените диапазон или добавьте трату.");
            return copy(report, lines);
        }
        if (!report.expenseCategories().isEmpty() && report.expense().signum() > 0) {
            AnalyticsReport.Slice top = report.expenseCategories().get(0);
            lines.add("Главная статья расходов — «" + top.name() + "»: " + top.share() + "% трат периода.");
        }
        if (!"flat".equals(report.expenseChange().tone()) && !report.expenseChange().text().equals("нет базы")) {
            lines.add("Расходы к прошлому такому же отрезку: " + report.expenseChange().text() + ".");
        }
        report.weekdays().stream()
                .max(Comparator.comparing(AnalyticsReport.Slice::amount))
                .filter(day -> day.amount().signum() > 0)
                .ifPresent(day -> lines.add("Больше всего денег уходит в " + weekdayPhrase(day.name()) + "."));
        if (report.savingsRate() != null) {
            lines.add("Норма сбережений " + report.savingsRate().stripTrailingZeros().toPlainString()
                    + "%: это доля разницы доходов и расходов в доходах.");
        }
        if (report.projectedExpense() != null && report.limit() != null) {
            if (report.projectedExpense().compareTo(report.limit()) > 0) {
                lines.add("При текущем темпе лимит месяца будет превышен.");
            } else {
                lines.add("При текущем темпе лимит месяца сохранится.");
            }
        } else if (report.limit() != null) {
            if (report.overLimit()) {
                lines.add("Месячный лимит превышен.");
            } else {
                lines.add("Из месячного лимита израсходовано " + percent(report.expense(), report.limit()) + "%.");
            }
        }
        if (report.weekendShare() >= 40 && report.expense().signum() > 0) {
            lines.add("На выходные приходится " + report.weekendShare() + "% расходов.");
        }
        if (report.runwayDays() != null) {
            lines.add("Разница доходов и расходов покрывает около "
                    + report.runwayDays().stripTrailingZeros().toPlainString() + " дней средних трат.");
        }
        if (!report.largest().isEmpty()) {
            AnalyticsReport.Highlight big = report.largest().get(0);
            lines.add("Крупнейшая трата — «" + big.category() + "».");
        }
        return copy(report, lines.stream().limit(5).toList());
    }

    private AnalyticsReport copy(AnalyticsReport report, List<String> insights) {
        return new AnalyticsReport(
                report.period(), report.from(), report.to(), report.days(),
                report.income(), report.expense(), report.net(),
                report.incomeChange(), report.expenseChange(), report.netChange(),
                report.savingsRate(), report.avgExpensePerDay(), report.operationCount(),
                report.expenseCount(), report.expenseDays(), report.weekdayExpense(),
                report.weekendExpense(), report.weekendShare(), report.limitLeft(),
                report.runwayDays(), report.avgCheck(), report.limit(), report.limitShare(),
                report.overLimit(), report.previousIncome(), report.previousExpense(),
                report.projectedExpense(), report.goalTitle(), report.expenseCategories(),
                report.incomeCategories(), report.tags(), report.timeline(), report.months(),
                report.weekdays(), report.largest(), insights
        );
    }

    private Range resolve(String period, LocalDate from, LocalDate to, List<MoneyTransaction> all) {
        LocalDate today = LocalDate.now();
        String key = period == null ? "month" : period;
        return switch (key) {
            case "quarter" -> new Range("quarter", today.with(today.getMonth().firstMonthOfQuarter()).withDayOfMonth(1), today);
            case "half" -> new Range("half", today.minusMonths(5).withDayOfMonth(1), today);
            case "year" -> new Range("year", LocalDate.of(today.getYear(), 1, 1), today);
            case "all" -> {
                LocalDate start = all.stream().map(MoneyTransaction::getOperatedOn).min(LocalDate::compareTo).orElse(today.withDayOfMonth(1));
                LocalDate end = all.stream().map(MoneyTransaction::getOperatedOn).max(LocalDate::compareTo).orElse(today);
                if (end.isAfter(today)) {
                    end = today;
                }
                yield new Range("all", start, end.isBefore(start) ? start : end);
            }
            case "custom" -> {
                LocalDate start = from == null ? today.withDayOfMonth(1) : from;
                LocalDate end = to == null ? today : to;
                if (end.isBefore(start)) {
                    LocalDate tmp = start;
                    start = end;
                    end = tmp;
                }
                yield new Range("custom", start, end);
            }
            default -> new Range("month", today.withDayOfMonth(1), today);
        };
    }

    private List<MoneyTransaction> between(List<MoneyTransaction> all, LocalDate from, LocalDate to) {
        return all.stream()
                .filter(tx -> !tx.getOperatedOn().isBefore(from) && !tx.getOperatedOn().isAfter(to))
                .toList();
    }

    private BigDecimal sum(List<MoneyTransaction> items, CategoryType type) {
        return items.stream()
                .filter(tx -> tx.getCategory().getType() == type)
                .map(MoneyTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int count(List<MoneyTransaction> items, CategoryType type) {
        return (int) items.stream().filter(tx -> tx.getCategory().getType() == type).count();
    }

    private List<AnalyticsReport.Slice> slices(List<MoneyTransaction> items, CategoryType type, boolean groupTail) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (MoneyTransaction tx : items) {
            if (tx.getCategory().getType() != type) {
                continue;
            }
            totals.merge(tx.getCategory().getName(), tx.getAmount(), BigDecimal::add);
        }
        List<Map.Entry<String, BigDecimal>> ordered = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .toList();
        if (groupTail && ordered.size() > 6) {
            List<Map.Entry<String, BigDecimal>> head = new ArrayList<>(ordered.subList(0, 5));
            BigDecimal rest = ordered.subList(5, ordered.size()).stream()
                    .map(Map.Entry::getValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            head.add(Map.entry("Остальное", rest));
            ordered = head;
        }
        BigDecimal total = ordered.stream().map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal max = ordered.stream().map(Map.Entry::getValue).max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        return ordered.stream()
                .map(entry -> new AnalyticsReport.Slice(entry.getKey(), entry.getValue(), percent(entry.getValue(), total), percent(entry.getValue(), max)))
                .toList();
    }

    private List<AnalyticsReport.Slice> tagSlices(List<MoneyTransaction> items) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (MoneyTransaction tx : items) {
            if (tx.getCategory().getType() != CategoryType.EXPENSE) {
                continue;
            }
            for (Tag tag : tx.getTags()) {
                totals.merge(tag.getName(), tx.getAmount(), BigDecimal::add);
            }
        }
        List<Map.Entry<String, BigDecimal>> ordered = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(6)
                .toList();
        BigDecimal max = ordered.stream().map(Map.Entry::getValue).max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        BigDecimal total = ordered.stream().map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return ordered.stream()
                .map(entry -> new AnalyticsReport.Slice(entry.getKey(), entry.getValue(), percent(entry.getValue(), total), percent(entry.getValue(), max)))
                .toList();
    }

    private SpendSplit spendSplit(List<MoneyTransaction> items) {
        BigDecimal weekday = BigDecimal.ZERO;
        BigDecimal weekend = BigDecimal.ZERO;
        Set<LocalDate> days = new HashSet<>();
        for (MoneyTransaction tx : items) {
            if (tx.getCategory().getType() != CategoryType.EXPENSE) {
                continue;
            }
            days.add(tx.getOperatedOn());
            DayOfWeek day = tx.getOperatedOn().getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                weekend = weekend.add(tx.getAmount());
            } else {
                weekday = weekday.add(tx.getAmount());
            }
        }
        return new SpendSplit(days.size(), weekday, weekend);
    }

    private BigDecimal projectMonth(Range range, BigDecimal expense, int elapsedDays) {
        if (!"month".equals(range.period()) || elapsedDays <= 0) {
            return null;
        }
        int length = range.from().lengthOfMonth();
        if (elapsedDays >= length) {
            return expense;
        }
        return expense.multiply(BigDecimal.valueOf(length))
                .divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);
    }

    private List<AnalyticsReport.MonthLine> months(List<MoneyTransaction> items, LocalDate from, LocalDate to) {
        Map<YearMonth, BigDecimal> income = new TreeMap<>();
        Map<YearMonth, BigDecimal> expense = new TreeMap<>();
        YearMonth cursor = YearMonth.from(from);
        YearMonth last = YearMonth.from(to);
        while (!cursor.isAfter(last)) {
            income.put(cursor, BigDecimal.ZERO);
            expense.put(cursor, BigDecimal.ZERO);
            cursor = cursor.plusMonths(1);
        }
        for (MoneyTransaction tx : items) {
            YearMonth key = YearMonth.from(tx.getOperatedOn());
            if (tx.getCategory().getType() == CategoryType.INCOME) {
                income.merge(key, tx.getAmount(), BigDecimal::add);
            } else {
                expense.merge(key, tx.getAmount(), BigDecimal::add);
            }
        }
        List<AnalyticsReport.MonthLine> lines = new ArrayList<>();
        DateTimeFormatter label = DateTimeFormatter.ofPattern("LLLL yyyy", RU);
        for (YearMonth month : income.keySet()) {
            BigDecimal in = income.get(month);
            BigDecimal out = expense.getOrDefault(month, BigDecimal.ZERO);
            lines.add(new AnalyticsReport.MonthLine(label.format(month.atDay(1)), in, out, in.subtract(out)));
        }
        return lines;
    }

    private List<AnalyticsReport.Slice> weekdays(List<MoneyTransaction> items) {
        Map<DayOfWeek, BigDecimal> totals = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            totals.put(day, BigDecimal.ZERO);
        }
        for (MoneyTransaction tx : items) {
            if (tx.getCategory().getType() == CategoryType.EXPENSE) {
                totals.merge(tx.getOperatedOn().getDayOfWeek(), tx.getAmount(), BigDecimal::add);
            }
        }
        BigDecimal total = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal max = totals.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        List<AnalyticsReport.Slice> rows = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            BigDecimal amount = totals.get(day);
            rows.add(new AnalyticsReport.Slice(WEEKDAYS[day.getValue() - 1], amount, percent(amount, total), percent(amount, max)));
        }
        return rows;
    }

    private List<AnalyticsReport.Bucket> timeline(List<MoneyTransaction> items, LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        List<LocalDate> starts = new ArrayList<>();
        List<LocalDate> ends = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (days <= 45) {
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                starts.add(day);
                ends.add(day);
                labels.add(DAY.format(day));
            }
        } else if (days <= 140) {
            LocalDate cursor = from;
            while (!cursor.isAfter(to)) {
                LocalDate end = cursor.plusDays(6);
                if (end.isAfter(to)) {
                    end = to;
                }
                starts.add(cursor);
                ends.add(end);
                labels.add(cursor.getDayOfMonth() + "–" + end.getDayOfMonth());
                cursor = end.plusDays(1);
            }
        } else {
            LocalDate cursor = from.withDayOfMonth(1);
            while (!cursor.isAfter(to)) {
                LocalDate end = cursor.with(TemporalAdjusters.lastDayOfMonth());
                if (end.isAfter(to)) {
                    end = to;
                }
                LocalDate start = cursor.isBefore(from) ? from : cursor;
                starts.add(start);
                ends.add(end);
                labels.add(MONTH.format(start));
                cursor = cursor.plusMonths(1).withDayOfMonth(1);
            }
        }
        List<BigDecimal> incomes = new ArrayList<>();
        List<BigDecimal> expenses = new ArrayList<>();
        BigDecimal max = BigDecimal.ONE;
        for (int i = 0; i < starts.size(); i++) {
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;
            for (MoneyTransaction tx : items) {
                LocalDate date = tx.getOperatedOn();
                if (date.isBefore(starts.get(i)) || date.isAfter(ends.get(i))) {
                    continue;
                }
                if (tx.getCategory().getType() == CategoryType.INCOME) {
                    income = income.add(tx.getAmount());
                } else {
                    expense = expense.add(tx.getAmount());
                }
            }
            incomes.add(income);
            expenses.add(expense);
            if (income.compareTo(max) > 0) {
                max = income;
            }
            if (expense.compareTo(max) > 0) {
                max = expense;
            }
        }
        List<AnalyticsReport.Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            buckets.add(new AnalyticsReport.Bucket(labels.get(i), incomes.get(i), expenses.get(i), percent(incomes.get(i), max), percent(expenses.get(i), max)));
        }
        return buckets;
    }

    private List<AnalyticsReport.Highlight> largest(List<MoneyTransaction> items) {
        return items.stream()
                .filter(tx -> tx.getCategory().getType() == CategoryType.EXPENSE)
                .sorted(Comparator.comparing(MoneyTransaction::getAmount).reversed())
                .limit(5)
                .map(tx -> new AnalyticsReport.Highlight(
                        tx.getOperatedOn(),
                        tx.getCategory().getName(),
                        tx.getComment() == null ? "" : tx.getComment(),
                        tx.getAmount()
                ))
                .toList();
    }

    private AnalyticsReport.Change change(BigDecimal current, BigDecimal previous, boolean growthIsGood) {
        if (previous.signum() == 0 && current.signum() == 0) {
            return new AnalyticsReport.Change("без изменений", "flat");
        }
        if (previous.signum() == 0) {
            return new AnalyticsReport.Change("нет базы", "flat");
        }
        BigDecimal delta = current.subtract(previous)
                .multiply(HUNDRED)
                .divide(previous.abs(), 0, RoundingMode.HALF_UP);
        int value = delta.intValue();
        if (value == 0) {
            return new AnalyticsReport.Change("без изменений", "flat");
        }
        boolean up = value > 0;
        String text = (up ? "+" : "") + value + "%";
        String tone = up == growthIsGood ? "good" : "bad";
        return new AnalyticsReport.Change(text, tone);
    }

    private BigDecimal rate(BigDecimal net, BigDecimal income) {
        if (income.signum() <= 0) {
            return null;
        }
        return net.multiply(HUNDRED).divide(income, 0, RoundingMode.HALF_UP);
    }

    private int percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0 || part == null || part.signum() == 0) {
            return 0;
        }
        return part.multiply(HUNDRED).divide(whole, 0, RoundingMode.HALF_UP).intValue();
    }

    private String weekdayPhrase(String shortName) {
        return switch (shortName) {
            case "Пн" -> "понедельник";
            case "Вт" -> "вторник";
            case "Ср" -> "среду";
            case "Чт" -> "четверг";
            case "Пт" -> "пятницу";
            case "Сб" -> "субботу";
            case "Вс" -> "воскресенье";
            default -> shortName;
        };
    }

    private record Range(String period, LocalDate from, LocalDate to) {
    }

    private record SpendSplit(int days, BigDecimal weekday, BigDecimal weekend) {
    }
}
