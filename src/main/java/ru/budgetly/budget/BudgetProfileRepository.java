package ru.budgetly.budget;

import org.springframework.data.repository.CrudRepository;

import ru.budgetly.dao.BudgetProfileDao;

public interface BudgetProfileRepository extends CrudRepository<BudgetProfile, Long>, BudgetProfileDao {
}
