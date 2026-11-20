# Logwise

Android-клиент для Tempo Timesheets на Jira Server/Data Center.

## Возможности

- Вход по Jira URL + логин/пароль (Basic Auth)
- Недельный календарь worklogs
- Быстрый лог времени на задачу по умолчанию (start/end + комментарий)
- Редактирование, удаление, дублирование записи, копирование дня
- Work attributes Tempo (если настроены на сервере)
- Таймер, темы, скрытие выходных

## Сборка

```bat
gradlew.bat :app:assembleDebug
```

APK: `dist/Logwise-debug.apk`

Нужен VPN до корпоративной Jira.
