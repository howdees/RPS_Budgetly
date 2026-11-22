package ru.budgetly.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.budgetly.user.AppUser;
import ru.budgetly.user.UserService;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String users(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin";
    }

    @PostMapping("/users/{id}/update")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam String email,
                         @RequestParam(defaultValue = "false") boolean enabled,
                         @RequestParam(defaultValue = "false") boolean adminRole,
                         @RequestParam(required = false) String newPassword,
                         RedirectAttributes redirectAttributes) {
        try {
            userService.updateByAdmin(id, name, email, enabled, adminRole, newPassword);
            redirectAttributes.addFlashAttribute("message", "Пользователь обновлён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/users/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes redirectAttributes) {
        try {
            AppUser current = userService.findByEmail(principal.getUsername()).orElseThrow();
            userService.deleteByAdmin(id, current.getId());
            redirectAttributes.addFlashAttribute("message", "Пользователь удалён");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin";
    }
}
