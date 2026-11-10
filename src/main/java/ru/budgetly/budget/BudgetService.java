package ru.budgetly.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.budgetly.user.AppUser;

@Service
public class BudgetService {

    private final BudgetProfileRepository profileRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionQueryService transactionQueryService;

    public BudgetService(BudgetProfileRepository profileRepository,
                         CategoryRepository categoryRepository,
                         TagRepository tagRepository,
                         TransactionRepository transactionRepository,
                         TransactionQueryService transactionQueryService) {
        this.profileRepository = profileRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.transactionRepository = transactionRepository;
        this.transactionQueryService = transactionQueryService;
    }

    @Transactional(readOnly = true)
    public Optional<BudgetProfile> findProfile(AppUser user) {
        return profileRepository.findByUser(user);
    }

    @Transactional
    public BudgetProfile createProfile(AppUser user, BigDecimal monthlyLimit, String currency, String goalTitle) {
        if (profileRepository.existsByUser(user)) {
            throw new IllegalStateException("Профиль уже создан");
        }
        return profileRepository.save(new BudgetProfile(user, monthlyLimit, currency, goalTitle));
    }

    @Transactional
    public BudgetProfile updateProfile(AppUser user, BigDecimal monthlyLimit, String currency, String goalTitle) {
        BudgetProfile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Профиль не найден"));
        profile.setMonthlyLimit(monthlyLimit);
        profile.setCurrency(currency);
        profile.setGoalTitle(goalTitle);
        return profileRepository.save(profile);
    }

    @Transactional
    public void deleteProfile(AppUser user) {
        BudgetProfile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Профиль не найден"));
        profileRepository.delete(profile);
    }

    @Transactional(readOnly = true)
    public List<Category> listCategories(AppUser user) {
        return categoryRepository.findByOwnerOrderByNameAsc(user);
    }

    @Transactional
    public Category createCategory(AppUser user, String name, CategoryType type) {
        return categoryRepository.save(new Category(name.trim(), type, user));
    }

    @Transactional
    public Category updateCategory(AppUser user, Long id, String name, CategoryType type) {
        Category category = categoryRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
        category.setName(name.trim());
        category.setType(type);
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(AppUser user, Long id) {
        Category category = categoryRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
        if (transactionRepository.countByCategory(category) > 0) {
            throw new IllegalStateException("Сначала удалите операции в этой категории");
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public List<Tag> listTags() {
        return tagRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Tag createTag(String name) {
        String normalized = name.trim();
        if (tagRepository.existsByNameIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Такой тег уже есть");
        }
        return tagRepository.save(new Tag(normalized));
    }

    @Transactional
    public Tag updateTag(Long id, String name) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Тег не найден"));
        String normalized = name.trim();
        tagRepository.findByNameIgnoreCase(normalized)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Такой тег уже есть");
                });
        tag.setName(normalized);
        return tagRepository.save(tag);
    }

    @Transactional
    public void deleteTag(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Тег не найден"));
        for (MoneyTransaction tx : List.copyOf(tag.getTransactions())) {
            tx.getTags().remove(tag);
        }
        tagRepository.delete(tag);
    }

    @Transactional(readOnly = true)
    public List<MoneyTransaction> listTransactions(AppUser user) {
        return transactionRepository.findAllByOwnerWithDetails(user);
    }

    @Transactional(readOnly = true)
    public List<MoneyTransaction> findTransactionsByTagJpql(AppUser user, String tagName) {
        return transactionRepository.findByOwnerAndTagNameJpql(user, tagName);
    }

    @Transactional(readOnly = true)
    public List<MoneyTransaction> findTransactionsByCategoryCriteria(AppUser user, String categoryName) {
        return transactionQueryService.findByOwnerAndCategoryNameCriteria(user, categoryName);
    }

    @Transactional
    public MoneyTransaction createTransaction(AppUser user, Long categoryId, BigDecimal amount,
                                              LocalDate operatedOn, String comment, List<Long> tagIds) {
        Category category = categoryRepository.findByIdAndOwner(categoryId, user)
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
        MoneyTransaction tx = new MoneyTransaction(amount, operatedOn, blankToNull(comment), category, user);
        tx.setTags(resolveTags(tagIds));
        return transactionRepository.save(tx);
    }

    @Transactional
    public MoneyTransaction updateTransaction(AppUser user, Long id, Long categoryId, BigDecimal amount,
                                              LocalDate operatedOn, String comment, List<Long> tagIds) {
        MoneyTransaction tx = transactionRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Операция не найдена"));
        Category category = categoryRepository.findByIdAndOwner(categoryId, user)
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
        tx.setCategory(category);
        tx.setAmount(amount);
        tx.setOperatedOn(operatedOn);
        tx.setComment(blankToNull(comment));
        tx.setTags(resolveTags(tagIds));
        return transactionRepository.save(tx);
    }

    @Transactional
    public void deleteTransaction(AppUser user, Long id) {
        MoneyTransaction tx = transactionRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Операция не найдена"));
        tx.getTags().clear();
        transactionRepository.delete(tx);
    }

    @Transactional(readOnly = true)
    public Optional<MoneyTransaction> findTransaction(AppUser user, Long id) {
        return transactionRepository.findByIdAndOwner(id, user)
                .map(tx -> {
                    tx.getCategory().getName();
                    tx.getTags().size();
                    return tx;
                });
    }

    @Transactional
    public void deleteAllDataForUser(AppUser user) {
        for (MoneyTransaction tx : transactionRepository.findAllByOwnerWithDetails(user)) {
            tx.getTags().clear();
            transactionRepository.delete(tx);
        }
        for (Category category : categoryRepository.findByOwnerOrderByNameAsc(user)) {
            categoryRepository.delete(category);
        }
        profileRepository.findByUser(user).ifPresent(profileRepository::delete);
    }

    private Set<Tag> resolveTags(List<Long> tagIds) {
        Set<Tag> tags = new HashSet<>();
        if (tagIds == null) {
            return tags;
        }
        for (Long tagId : tagIds) {
            tagRepository.findById(tagId).ifPresent(tags::add);
        }
        return tags;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
