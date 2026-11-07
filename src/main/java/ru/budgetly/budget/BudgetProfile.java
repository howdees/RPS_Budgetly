package ru.budgetly.budget;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import ru.budgetly.user.AppUser;

@Entity
@Table(name = "budget_profiles")
public class BudgetProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyLimit = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String currency = "RUB";

    @Column(length = 255)
    private String goalTitle;

    protected BudgetProfile() {
    }

    public BudgetProfile(AppUser user, BigDecimal monthlyLimit, String currency, String goalTitle) {
        this.user = user;
        this.monthlyLimit = monthlyLimit;
        this.currency = currency;
        this.goalTitle = goalTitle;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getGoalTitle() {
        return goalTitle;
    }

    public void setGoalTitle(String goalTitle) {
        this.goalTitle = goalTitle;
    }
}
