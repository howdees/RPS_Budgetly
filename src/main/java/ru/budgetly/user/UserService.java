package ru.budgetly.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.budgetly.budget.BudgetService;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BudgetService budgetService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       BudgetService budgetService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.budgetService = budgetService;
    }

    @Transactional
    public AppUser register(String name, String email, String rawPassword, Set<Role> roles) {
        String normalized = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }
        AppUser user = new AppUser(
                name.trim(),
                normalized,
                passwordEncoder.encode(rawPassword),
                roles
        );
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim().toLowerCase());
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        List<AppUser> users = new ArrayList<>();
        userRepository.findAll().forEach(users::add);
        return users;
    }

    @Transactional
    public AppUser updateByAdmin(Long id, String name, String email, boolean enabled,
                                 boolean adminRole, String newPassword) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        String normalized = email.trim().toLowerCase();
        userRepository.findByEmailIgnoreCase(normalized)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Пользователь с таким email уже существует");
                });
        user.setName(name.trim());
        user.setEmail(normalized);
        user.setEnabled(enabled);
        Set<Role> roles = new HashSet<>();
        roles.add(Role.USER);
        if (adminRole) {
            roles.add(Role.ADMIN);
        }
        user.setRoles(roles);
        if (newPassword != null && !newPassword.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        }
        return userRepository.save(user);
    }

    @Transactional
    public void deleteByAdmin(Long id, Long currentAdminId) {
        if (id.equals(currentAdminId)) {
            throw new IllegalArgumentException("Нельзя удалить собственный аккаунт");
        }
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        budgetService.deleteAllDataForUser(user);
        user.getRoles().clear();
        userRepository.delete(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
        return User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toList())
                .build();
    }
}
