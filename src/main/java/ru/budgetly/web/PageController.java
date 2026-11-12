package ru.budgetly.web;

import java.math.BigDecimal;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import ru.budgetly.budget.BudgetService;
import ru.budgetly.budget.CategoryType;
import ru.budgetly.budget.MoneyTransaction;
import ru.budgetly.user.AppUser;
import ru.budgetly.user.Role;
import ru.budgetly.user.UserService;

@Controller
public class PageController {

    private final UserService userService;
    private final BudgetService budgetService;

    public PageController(UserService userService, BudgetService budgetService) {
        this.userService = userService;
        this.budgetService = budgetService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/faq")
    public String faq() {
        return "faq";
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (isLoggedIn(authentication)) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model, Authentication authentication) {
        if (isLoggedIn(authentication)) {
            return "redirect:/dashboard";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterForm());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "register";
        }
        try {
            userService.register(form.getName(), form.getEmail(), form.getPassword(), Set.of(Role.USER));
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("email", "exists", ex.getMessage());
            return "register";
        }
        redirectAttributes.addFlashAttribute("registered", true);
        return "redirect:/login";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails principal, Model model) {
        AppUser user = userService.findByEmail(principal.getUsername()).orElseThrow();
        var transactions = budgetService.listTransactions(user);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (MoneyTransaction tx : transactions) {
            if (tx.getCategory().getType() == CategoryType.INCOME) {
                income = income.add(tx.getAmount());
            } else {
                expense = expense.add(tx.getAmount());
            }
        }
        model.addAttribute("income", income);
        model.addAttribute("expense", expense);
        model.addAttribute("balance", income.subtract(expense));
        model.addAttribute("recent", transactions.stream().limit(5).toList());
        model.addAttribute("profile", budgetService.findProfile(user).orElse(null));
        return "dashboard";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal UserDetails principal, Model model) {
        AppUser user = userService.findByEmail(principal.getUsername()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("budgetProfile", budgetService.findProfile(user).orElse(null));
        model.addAttribute("categoryCount", budgetService.listCategories(user).size());
        model.addAttribute("transactionCount", budgetService.listTransactions(user).size());
        return "profile";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }

    private boolean isLoggedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String);
    }
}
