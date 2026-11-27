# Шаг 1. Настройка среды разработки Android (Ubuntu)

## Установленные компоненты

На рабочей машине (Ubuntu 26.10, `x86_64`) установлены и настроены:

1. **Android Studio** `2026.2.2.9-rabbit2-patch1` (`snap install android-studio --classic`).
2. **OpenJDK 21** (`openjdk-21-jdk`) — `/usr/lib/jvm/java-21-openjdk-amd64`.
3. **Git** `2.55.0`, **git-crypt** `0.8.0`, **GitHub CLI (`gh`)** `2.46.0`.
4. **Android SDK** в `/home/android-brother-pt-d450vp/Android/Sdk`:
   - `cmdline-tools;latest` (`sdkmanager`, `android`)
   - `platform-tools` (`adb`, `fastboot`)
   - `platforms;android-35`, `platforms;android-36`
   - `build-tools;34.0.0`, `build-tools;35.0.1`, `build-tools;36.0.0`
   - `emulator`
5. **USB и виртуализация:**
   - Установлен пакет `android-sdk-platform-tools-common` (`android-udev-rules`) для подключения Android-смартфонов по USB.
   - Пользователь `android-brother-pt-d450vp` добавлен в группу `kvm` для аппаратного ускорения эмулятора.

## Переменные окружения

Прописаны глобально в `/etc/profile.d/android-sdk.sh`, а также в `~/.bashrc`:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/android-brother-pt-d450vp/Android/Sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

## Повторная сборка проекта

Проект использует **Gradle Wrapper** (`./gradlew`, Gradle `8.11.1`) и `local.properties` с путём к SDK (файл `local.properties` добавлен в `.gitignore`, чтобы каждый разработчик использовал локальный путь).

Идемпотентный запуск сборки и тестов:
```bash
./scripts/build-and-test.sh
```
