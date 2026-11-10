package ru.budgetly.budget;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import ru.budgetly.user.AppUser;

@Service
public class TransactionQueryService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<MoneyTransaction> findByOwnerAndCategoryNameCriteria(AppUser owner, String categoryName) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<MoneyTransaction> idRoot = idQuery.from(MoneyTransaction.class);
        Join<MoneyTransaction, Category> category = idRoot.join("category", JoinType.INNER);

        Predicate byOwner = cb.equal(idRoot.get("owner"), owner);
        Predicate byCategory = cb.equal(cb.lower(category.get("name")), categoryName.trim().toLowerCase());
        idQuery.select(idRoot.get("id")).where(cb.and(byOwner, byCategory));
        List<Long> ids = entityManager.createQuery(idQuery).getResultList();
        if (ids.isEmpty()) {
            return List.of();
        }

        CriteriaQuery<MoneyTransaction> dataQuery = cb.createQuery(MoneyTransaction.class);
        Root<MoneyTransaction> root = dataQuery.from(MoneyTransaction.class);
        root.fetch("category", JoinType.INNER);
        root.fetch("tags", JoinType.LEFT);
        dataQuery.select(root).distinct(true)
                .where(root.get("id").in(ids))
                .orderBy(cb.desc(root.get("operatedOn")), cb.desc(root.get("id")));
        return entityManager.createQuery(dataQuery).getResultList();
    }
}
