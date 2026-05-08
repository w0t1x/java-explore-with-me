Explore With Me — фронтенд v3

Запуск:
1. Убедитесь, что backend работает:
   - http://localhost:8080/actuator/health
   - http://localhost:9090/actuator/health
2. В этой папке запустите:
   node server.js
   или дважды нажмите start-frontend.bat
3. Откройте в браузере:
   http://localhost:4174

Страницы:
- /          — публичный сайт для гостей
- /profile   — личный кабинет пользователя
- /admin     — админ-панель

Важно:
В backend-проекте нет публичных endpoints для реальной регистрации/логина/пароля,
поэтому регистрация и вход во фронтенде сделаны локально через localStorage.
При регистрации фронтенд создаёт реального пользователя в backend через POST /admin/users,
чтобы затем можно было работать с /users/{userId}/events и /users/{userId}/requests.
