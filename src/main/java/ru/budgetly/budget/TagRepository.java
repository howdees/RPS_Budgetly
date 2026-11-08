package ru.budgetly.budget;

import org.springframework.data.repository.CrudRepository;

import ru.budgetly.dao.TagDao;

public interface TagRepository extends CrudRepository<Tag, Long>, TagDao {
}
