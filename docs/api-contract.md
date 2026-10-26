# Контракти даних і API

Окремого REST API зараз немає. У документі описано: (1) реалізований контракт AI-команд, (2) реалізовану локальну модель даних (Room + WAV), (3) заплановану модель даних у Firebase, (4) умови, за яких знадобиться власний REST backend.

## 1. Контракт AI-команд (реалізовано)

Це межа між AI та додатком. Реалізація: `Ai.SYSTEM`, `Ai.apply`, `CommandSource.ask`.

**Запит:** довільний текст користувача (рядок). Приклад: `add delay 1/8 at 120 bpm and turn on compressor`.

**Відповідь:** JSON без додаткового тексту:

```json
{"actions":[
  {"fx":"bpm","value":120},
  {"fx":"delay","on":true,"division":8,"feedback":0.4,"mix":0.35},
  {"fx":"compressor","on":true,"threshold":-20,"ratio":4}
]}
```

**Дії:**

| fx | Поля | Діапазон (задано в промпті) |
|---|---|---|
| `bpm` | `value` | 40-240 |
| `delay` | `on`, `division`, `feedback`, `mix` | division: 4, 8, 16; feedback 0-0.9; mix 0-1 |
| `reverb` | `on`, `mix`, `size` | mix 0-1; size 0.5-0.95 |
| `compressor` | `on`, `threshold`, `ratio` | threshold −40…0 дБ; ratio 1-20 |
| `eq` | `on`, `low`, `mid`, `high` | −12…12 дБ |

Поведінка пропущених полів у `Ai.apply` (перевірено за кодом):

| fx | `on` пропущено | Числові поля пропущено |
|---|---|---|
| `bpm` | немає | не змінюється (`value`) |
| `delay`, `reverb` | залишається поточне | залишаються поточні |
| `compressor` | залишається поточне | скидаються до `threshold=-18`, `ratio=4` |
| `eq` | **вмикається** (`true`) | скидаються до `0` дБ |

Слайдер BPM в UI має діапазон 60-200, а AI може встановити значення в межах 40-240 (з промпту), тож значення поза слайдером можливе. Див. [known-limitations.md](known-limitations.md).

**Помилки:**

| Ситуація | Поведінка |
|---|---|
| Нерозпізнана `fx` | у лог додається `? <fx>`, дія пропускається |
| Некоректний JSON, відсутній `actions` | виняток; UI показує `Error: <клас>: <повідомлення>` |
| Хмара недоступна або Firebase не налаштовано | `HybridSource` повертає результат правил (можливо, порожній) |

Значення від AI поки не обмежуються діапазонами в коді.

## 2. Локальна модель даних (реалізовано)

База `soundengitoolkit.db` (Room, версія 1, міграцій поки немає). Файли: `filesDir/projects/<uuid>.wav` (WAV PCM 16 біт, stereo (обидва канали однакові, бо джерело mono), 44 100 Гц; при читанні підтримуються і старі mono-файли, stereo зводиться в mono; імʼя файлу не залежить від назви проєкту).

**`projects`:**

| Поле | Тип | Зміст |
|---|---|---|
| `id` | Long (PK, авто) | |
| `name` | String | назва від користувача (за замовчуванням `Recording <дата час>`) |
| `createdAt` | Long | мс з епохи |
| `bpm` | Float | |
| `cursorPos`, `loopA`, `loopB` | Int | позиції в семплах |
| `loopOn` | Boolean | |
| `durationSamples` | Int | довжина запису |
| `wavFile` | String | імʼя файлу в `filesDir/projects` |
| `effectsJson` | String | налаштування ефектів, формат нижче |

**`ai_history`:** `id`, `prompt`, `json` (відповідь джерела), `log` (журнал виконання), `source` (`rules`, `cloud`, `rules (cloud failed)`), `ok` (виконано без помилки й щось розпізнано), `createdAt`. Зберігається до 200 останніх записів.

**Формат `effectsJson`** (ключі збігаються з AI-діями):

```json
{"eq":{"on":false,"low":0.0,"mid":0.0,"high":0.0},
 "comp":{"on":true,"threshold":-18.0,"ratio":4.0},
 "delay":{"on":true,"division":8,"feedback":0.4,"mix":0.35},
 "reverb":{"on":false,"mix":0.25,"size":0.84}}
```

Не зберігається: master volume, масштаб і позиція перегляду осцилографа.

## 3. Модель даних Firebase (заплановано)

Не реалізовано. Пропонована структура (поля проєкту відповідають локальній моделі з розділу 2):

```
users/{uid}
users/{uid}/projects/{projectId}
    name: string
    bpm: number
    updatedAt: timestamp
    effects: { eq: {...}, comp: {...}, delay: {...}, reverb: {...} }   // той самий формат, що й у AI-діях
    samples: [ { id, storagePath, durationSamples, loopA, loopB, cursor } ]
users/{uid}/presets/{presetId}
    name: string
    effects: {...}
```

Cloud Storage: `users/{uid}/samples/{sampleId}.wav` (аудіо в Firestore не зберігається, ліміт документа близько 1 МБ).

Security Rules: доступ до `users/{uid}/**` лише власнику (`request.auth.uid == uid`).

## 4. Власний REST backend (рішення не прийнято)

Якщо знадобиться (обробка на сервері, інтеграції), пропонується мінімум: `POST /v1/commands` (запит і відповідь як у розділі 1), `GET/PUT /v1/projects/{id}`. Форматування помилок: `{"error":{"code":"...","message":"..."}}`. Поки жодного endpoint не існує.