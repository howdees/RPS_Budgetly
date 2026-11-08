package ru.budgetly.dao;

import java.util.Optional;

import ru.budgetly.budget.BudgetProfile;
import ru.budgetly.user.AppUser;

/**
 * Доступ к профилю бюджета пользователя (лимит, валюта, цель).
 */
public interface BudgetProfileDao {

    /**
     * @param user владелец профиля
     * @return профиль или пусто
     */
    Optional<BudgetProfile> findByUser(AppUser user);

    /**
     * @param user владелец
     * @return true, если профиль уже создан
     */
    boolean existsByUser(AppUser user);
}
