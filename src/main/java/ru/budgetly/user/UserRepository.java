package ru.budgetly.user;

import org.springframework.data.repository.CrudRepository;

import ru.budgetly.dao.UserDao;

public interface UserRepository extends CrudRepository<AppUser, Long>, UserDao {
}
