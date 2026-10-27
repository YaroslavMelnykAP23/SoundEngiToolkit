# Knowledge Base: SoundEngiToolkit

Android-додаток (Kotlin, Jetpack Compose): семплер + синтезатор + ефекти + керування текстовими командами. Пакет: `com.example.soundengitoolkit`.

## З чого почати

| Мета | Файл |
|---|---|
| Зрозуміти продукт | [project-overview.md](project-overview.md) |
| Що готово, що ні, критерії готовності | [requirements.md](requirements.md) |
| Як влаштований код і потоки | [architecture.md](architecture.md) |
| Формат AI-команд і модель даних | [api-contract.md](api-contract.md) |
| Як працює AI-частина | [ai-component.md](ai-component.md) |
| Як перевіряти | [testing.md](testing.md) |
| Що зламано чи ризиковано | [known-limitations.md](known-limitations.md) |
| Чому зроблено саме так | [decisions.md](decisions.md) |
| Правила для coding-агента | [ai-instructions.md](ai-instructions.md) |
| Історія змін | [../CHANGELOG.md](../CHANGELOG.md) |

Порядок читання для нового розробника або агента: overview → architecture → requirements → known-limitations → ai-instructions.

## Налаштування

1. `app/src/main/AndroidManifest.xml`, перед `<application>`:
   ```xml
   <uses-permission android:name="android.permission.RECORD_AUDIO" />
   <uses-permission android:name="android.permission.INTERNET" />
   ```
2. `app/build.gradle.kts`: плагін KSP та Room (версію KSP підбирати під версію Kotlin проєкту), а також залежності для хмарного AI (версії Firebase звіряти з документацією):
   ```kotlin
   plugins { id("com.google.devtools.ksp") version "<версія KSP>" }   // у файлі модуля app
   implementation("androidx.room:room-runtime:2.8.4")
   implementation("androidx.room:room-ktx:2.8.4")
   ksp("androidx.room:room-compiler:2.8.4")
   implementation(platform("com.google.firebase:firebase-bom:<версія>"))
   implementation("com.google.firebase:firebase-ai")
   implementation("com.google.firebase:firebase-appcheck-debug")
   ```
3. Для хмарного AI: `google-services.json` у `app/` і плагін `com.google.gms.google-services`. Файл **не комітити**. Без нього додаток працює, а AI обмежується правилами.
4. Запуск: `./gradlew assembleDebug`, потім Run на реальному пристрої. Версії `minSdk`, `compileSdk`, Kotlin і Compose дивитися в `app/build.gradle.kts` (рішення про `minSdk` ще відкрите, див. [decisions.md](decisions.md)).

# Лабораторна робота 5: оптимізація архітектури

**Варіант:** А, Mobile Architecture (Android-клієнт без власного backend).

**Сценарій:** «AI-команда». Користувач вводить текст у полі AI command і натискає Run;
джерело команд повертає JSON-дії, які змінюють BPM та параметри ефектів.

**Зовнішня залежність за інтерфейсом:** `CommandSource` (хмарне джерело: Gemini через
Firebase AI Logic).

**Реалізації інтерфейсу:**
- real: `FirebaseSource` (Gemini), `HybridSource` (правила + хмара);
- fake: `FakeCommandSource` (режими success / error / fail once, опційна затримка), для тестів і debug.

**Результат рефакторингу:** `UI (MainActivity) → AiCommandViewModel (AiUiState) → RunAiCommandUseCase → CommandSource`;
залежності збирає `AppContainer` (ручний DI, composition root). Деталі: [architecture.md](architecture.md), [decisions.md](decisions.md), [lab5-baseline.md](lab5-baseline.md), [known-limitations.md](known-limitations.md).

**Контрольне розширення:** повтор останнього невдалого запиту без втрати введених даних (кнопка Retry).
Змінено лише шар presentation (`AiUiState`, `AiCommandViewModel`, `Screen`); use case, джерела й composition root не чіпалися.

**Команди:**

| Дія | Команда |
|---|---|
| Збірка | `./gradlew assembleDebug` |
| Unit-тести (без мережі й API key) | `./gradlew testDebugUnitTest` |

Windows: `gradlew` замість `./gradlew`.

## Ключові факти

| Параметр | Значення |
|---|---|
| Аудіо | mono, 44 100 Гц, `PCM_FLOAT`, блок 256 семплів |
| Потоки | UI, `audio-out`, `audio-in`, корутина `Dispatchers.IO` для AI |
| Пакети коду | корінь (`App`, `MainActivity`), `di`, `audio`, `data`, `ai.domain`, `ai.data`, `ai.ui`, `ui.library`, `ui.theme` (див. [architecture.md](architecture.md)) |
| Локальне сховище | Room (`soundengitoolkit.db`) + WAV у `filesDir/projects` |
| Backend | немає; заплановано Firebase (Auth, Firestore, Storage) |
| Автоматичні тести | JVM unit-тести без мережі й API key (`gradlew testDebugUnitTest`) |

## Словник

- **dBFS**: децибели відносно повної шкали; 0 dBFS це максимум, вище починається обрізання.
- **Peak / RMS**: найбільше миттєве значення та середньоквадратичний рівень сигналу.
- **BPM**: темп, ударів на хвилину; бит = чверть, такт = 4 біти (розмір 4/4).
- **Snap to beats**: прив'язка курсора й маркерів до найближчого біта.
- **Курсор**: точка, з якої починається відтворення. **Loop A-B**: ділянка між двома маркерами, що зациклюється.
- **Master volume**: гучність виходу в дБ (змінює звук). **View gain**: масштаб картинки осцилографа (звук не змінює).
- **Ефектний ланцюжок**: EQ → компресор → delay → reverb.
- **AI-шар** (`ai/domain/`): код, що виконує JSON-дії. **Джерело команд** (`CommandSource`): те, що перетворює текст на JSON (правила або модель Gemini). Сама модель звуку не обробляє.