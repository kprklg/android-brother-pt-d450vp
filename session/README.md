# Выгрузки сессий OpenCode (`session/`)

Все файлы `*.json` в этой директории **обязательно шифруются через `git-crypt`** (правило в `.gitattributes`: `session/*.json filter=git-crypt diff=git-crypt`).

## Как выгрузить текущую сессию

Используйте идемпотентный скрипт из корня репозитория:

```bash
./scripts/export-session.sh
```

Скрипт:
1. Проверит, что `.gitattributes` содержит правило шифрования `session/*.json` через `git-crypt`.
2. Выполнит `opencode session export <SESSION_ID>` в `session/<SESSION_ID>.json`.
3. Проверит `git-crypt status -e`, что файл действительно отмечен как `encrypted`.

## Как расшифровать после клонирования

```bash
git-crypt unlock /путь/к/android-brother-pt-d450vp.git-crypt.key
```
