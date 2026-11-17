package ru.budgetly.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import ru.budgetly.budget.BudgetService;
import ru.budgetly.budget.Category;
import ru.budgetly.budget.CategoryType;
import ru.budgetly.budget.Tag;
import ru.budgetly.budget.TransactionRepository;
import ru.budgetly.user.AppUser;
import ru.budgetly.user.Role;
import ru.budgetly.user.UserRepository;
import ru.budgetly.user.UserService;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final int TRANSACTIONS_PER_MAIN_USER = 20;
    private static final int EXTRA_USERS = 3;
    private static final int TRANSACTIONS_PER_EXTRA_USER = 10;

    private final UserRepository userRepository;
    private final UserService userService;
    private final BudgetService budgetService;
    private final TransactionRepository transactionRepository;

    public DataInitializer(UserRepository userRepository,
                           UserService userService,
                           BudgetService budgetService,
                           TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.budgetService = budgetService;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void run(String... args) {
        ensureUser("Админ", "admin@budgetly.local", "admin123", Set.of(Role.ADMIN, Role.USER));
        ensureUser("Анна Иванова", "anna@example.com", "user123", Set.of(Role.USER));
        ensureUser("Игорь Петров", "igor@example.com", "user123", Set.of(Role.USER));

        List<Tag> tags = ensureTags();

        seedUserBudget(
                userService.findByEmail("anna@example.com").orElseThrow(),
                true,
                TRANSACTIONS_PER_MAIN_USER,
                tags
        );
        seedUserBudget(
                userService.findByEmail("igor@example.com").orElseThrow(),
                false,
                TRANSACTIONS_PER_MAIN_USER,
                tags
        );

        for (int i = 1; i <= EXTRA_USERS; i++) {
            String email = "user" + i + "@budgetly.local";
            ensureUser("Тест User " + i, email, "user123", Set.of(Role.USER));
            seedUserBudget(
                    userService.findByEmail(email).orElseThrow(),
                    false,
                    TRANSACTIONS_PER_EXTRA_USER,
                    tags
            );
        }

        System.out.println("[DataInitializer] users≈" + countUsers()
                + ", transactions=" + transactionRepository.count()
                + ", tags=" + budgetService.listTags().size());
    }

    private void ensureUser(String name, String email, String password, Set<Role> roles) {
        if (!userRepository.existsByEmailIgnoreCase(email)) {
            userService.register(name, email, password, roles);
        }
    }

    private List<Tag> ensureTags() {
        String[] names = {
                "работа", "дом", "срочно", "отдых", "здоровье",
                "учеба", "подарки", "спорт", "подписки", "путешествия",
                "авто", "дети", "ремонт", "налоги", "инвестиции"
        };
        List<Tag> tags = new ArrayList<>();
        for (String name : names) {
            tags.add(ensureTag(name));
        }
        return tags;
    }

    private void seedUserBudget(AppUser user, boolean primary, int targetTransactions, List<Tag> tags) {
        if (budgetService.findProfile(user).isEmpty()) {
            budgetService.createProfile(
                    user,
                    primary ? new BigDecimal("50000.00") : new BigDecimal("35000.00"),
                    "RUB",
                    primary ? "Подушка безопасности" : "Цель накоплений"
            );
        }

        List<Category> categories = budgetService.listCategories(user);
        if (categories.isEmpty()) {
            categories = List.of(
                    budgetService.createCategory(user, "Зарплата", CategoryType.INCOME),
                    budgetService.createCategory(user, "Фриланс", CategoryType.INCOME),
                    budgetService.createCategory(user, "Кэшбэк", CategoryType.INCOME),
                    budgetService.createCategory(user, "Продукты", CategoryType.EXPENSE),
                    budgetService.createCategory(user, "Транспорт", CategoryType.EXPENSE),
                    budgetService.createCategory(user, "Кафе", CategoryType.EXPENSE),
                    budgetService.createCategory(user, "Жильё", CategoryType.EXPENSE),
                    budgetService.createCategory(user, "Развлечения", CategoryType.EXPENSE),
                    budgetService.createCategory(user, "Здоровье", CategoryType.EXPENSE),
                    budgetService.createCategory(user, "Одежда", CategoryType.EXPENSE)
            );
        }

        int existing = budgetService.listTransactions(user).size();
        LocalDate base = LocalDate.of(2026, 1, 1);
        for (int i = existing; i < targetTransactions; i++) {
            Category category = categories.get(i % categories.size());
            Tag tagA = tags.get(i % tags.size());
            Tag tagB = tags.get((i * 3) % tags.size());
            List<Long> tagIds = tagA.getId().equals(tagB.getId())
                    ? List.of(tagA.getId())
                    : List.of(tagA.getId(), tagB.getId());

            BigDecimal amount = BigDecimal.valueOf(200 + (i * 37) % 15000)
                    .setScale(2, RoundingMode.HALF_UP);
            LocalDate date = base.plusDays(i);
            String comment = "Операция #" + (i + 1) + " / " + category.getName();

            budgetService.createTransaction(
                    user,
                    category.getId(),
                    amount,
                    date,
                    comment,
                    tagIds
            );
        }
        ensureRecentHistory(user, primary, categories, tags);
    }

    private void ensureRecentHistory(AppUser user, boolean primary, List<Category> categories, List<Tag> tags) {
        LocalDate horizon = LocalDate.now().minusMonths(4);
        boolean hasRecent = budgetService.listTransactions(user).stream()
                .anyMatch(tx -> !tx.getOperatedOn().isBefore(horizon));
        if (hasRecent || categories.isEmpty()) {
            return;
        }
        Category salary = category(categories, "Зарплата");
        Category freelance = category(categories, "Фриланс");
        Category cashback = category(categories, "Кэшбэк");
        Category food = category(categories, "Продукты");
        Category transport = category(categories, "Транспорт");
        Category cafe = category(categories, "Кафе");
        Category home = category(categories, "Жильё");
        Category fun = category(categories, "Развлечения");
        Category health = category(categories, "Здоровье");
        Category clothes = category(categories, "Одежда");
        List<Long> homeTags = List.of(tags.get(1).getId());
        List<Long> foodTags = List.of(tags.get(1).getId(), tags.get(0).getId());
        LocalDate today = LocalDate.now();
        for (int back = 5; back >= 0; back--) {
            LocalDate month = today.minusMonths(back).withDayOfMonth(1);
            add(user, salary, primary ? "86000.00" : "64000.00", day(month, 5), "Зарплата", List.of(tags.get(0).getId()));
            if (back % 2 == 0) {
                add(user, freelance, primary ? "18000.00" : "9000.00", day(month, 18), "Подработка", List.of(tags.get(0).getId()));
            }
            add(user, cashback, "750.00", day(month, 12), "Кэшбэк по карте", List.of());
            add(user, home, primary ? "24000.00" : "18000.00", day(month, 2), "Жильё", homeTags);
            add(user, food, "2400.00", day(month, 4), "Продукты на неделю", foodTags);
            add(user, food, "3100.00", day(month, 11), "Продукты на неделю", foodTags);
            add(user, food, "2800.00", day(month, 19), "Продукты на неделю", foodTags);
            add(user, food, "1900.00", day(month, 26), "Продукты на неделю", foodTags);
            add(user, transport, "1400.00", day(month, 7), "Проезд", List.of(tags.get(10).getId()));
            add(user, transport, "900.00", day(month, 21), "Такси", List.of(tags.get(10).getId()));
            add(user, cafe, "650.00", day(month, 8), "Кофе и обед", List.of());
            add(user, cafe, "1200.00", day(month, 22), "Ужин", List.of(tags.get(3).getId()));
            add(user, fun, "1700.00", day(month, 15), "Кино и выходные", List.of(tags.get(3).getId()));
            add(user, health, back % 2 == 0 ? "2300.00" : "800.00", day(month, 16), "Аптека", List.of(tags.get(4).getId()));
            if (back % 2 == 1) {
                add(user, clothes, primary ? "4500.00" : "2700.00", day(month, 20), "Одежда", List.of());
            }
        }
    }

    private void add(AppUser user, Category category, String amount, LocalDate date, String comment, List<Long> tagIds) {
        if (date.isAfter(LocalDate.now())) {
            return;
        }
        budgetService.createTransaction(user, category.getId(), new BigDecimal(amount), date, comment, tagIds);
    }

    private Category category(List<Category> categories, String name) {
        return categories.stream()
                .filter(item -> item.getName().equals(name))
                .findFirst()
                .orElse(categories.get(0));
    }

    private LocalDate day(LocalDate month, int dayOfMonth) {
        return month.withDayOfMonth(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    private Tag ensureTag(String name) {
        return budgetService.listTags().stream()
                .filter(tag -> tag.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> budgetService.createTag(name));
    }

    private long countUsers() {
        long count = 0;
        for (AppUser ignored : userRepository.findAll()) {
            count++;
        }
        return count;
    }
}
