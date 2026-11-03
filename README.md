# Budgetly

Учёт личного бюджета: операции, категории, метки, профиль, аналитика.

## Запуск

```
mvnw.cmd spring-boot:run
```

http://localhost:8080

## Демо-учётные записи

- anna@example.com / user123  
- admin@budgetly.local / admin123  

## Структура пакетов

- `ru.budgetly.budget` — бюджет и аналитика  
- `ru.budgetly.user` — пользователи и роли  
- `ru.budgetly.dao` — доступ к данным  
- `ru.budgetly.web` — контроллеры и формы  

Скрипт схемы БД: `src/main/resources/db/schema.sql`
