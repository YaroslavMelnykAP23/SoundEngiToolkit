# SoundEngiToolkit

Android-застосунок (Kotlin, Jetpack Compose) для звукоінженера-початківця: запис з мікрофона, осцилограф, ефекти
(EQ, компресор, delay, reverb) і **AI-чат**, у якому користувач описує бажане звучання звичайними словами, а застосунок
змінює параметри й пояснює, що змінено (було → стало) і навіщо.

Версія: **0.3.0** (тег `lab7-final`, debug APK). Документація (Knowledge Base): [docs/README.md](docs/README.md).

## Основний сценарій

Записати фрагмент (Record) → вкладка **AI** → запит, напр. `зменш reverb наполовину` → у чаті «Reverb mix: 25% → 13%» і
пояснення → Studio: Play → Save → пізніше Library → Load (запис з ефектами). Критерії: [docs/final-scenario.md](docs/final-scenario.md).

## Архітектура коротко

| | |
|---|---|
| Клієнт | Android, Kotlin, Jetpack Compose; Room + WAV у пам'яті застосунку |
| AI | **гібрид без власного backend:** прості однозначні команди (`120 bpm`, `reverb off`, `delay 1/8`) — правила на пристрої, офлайн; решта — Gemini через **Firebase AI Logic** |
| Модель | `gemini-3.5-flash-lite` (Gemini Developer API), `temperature 0.2`, `thinkingLevel LOW`, `responseSchema`; таймаут 20 с |
| Тариф | Firebase **Spark** (безкоштовний), білінг не потрібен |
| Захист | ключів у коді й APK немає; виклики приймаються лише від застосунку з **App Check** (debug provider у debug-збірці) |
| Адреса API | власного API немає; запити йдуть через SDK `firebase-ai` до Firebase (HTTPS) |

Схема і шлях даних: [docs/architecture.md](docs/architecture.md).

## Вимоги до середовища

- Android Studio (AGP 9.4, Gradle 9.6 — підтягується wrapper'ом), JDK з комплекту Android Studio.
- Android SDK: `compileSdk 37`, `minSdk 29` (Android 10+). Перевірено на емуляторі «Medium Phone», Android 17.0.
- Обліковий запис Google і проєкт Firebase (для хмарного AI). Без нього застосунок працює, але AI — лише правила.

## Встановлення і запуск

### 1. Конфігурація (секрети не в Git)

| Файл | Що робити | У Git |
|---|---|---|
| `local.properties` | створює Android Studio; приклад — [local.properties.example](local.properties.example) | ні |
| `app/google-services.json` | завантажити з Firebase Console (див. крок 2); приклад структури — [app/google-services.json.example](app/google-services.json.example) | ні |
| App Check debug token | з Logcat після першого запуску (див. крок 4) | ні — і не в звіти/скриншоти |

### 2. Firebase

1. [Firebase Console](https://console.firebase.google.com/) → проєкт → додати Android-застосунок з package name `com.example.soundengitoolkit`.
2. Завантажити `google-services.json` у папку `app/`.
3. **AI Logic** → Get started → **Gemini Developer API**.
4. **App Check** → зареєструвати застосунок; для debug-збірки токени додаються вручну (крок 4).

### 3. Збірка і встановлення

```
./gradlew testDebugUnitTest     # 132 unit-тести, без мережі й ключів
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
(Windows: `gradlew` замість `./gradlew`.) Або Run з Android Studio на емуляторі / телефоні.

`adb` лежить у `<Android SDK>/platform-tools` (шлях до SDK — у `local.properties`, `sdk.dir`). Якщо `adb` не знайдено
(Windows PowerShell: «The term 'adb' is not recognized»), додати до PATH для поточного вікна:
```powershell
$env:Path += ";<Android SDK>\platform-tools"
adb devices          # має бути рядок emulator-5554   device
```

### 4. App Check debug token (обов'язково для хмарного AI)

1. Запустити застосунок, відкрити Logcat, фільтр `DebugAppCheckProvider` → рядок `Firebase App Check debug token: …`.
2. Firebase Console → App Check → Apps → застосунок → ⋮ → **Manage debug tokens** → **Add debug token**.
3. Почекати ~1 хв, **перезапустити** застосунок.

Token прив'язаний до **встановлення**: після перевстановлення, очищення даних або на новому пристрої з'являється новий —
його треба додати знову. Без нього хмарні запити дають «Cloud AI is unavailable (setup or quota)…» (прості команди працюють).
Release-збірка без Play Integrity App Check не пройде — для демонстрації використовується **debug APK**.
Після захисту debug token варто видалити в консолі.

## Перевірка після встановлення (≈ 5 хв)

1. Studio → **Record** → дозволити мікрофон → 3 с → Stop (на емуляторі: Extended controls → Microphone → *Virtual microphone uses host audio input*).
2. Studio → увімкнути **Reverb** → вкладка **AI** → `зменш reverb наполовину` → у чаті `Reverb mix: 25% → 13%`, джерело `cloud`.
3. Режим польоту → `make sound warmer` → «No connection…» + **Retry**; `reverb off` → виконано (`rules`).
4. Вимкнути режим польоту → **Retry** → відповідь `cloud` без перезапуску.
5. Studio → **Save** → Library → **Load** → налаштування відновлено.
6. Logcat, фільтр `tag:AiChat` — один рядок на запит: джерело, результат, час.

Повний перелік перевірок і результати: [docs/testing.md](docs/testing.md); оцінювання AI: [docs/evaluation.md](docs/evaluation.md), розділ 6.

## Відомі обмеження (головне)

- Хмарний AI потребує мережі й зареєстрованого debug token; офлайн — лише прості команди.
- Модель недетермінована: на «тепліше» іноді вмикає EQ без значень (L-27), зрідка відповідає не мовою запиту (L-35),
  може писати, що зміну зроблено, коли її немає — застосунок завжди показує фактичні зміни, обчислені на пристрої (L-33).
- Орієнтація зафіксована portrait; зміна теми / мови під час роботи скидає запис (L-13).
- Повідомлення про відмову в дозволі мікрофона дрібне, без кнопки переходу в налаштування (L-38).

Повний список: [docs/known-limitations.md](docs/known-limitations.md).
