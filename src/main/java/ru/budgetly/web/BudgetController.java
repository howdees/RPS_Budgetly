package ru.budgetly.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.budgetly.budget.AnalyticsService;
import ru.budgetly.budget.BudgetProfile;
import ru.budgetly.budget.BudgetService;
import ru.budgetly.budget.CategoryType;
import ru.budgetly.budget.MoneyTransaction;
import ru.budgetly.user.AppUser;
import ru.budgetly.user.UserService;

@Controller
public class BudgetController {

    private final BudgetService budgetService;
    private final UserService userService;
    private final AnalyticsService analyticsService;

    public BudgetController(BudgetService budgetService, UserService userService, AnalyticsService analyticsService) {
        this.budgetService = budgetService;
        this.userService = userService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics")
    public String analytics(@AuthenticationPrincipal UserDetails principal,
                            @RequestParam(defaultValue = "month") String period,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Model model) {
        model.addAttribute("report", analyticsService.build(currentUser(principal), period, from, to));
        return "analytics";
    }

    @GetMapping("/categories")
    public String categories(@AuthenticationPrincipal UserDetails principal, Model model) {
        AppUser user = currentUser(principal);
        model.addAttribute("categories", budgetService.listCategories(user));
        model.addAttribute("types", CategoryType.values());
        return "categories";
    }

    @PostMapping("/categories")
    public String createCategory(@AuthenticationPrincipal UserDetails principal,
                                 @RequestParam String name,
                                 @RequestParam CategoryType type,
                                 RedirectAttributes redirectAttributes) {
        try {
            budgetService.createCategory(currentUser(principal), name, type);
            redirectAttributes.addFlashAttribute("message", "Категория добавлена");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/categories";
    }

    @PostMapping("/categories/{id}/update")
    public String updateCategory(@AuthenticationPrincipal UserDetails principal,
                                 @PathVariable Long id,
                                 @RequestParam String name,
                                 @RequestParam CategoryType type,
                                 RedirectAttributes redirectAttributes) {
        try {
            budgetService.updateCategory(currentUser(principal), id, name, type);
            redirectAttributes.addFlashAttribute("message", "Категория обновлена");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@AuthenticationPrincipal UserDetails principal,
                                 @PathVariable Long id,
                                 RedirectAttributes redirectAttributes) {
        try {
            budgetService.deleteCategory(currentUser(principal), id);
            redirectAttributes.addFlashAttribute("message", "Категория удалена");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/categories";
    }

    @GetMapping("/tags")
    public String tags(Model model) {
        model.addAttribute("tags", budgetService.listTags());
        return "tags";
    }

    @PostMapping("/tags")
    public String createTag(@RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            budgetService.createTag(name);
            redirectAttributes.addFlashAttribute("message", "Тег добавлен");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/tags";
    }

    @PostMapping("/tags/{id}/update")
    public String updateTag(@PathVariable Long id, @RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            budgetService.updateTag(id, name);
            redirectAttributes.addFlashAttribute("message", "Тег обновлён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/tags";
    }

    @PostMapping("/tags/{id}/delete")
    public String deleteTag(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            budgetService.deleteTag(id);
            redirectAttributes.addFlashAttribute("message", "Тег удалён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/tags";
    }

    @GetMapping("/transactions")
    public String transactions(@AuthenticationPrincipal UserDetails principal,
                               @RequestParam(required = false) String tag,
                               @RequestParam(required = false) String category,
                               Model model) {
        AppUser user = currentUser(principal);
        List<MoneyTransaction> items;
        String mode = "all";
        if (tag != null && !tag.isBlank()) {
            items = budgetService.findTransactionsByTagJpql(user, tag);
            mode = "jpql";
        } else if (category != null && !category.isBlank()) {
            items = budgetService.findTransactionsByCategoryCriteria(user, category);
            mode = "criteria";
        } else {
            items = budgetService.listTransactions(user);
        }
        model.addAttribute("transactions", items);
        model.addAttribute("categories", budgetService.listCategories(user));
        model.addAttribute("tags", budgetService.listTags());
        model.addAttribute("tagFilter", tag);
        model.addAttribute("categoryFilter", category);
        model.addAttribute("queryMode", mode);
        model.addAttribute("today", LocalDate.now());
        return "transactions";
    }

    @PostMapping("/transactions")
    public String createTransaction(@AuthenticationPrincipal UserDetails principal,
                                    @RequestParam Long categoryId,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate operatedOn,
                                    @RequestParam(required = false) String comment,
                                    @RequestParam(required = false) Long[] tagIds,
                                    RedirectAttributes redirectAttributes) {
        try {
            budgetService.createTransaction(
                    currentUser(principal),
                    categoryId,
                    amount,
                    operatedOn,
                    comment,
                    tagIds == null ? List.of() : Arrays.asList(tagIds)
            );
            redirectAttributes.addFlashAttribute("message", "Операция добавлена");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/transactions";
    }

    @PostMapping("/transactions/{id}/update")
    public String updateTransaction(@AuthenticationPrincipal UserDetails principal,
                                    @PathVariable Long id,
                                    @RequestParam Long categoryId,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate operatedOn,
                                    @RequestParam(required = false) String comment,
                                    @RequestParam(required = false) Long[] tagIds,
                                    RedirectAttributes redirectAttributes) {
        try {
            budgetService.updateTransaction(
                    currentUser(principal),
                    id,
                    categoryId,
                    amount,
                    operatedOn,
                    comment,
                    tagIds == null ? List.of() : Arrays.asList(tagIds)
            );
            redirectAttributes.addFlashAttribute("message", "Операция обновлена");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/transactions";
    }

    @PostMapping("/transactions/{id}/delete")
    public String deleteTransaction(@AuthenticationPrincipal UserDetails principal,
                                    @PathVariable Long id,
                                    RedirectAttributes redirectAttributes) {
        try {
            budgetService.deleteTransaction(currentUser(principal), id);
            redirectAttributes.addFlashAttribute("message", "Операция удалена");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/transactions";
    }

    @PostMapping("/profile/budget")
    public String createOrUpdateProfile(@AuthenticationPrincipal UserDetails principal,
                                        @RequestParam BigDecimal monthlyLimit,
                                        @RequestParam String currency,
                                        @RequestParam(required = false) String goalTitle,
                                        RedirectAttributes redirectAttributes) {
        AppUser user = currentUser(principal);
        try {
            if (budgetService.findProfile(user).isPresent()) {
                budgetService.updateProfile(user, monthlyLimit, currency, goalTitle);
                redirectAttributes.addFlashAttribute("message", "Профиль бюджета обновлён");
            } else {
                budgetService.createProfile(user, monthlyLimit, currency, goalTitle);
                redirectAttributes.addFlashAttribute("message", "Профиль бюджета создан");
            }
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/budget/delete")
    public String deleteProfile(@AuthenticationPrincipal UserDetails principal, RedirectAttributes redirectAttributes) {
        try {
            budgetService.deleteProfile(currentUser(principal));
            redirectAttributes.addFlashAttribute("message", "Профиль бюджета удалён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    private AppUser currentUser(UserDetails principal) {
        return userService.findByEmail(principal.getUsername()).orElseThrow();
    }
}
