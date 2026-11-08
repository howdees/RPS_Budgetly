package ru.budgetly.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.budgetly.budget.Category;
import ru.budgetly.budget.MoneyTransaction;
import ru.budgetly.user.AppUser;

/**
 * Доступ к денежным операциям.
 * Списки сразу подгружают категорию и метки, чтобы страница операций не делала отдельный запрос на каждую строку.
 */
public interface TransactionDao {

    /**
     * Все операции пользователя, сначала более новые.
     */
    @Query("""
            select t from MoneyTransaction t
            join fetch t.category c
            left join fetch t.tags
            where t.owner = :owner
            order by t.operatedOn desc, t.id desc
            """)
    List<MoneyTransaction> findAllByOwnerWithDetails(@Param("owner") AppUser owner);

    /**
     * Операции пользователя, у которых есть метка с указанным именем. Запрос на JPQL.
     */
    @Query("""
            select distinct t from MoneyTransaction t
            join fetch t.category
            left join fetch t.tags tag
            where t.owner = :owner and lower(tag.name) = lower(:tagName)
            order by t.operatedOn desc
            """)
    List<MoneyTransaction> findByOwnerAndTagNameJpql(@Param("owner") AppUser owner, @Param("tagName") String tagName);

    /**
     * Операция по идентификатору, только если владелец совпадает.
     */
    Optional<MoneyTransaction> findByIdAndOwner(Long id, AppUser owner);

    /**
     * Сколько операций ссылаются на категорию. Нужно, чтобы не удалить категорию, которая ещё используется.
     */
    long countByCategory(Category category);
}
