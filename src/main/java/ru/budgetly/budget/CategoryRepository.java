package ru.budgetly.budget;

import org.springframework.data.repository.CrudRepository;

import ru.budgetly.dao.CategoryDao;

public interface CategoryRepository extends CrudRepository<Category, Long>, CategoryDao {
}
