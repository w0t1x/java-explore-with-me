# Explore With Me

Сервис для создания и участия в событиях. Позволяет пользователям находить единомышленников, организовывать встречи и получать статистику просмотров/посещений.

---

## Архитектура проекта

Проект построен на **микросервисной архитектуре** и состоит из следующих модулей Maven:

1. **ewm-main-service** (основной сервис) — порт **8080**
    - управление пользователями, категориями, событиями, подборками, запросами на участие
    - работа с комментариями (доп. функциональность)
    - отправляет информацию о просмотрах в сервис статистики

2. **stats-server** (сервис статистики) — порт **9090**
    - принимает и сохраняет «хиты» (app, uri, ip, timestamp)
    - отдаёт агрегированную статистику по заданным параметрам

3. **stats-client**
    - HTTP‑клиент для обращения к **stats-server** из основного сервиса

4. **stats-dto**
    - DTO-модели для обмена данными со статистикой

---

## Технологии

- **Java 21**
- **Spring Boot 3.3.2**
- **Spring Data JPA (Hibernate)**
- **PostgreSQL 16.1**
- **Maven** (многомодульная сборка)
- **Lombok**
- **Spring Actuator** (health/metrics)
- **OpenAPI 3.0** (спецификации API в JSON)

---

## База данных

Используются две базы данных **PostgreSQL 16.1** (поднимаются через Docker Compose):

| Сервис | Контейнер | БД | Порт подключения (host → container) |
|---|---|---|---|
| stats-server | `stats-db` | `stats` | **5432 → 5432** |
| ewm-main-service | `ewm-db` | `ewm` | **5433 → 5432** |

Схема БД создаётся/обновляется Hibernate (`spring.jpa.hibernate.ddl-auto`).

---

## Конфигурация

### Основной сервис (ewm-main-service)

- `server.port` — **8080**
- `spring.datasource.url` — по умолчанию `jdbc:postgresql://localhost:5433/ewm`
- `stats.server.url` — по умолчанию `http://localhost:9090`

### Сервис статистики (stats-server)

- `server.port` — **9090**
- `spring.datasource.url` — по умолчанию `jdbc:postgresql://localhost:5432/stats`

В Docker Compose конфигурация переопределяется переменными окружения:
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`
- `STATS_SERVER_URL` (для основного сервиса)

---

## Запуск проекта

### 1) Запуск через Docker Compose

> Если вы собираете проект из исходников, сначала соберите JAR-файлы.

```bash
mvn clean package
```

Запуск инфраструктуры и сервисов:

```bash
docker compose up -d --build
```

Поднимутся контейнеры:
- `stats-db` (PostgreSQL для статистики)
- `stats-server` (порт **9090**)
- `ewm-db` (PostgreSQL для основного сервиса)
- `ewm-main-service` (порт **8080**)

Проверка доступности (Actuator):

- `http://localhost:8080/actuator/health`
- `http://localhost:9090/actuator/health`

### 2) Локальный запуск без Docker (только БД в контейнерах)

Сначала поднимите базы:

```bash
docker compose up -d stats-db ewm-db
```

Затем запустите сервисы из корня проекта:

```bash
# stats-server
mvn -pl stats-server -am spring-boot:run

# ewm-main-service
mvn -pl ewm-main-service -am spring-boot:run
```

---

## Спецификация API (OpenAPI)

В корне репозитория лежат спецификации:

- `ewm-main-service-spec.json` — основной сервис
- `ewm-stats-service-spec.json` — сервис статистики

Файлы можно открыть в Swagger Editor / Swagger UI или импортировать в Postman.

---

## Краткое описание эндпоинтов

### Основной сервис (ewm-main-service)

**Admin API:**
- **Категории** — `/admin/categories`
- **Пользователи** — `/admin/users`
- **События** — `/admin/events`
- **Подборки** — `/admin/compilations`
- **Комментарии (модерация)** — `/admin/comments`

**Private API:**
- **События пользователя** — `/users/{userId}/events`
- **Запросы на участие** — `/users/{userId}/requests`
- **Комментарии пользователя** — `/users/{userId}/comments` (создание с `eventId` как query‑param)

**Public API:**
- **Категории** — `/categories`
- **События** — `/events`
- **Подборки** — `/compilations`
- **Комментарии к событию** — `/events/{eventId}/comments`

> Примечание: публичные запросы к `/events` и `/events/{id}` пишут статистику просмотров в stats-server.

### Сервис статистики (stats-server)

- **POST** `/hit` — сохранить информацию о запросе (app, uri, ip, timestamp)
- **GET** `/stats` — получить статистику за период (параметры: `start`, `end`, `uris`, `unique`)

---

## Тесты

- Postman‑коллекция для дополнительного функционала (комментарии): `postman/feature.json`
