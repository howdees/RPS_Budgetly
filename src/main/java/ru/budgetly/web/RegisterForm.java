package ru.budgetly.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterForm {

    @NotBlank(message = "Укажите имя")
    @Size(max = 80, message = "Имя слишком длинное")
    private String name;

    @NotBlank(message = "Укажите email")
    @Email(message = "Некорректный email")
    private String email;

    @NotBlank(message = "Укажите пароль")
    @Size(min = 6, max = 64, message = "Пароль от 6 до 64 символов")
    private String password;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
