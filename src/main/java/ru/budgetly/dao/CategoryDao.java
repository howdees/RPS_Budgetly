package ru.budgetly.dao;

import java.util.List;
import java.util.Optional;

import ru.budgetly.budget.Category;
import ru.budgetly.user.AppUser;

/**
 * Доступ к категориям доходов и расходов конкретного пользователя.
 */
public interface CategoryDao {

    /**
     * Список категорий владельца по имени по возрастанию.
     */
    List<Category> findByOwnerOrderByNameAsc(AppUser owner);

    /**
     * Категория только если она принадлежит указанному пользователю.
     */
    Optional<Category> findByIdAndOwner(Long id, AppUser owner);
}
