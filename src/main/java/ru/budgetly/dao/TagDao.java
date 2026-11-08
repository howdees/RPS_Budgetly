package ru.budgetly.dao;

import java.util.List;
import java.util.Optional;

import ru.budgetly.budget.Tag;

/**
 * Доступ к меткам операций. Метки общие для системы.
 */
public interface TagDao {

    /**
     * Все метки по имени по возрастанию.
     */
    List<Tag> findAllByOrderByNameAsc();

    /**
     * @param name имя метки без учёта регистра
     */
    Optional<Tag> findByNameIgnoreCase(String name);

    /**
     * @param name имя метки без учёта регистра
     * @return true, если метка с таким именем уже есть
     */
    boolean existsByNameIgnoreCase(String name);
}
