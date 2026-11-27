# Шаг 4. Работа с Git, git-crypt, выгрузкой сессий и релизами

## 1. Шифрование чувствительных данных (`git-crypt`)

Репозиторий `kprklg/android-brother-pt-d450vp` является **публичным**, поэтому все секреты и выгрузки сессий **обязательно** шифруются через `git-crypt` до попадания в коммит.

Правила в `.gitattributes`:
```gitattributes
group_vars/all/secrets.yml filter=git-crypt diff=git-crypt
group_vars/**/secrets.yml filter=git-crypt diff=git-crypt
session/*.json filter=git-crypt diff=git-crypt
session/**/*.json filter=git-crypt diff=git-crypt
*.secret filter=git-crypt diff=git-crypt
*.key filter=git-crypt diff=git-crypt
```

### Где хранится ключ `git-crypt`
Симметричный ключ вынесен **вне репозитория**:
```text
/home/android-brother-pt-d450vp/.keys/android-brother-pt-d450vp.git-crypt.key
```
Права доступа: `0600`, владелец `android-brother-pt-d450vp`.

> **Важно:** сохраните резервную копию этого ключа в надёжном месте (менеджер паролей / зашифрованный носитель). Без ключа невозможно расшифровать `group_vars/all/secrets.yml` и `session/*.json` после клонирования.

### Расшифровка после `git clone`
```bash
git clone https://github.com/kprklg/android-brother-pt-d450vp.git
cd android-brother-pt-d450vp
git-crypt unlock /путь/к/android-brother-pt-d450vp.git-crypt.key
```

### Обязательная проверка перед `git push`
```bash
git-crypt status -e
```
В выводе должны быть отмечены как `encrypted:` файлы `group_vars/all/secrets.yml` и все `session/*.json`. Также `git-crypt status -f` не должен показывать ни одного предупреждения о незашифрованных файлах.

## 2. Выгрузка сессии OpenCode

Перед каждым релизом или завершением этапа выгружаем текущую сессию в `session/<SESSION_ID>.json` через идемпотентный скрипт:

```bash
./scripts/export-session.sh
```

Скрипт проверяет правила `.gitattributes`, экспортирует сессию командой `opencode session export` и проверяет `git-crypt status -e`.

## 3. Выпуск релизов и тегов

Каждый значимый этап фиксируется:
1. Обновлением `CHANGELOG.md`, `TASKS.md` и соответствующего раздела в `docs/`.
2. Выгрузкой сессии в `session/` (зашифровано `git-crypt`).
3. Сборкой и проверкой юнит-тестов: `./scripts/build-and-test.sh`.
4. Аннотированным тегом `git tag -a vX.Y.Z -m "..."` и публикацией GitHub Release с прикреплённым APK через `gh release create`.
