package ru.budgetly.dao;

import java.util.Optional;

import ru.budgetly.user.AppUser;

/**
 * Доступ к учётным записям.
 * Назначение: найти пользователя по почте и проверить, что почта ещё не занята.
 */
public interface UserDao {

    /**
     * @param email почта, сравнение без учёта регистра
     * @return пользователь или пусто, если записи нет
     */
    Optional<AppUser> findByEmailIgnoreCase(String email);

    /**
     * @param email почта, сравнение без учёта регистра
     * @return true, если такая почта уже сохранена
     */
    boolean existsByEmailIgnoreCase(String email);
}
