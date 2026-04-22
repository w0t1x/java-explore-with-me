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

### Требования

Для запуска проекта должны быть установлены:

- Java 21
- Maven
- Docker / Docker Compose

---

### 1. Запуск баз данных

Из корня проекта выполните:

```bash
docker compose up -d stats-db ewm-db
```

Проверить, что контейнеры запущены, можно командой:

```bash
docker ps -a
```

---

### 2. Сборка проекта

Из корня проекта выполните:

```bash
mvn clean package -DskipTests
```

---

### 3. Запуск сервисов

> Важно: корневой `pom.xml` является агрегатором модулей, поэтому запускать проект командой  
> `mvn -f pom.xml spring-boot:run` нельзя.  
> Каждый сервис нужно запускать отдельно.

#### 3.1. Запуск `stats-server`

```bash
mvn -f stats-server/pom.xml spring-boot:run
```

Сервис будет доступен по адресу:

```text
http://localhost:9090
```

#### 3.2. Запуск `ewm-main-service`

```bash
mvn -f ewm-main-service/pom.xml spring-boot:run
```

Сервис будет доступен по адресу:

```text
http://localhost:8080
```

---

### 4. Проверка работоспособности

После запуска сервисов проверьте health-check:

- `stats-server` — `http://localhost:9090/actuator/health`
- `ewm-main-service` — `http://localhost:8080/actuator/health`

Корректный ответ:

```json
{"status":"UP"}
```

---

### 5. Проверка API сервиса статистики

Для демонстрации работы `stats-server` можно выполнить следующий PowerShell-скрипт:

```powershell
Write-Host "1) Проверка health stats-server" -ForegroundColor Cyan
curl.exe http://localhost:9090/actuator/health

Write-Host ""
Write-Host "2) Проверка health ewm-main-service" -ForegroundColor Cyan
curl.exe http://localhost:8080/actuator/health

Write-Host ""
Write-Host "3) Отправка тестового hit в stats-server" -ForegroundColor Cyan
$body = @{
    app = "ewm-main-service"
    uri = "/events/1"
    ip = "127.0.0.1"
    timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:9090/hit" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body

Write-Host "Hit успешно отправлен" -ForegroundColor Green

Write-Host ""
Write-Host "4) Чтение статистики по /events/1" -ForegroundColor Cyan
$start = (Get-Date).Date.ToString("yyyy-MM-dd HH:mm:ss")
$end = (Get-Date).Date.AddDays(1).ToString("yyyy-MM-dd HH:mm:ss")

$url = "http://localhost:9090/stats?start=$([uri]::EscapeDataString($start))&end=$([uri]::EscapeDataString($end))&uris=/events/1&unique=false"
curl.exe $url
```

Если сервис работает корректно, в ответе на последний запрос будет возвращён массив со статистикой, например:

```json
[
  {
    "app": "ewm-main-service",
    "uri": "/events/1",
    "hits": 1
  }
]
```

---

### 6. Краткий порядок запуска

```bash
docker compose up -d stats-db ewm-db
mvn clean package -DskipTests
mvn -f stats-server/pom.xml spring-boot:run
mvn -f ewm-main-service/pom.xml spring-boot:run
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
