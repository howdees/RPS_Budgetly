package ru.budgetly.budget;

import org.springframework.data.repository.CrudRepository;

import ru.budgetly.dao.TransactionDao;

public interface TransactionRepository extends CrudRepository<MoneyTransaction, Long>, TransactionDao {
}
