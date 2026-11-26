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
| AI-чат: специфікація, REQ, AC, план (Лаба 6) | [ai-chat.md](ai-chat.md) |
| Оцінка AI «до/після», контрольні приклади | [evaluation.md](evaluation.md) |
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
   debugImplementation("com.google.firebase:firebase-appcheck-debug")   // лише debug-збірка
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

# Лабораторна робота 6: розвиток AI-функціональності

**Варіант:** А, розширення наявної функції «AI-команда».

**Що змінилось і навіщо.** Нова вкладка **AI** (чат): користувач описує бажаний звук, застосунок змінює параметри
і відповідає, **що змінено** (було → стало, рахується на пристрої) і **навіщо** (пояснення моделі). Моделі передається
поточний стан, тому працюють відносні команди («зроби delay довшим», «зменш reverb наполовину») і вимкнення
(`reverb off`). Результат на 5 контрольних прикладах: намір 2/5 → 5/5, пояснень 0/5 → 5/5 ([evaluation.md](evaluation.md)).
Попередній сценарій (поле AI command на Studio) на момент здачі збережено (тег `lab6-final`); після здачі його прибрано, бо чат його повністю замінює.

**Модель і доступ:**

| | |
|---|---|
| Джерело моделі | Gemini Developer API через Firebase AI Logic (`com.google.firebase:firebase-ai`, `firebase-bom 35.1.0`), виклик з клієнта, без власного backend |
| Модель / параметри | `gemini-3.5-flash-lite`, `temperature 0.2`, `thinkingLevel LOW`, `responseSchema`; таймаут 20 с (`FirebaseSource`, `AiCall`) |
| Тариф | Spark (безкоштовний); модель не потребує білінгу |
| Потрібно | `app/google-services.json` (не комітиться), AI Logic → Gemini Developer API у консолі Firebase, App Check |
| Без Firebase / мережі | прості команди (`120 bpm`, `reverb off`, `delay 1/8`) працюють офлайн правилами; складні дають помилку з Retry |

**Запуск і перевірка:**

1. Firebase Console → проєкт → Android-застосунок `com.example.soundengitoolkit` → завантажити `google-services.json` у `app/`.
2. Firebase AI Logic → Get started → **Gemini Developer API**.
3. App Check: запустити debug-збірку, скопіювати debug token з Logcat (тег `DebugAppCheckProvider`) →
   App Check → Apps → Manage debug tokens → додати.
4. `./gradlew assembleDebug` → Run на пристрої → вкладка **AI**.
5. Тести (без мережі й ключів): `./gradlew testDebugUnitTest` (133 тести на `lab6-final`).
6. Ручні сценарії і контрольні приклади E1–E5: [evaluation.md](evaluation.md), розділ 5.
7. Перед commit: `git diff` — без `google-services.json`, ключів і debug token.

**Coding agent.** Код, тести й документацію писав AI-асистент (Claude) з доступом до репозиторію, покроково
(крок → збірка → тести → commit). Мої перевірки: збірка і `testDebugUnitTest` після кожного кроку; ручні прогони на
пристрої (офлайн, «high demand», таймаути, E1–E5 по 3 запуски); діагностика через Logcat, вибір моделі за результатами
(3.8-flash → «high demand», 3.5-flash → таймаути, обрано 3.5-flash-lite). Знайдені на пристрої проблеми, які виправлялись:
`eq on=true` без значень, неправильна категорія помилки офлайн, межове значення delay (L-30, записано як обмеження).

# Лабораторна робота 7: підсумкова інтеграція, тестування та демонстрація

**Фінальний сценарій.** Користувач записує фрагмент (або відкриває збережений), у вкладці **AI** описує бажане звучання
звичайною мовою → застосунок змінює параметри ефектів і показує, що змінено (було → стало) і навіщо → користувач
слухає результат і зберігає запис разом з ефектами (Save). Повний опис і критерії K-1…K-5: [final-scenario.md](final-scenario.md).

**Інтеграція.** Шлях даних від Send до Save з файлами й функціями, конфігурація Firebase AI Logic і слабкі місця W-1…W-5:
[architecture.md](architecture.md), розділ «Шлях даних основного сценарію»; схема оновлена за фактичним кодом. Помилки й таймаут виклику:
[api-contract.md](api-contract.md), розділ 1a.

**Оцінювання AI.** Контрольний набір A-01…A-10 (6 типових, 2 граничних, 2 некоректних) і регресія попередньої функції P-01…P-05: [evaluation.md](evaluation.md), розділ 6. Час відповіді — Logcat, тег `AiChat`.

**Стани інтерфейсу.** AI-екран × 5 станів, помилки й дії користувача, повторне натискання, фон, дозвіл: [ui-states.md](ui-states.md).

## Ключові факти

| Параметр | Значення |
|---|---|
| Аудіо | mono, 44 100 Гц, `PCM_FLOAT`, блок 256 семплів |
| Потоки | UI, `audio-out`, `audio-in`, корутини `viewModelScope` для AI (команда і чат), `Dispatchers.IO` для БД |
| Пакети коду | корінь (`App`, `MainActivity`), `di`, `audio`, `data`, `ai.domain`, `ai.data`, `ai.ui` (зокрема AI-чат), `ui.library`, `ui.theme` (див. [architecture.md](architecture.md)) |
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