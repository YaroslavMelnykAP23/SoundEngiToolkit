# Контракти даних і API

Окремого REST API зараз немає. У документі описано: (1) реалізований контракт AI-команд, (2) реалізовану локальну модель даних (Room + WAV), (3) заплановану модель даних у Firebase, (4) умови, за яких знадобиться власний REST backend.

## 1. Контракт AI-команд (реалізовано)

Це межа між AI та додатком: формат дій, спільний для правил і моделі. Реалізація: `Ai.SYSTEM`, `CommandSource.ask`, `AiActionMapper`, `ActionExecutor`.
Старе поле AI command, для якого контракт створювався, прибрано після Лаби 6; зараз цей формат використовує AI-чат (розділ 1a).

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

Поведінка пропущених полів у `ActionExecutor` (з Лаби 6, L-04 виправлено; перевірено тестами `BaselineAiTest`):

| fx | `on` пропущено | Числові поля пропущено |
|---|---|---|
| `bpm` | немає | не змінюється (`value`) |
| `delay`, `reverb`, `compressor` | залишається поточне | залишаються поточні |
| `eq` | вмикається, якщо передано хоч одну смугу; інакше поточне | залишаються поточні |

Значення поза діапазонами обрізаються на пристрої (`ActionClamp`, R-28) для обох сценаріїв; division — до найближчого з 4, 8, 16.
До Лаби 6 `compressor` скидався до `threshold=-18`, `ratio=4`, а `eq` — до 0 дБ.

Слайдер BPM в UI має діапазон 60-200, а AI може встановити значення в межах 40-240 (з промпту), тож значення поза слайдером можливе. Див. [known-limitations.md](known-limitations.md).

**Помилки** (стан коду на Лабу 7):

| Ситуація | Поведінка |
|---|---|
| Нерозпізнана `fx` | `AiAction.Unknown`, дія пропускається; якщо інших змін немає — «нічого не змінено» |
| Некоректний JSON, відсутній `actions` | `AiError.InvalidResponse`, нічого не застосовується (розбір до застосування) |
| Хмара недоступна, Firebase не налаштовано | помилка показується користувачу (`Network` / `AiUnavailable` / `Busy` / `Timeout`), результат правил її не приховує (L-25 виправлено в Лабі 6) |

## 1a. AI-чат: запит і відповідь (Лаба 6, реалізовано)

**Запит до моделі** (`AiRequest.toUserMessage()`, user message; системна інструкція `Ai.SYSTEM`):

```json
{
  "state": {
    "bpm": 120,
    "delay": {"on": true, "division": 8, "feedback": 0.4, "mix": 0.35},
    "reverb": {"on": false, "mix": 0.25, "size": 0.84},
    "compressor": {"on": false, "threshold": -18, "ratio": 4},
    "eq": {"on": false, "low": 0, "mid": 0, "high": 0}
  },
  "history": [{"prompt": "reverb on", "result": "Reverb off→on"}],
  "prompt": "зроби delay довшим"
}
```

`state` відсутній у старому блоці AI command; `history` — до 3 останніх реплік (якщо є).

**Відповідь моделі** (`responseSchema`, `FirebaseSource.REPLY_SCHEMA`):

```json
{
  "actions": [
    {"fx": "delay", "division": 4, "why": "Менший поділ — довша затримка."}
  ],
  "reply": "Зробив затримку довшою."
}
```

| Поле | Тип | Обов'язкове | Примітка |
|---|---|---|---|
| `actions[].fx` | enum `bpm`, `delay`, `reverb`, `compressor`, `eq` | так | |
| `actions[].why` | string | так | мовою запиту; для правил відсутнє → стандартне пояснення |
| `value`, `on`, `division`, `feedback`, `mix`, `size`, `threshold`, `ratio`, `low`, `mid`, `high` | number / bool | ні | діапазони як у розділі 1; на пристрої обрізаються (`ActionClamp`) |
| `reply` | string | так | короткий підсумок; при `actions: []` пояснює, чим керує AI |

Правила (`RulesSource`) повертають той самий формат без `why` і `reply`.
Пропущені поля **зберігають поточні значення** (L-04 виправлено, Лаба 6); смуга EQ без `on` вмикає EQ.

**Виклик і помилки** (`AiCall`, `FirebaseSource`, `AiErrorMapper`, `ChatUiState`):

| Параметр | Значення |
|---|---|
| Транспорт | Firebase AI Logic SDK (`firebase-ai`), HTTPS; власного endpoint немає |
| Захист | App Check (debug provider у debug-збірці); ключів у коді й APK немає |
| Таймаут | 20 с на весь виклик, автоматичних повторів немає |
| Обмеження запиту | `prompt` 1–500 символів після `trim`; `history` ≤ 3 |

| `AiError` | Коли | Текст у чаті | Retry |
|---|---|---|---|
| `InvalidInput` | порожній або > 500 символів (UI цього не допускає) | «Enter a command (up to 500 characters)» | ні |
| `Network` | немає з'єднання | «No connection. Simple commands still work offline…» | так |
| `Timeout` | > 20 с | «AI did not answer in time. Try again» | так |
| `Busy` | `ServerException` «high demand» / overloaded | «AI is busy right now. Try again in a minute» | так |
| `AiUnavailable` | App Check, квота, модель, немає `google-services.json`, порожня відповідь | «Cloud AI is unavailable (setup or quota). Simple commands still work.» | так (вручну) |
| `InvalidResponse` | відповідь не відповідає контракту | «The AI reply could not be understood» | так |
| `Unknown` | інше, зокрема виняток під час застосування | «Something went wrong» | так |

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

**`ai_history`:** `id`, `prompt`, `json` (відповідь джерела), `log` (журнал виконання), `source` (AI-чат: `chat:rules`, `chat:cloud`, `chat:error`; старі записи до Лаби 6: `rules`, `cloud`, `fake`, `error`, `rules (cloud failed)`), `ok` (виконано без помилки й щось розпізнано), `createdAt`. Зберігається до 200 останніх записів.

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