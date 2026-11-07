package ru.budgetly.budget;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String name;

    // Обратная сторона N:M — LAZY, иначе EAGER с двух сторон даёт циклическую подгрузку.
    // Жадная загрузка тегов идёт с владеющей стороны MoneyTransaction.tags (EAGER).
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    private Set<MoneyTransaction> transactions = new HashSet<>();

    protected Tag() {
    }

    public Tag(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<MoneyTransaction> getTransactions() {
        return transactions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tag tag) || id == null) {
            return false;
        }
        return id.equals(tag.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
}
