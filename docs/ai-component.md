# AI-компонент

## Призначення

Перетворити текстовий запит користувача на дії над параметрами (BPM, delay, reverb, compressor, EQ) і пояснити,
що змінено і навіщо. AI **не обробляє і не генерує звук**: він повертає JSON, а `ActionExecutor` виконує його тими ж
сетерами, що й інтерфейс.

Два сценарії користуються тим самим конвеєром:

| Сценарій | Де | Контекст для моделі | Що бачить користувач |
|---|---|---|---|
| AI command (Лаба 2–5) | вкладка Studio, поле AI command → Run | лише текст | технічний лог `✓ fx k=v` |
| **AI-чат** (Лаба 6) | вкладка **AI** | текст + поточний стан + 3 останні репліки | Changed (було → стало), Why, попередження ⚠ |

Специфікація чату: [ai-chat.md](ai-chat.md). Оцінка «до/після»: [evaluation.md](evaluation.md).

## Маршрутизація і джерела (`CommandSource`)

**Прості команди → правила, усе інше → Gemini з поточним станом** (`HybridSource`).

| Реалізація | Опис | Потребує |
|---|---|---|
| `RulesSource` | `SimpleCommandParser`: команда проста, якщо зрозуміле кожне слово (`120 bpm`, `delay 1/8`, `reverb off`, `вимкни компресор`); інакше дій немає. Офлайн, миттєво | нічого |
| `FirebaseSource` | Gemini через Firebase AI Logic; надсилає `AiRequest.toUserMessage()` (`state`, `history`, `prompt`) | `google-services.json`, мережа, App Check |
| `HybridSource` | за замовчуванням: правила, якщо вони розібрали команду; інакше хмара. Помилки хмари **не ховаються** | як вище |
| `FakeCommandSource` | тести й debug-перемикач: `SUCCESS`, `NETWORK_ERROR`, `UNAVAILABLE`, `BUSY`, `INVALID_JSON`, `NO_ACTIONS`, `FAIL_ONCE`, `delayMs` | нічого |

Збирання: `di/AppContainer.kt` (`runAiCommand`, `runAiChat`, перемикач `useFake`).

## Модель і параметри

| Параметр | Значення | Де |
|---|---|---|
| Провайдер | Gemini Developer API (`GenerativeBackend.googleAI()`), SDK `firebase-ai` з `firebase-bom 35.1.0` | `FirebaseSource` |
| Модель | `gemini-3.5-flash-lite` (Stable, featured, без білінгу); перевірено 23.11.2026 | `FirebaseSource.MODEL` |
| `temperature` | 0.2: та сама команда має давати ті самі дії | `FirebaseSource.TEMPERATURE` |
| Thinking | `ThinkingLevel.LOW`: коротка задача, менша затримка | `FirebaseSource.THINKING` |
| Формат | `responseMimeType = application/json` + `responseSchema` (`REPLY_SCHEMA`) | `FirebaseSource` |
| Системна інструкція | `Ai.SYSTEM`: дозволені дії й діапазони, правила для відносних команд, `why`, `reply` | `ai/domain/Ai.kt` |
| Таймаут | 20 с на будь-яке джерело | `AiCall.TIMEOUT_MS` |

Чому не інші моделі: `gemini-3.8-flash` 23.11.2026 відповідала «high demand», `gemini-3.5-flash`
(у Developer API перенаправляється на 3.6-flash) давала таймаути до 3 хв ([evaluation.md](evaluation.md), 1.3).

## Конвеєр (чат)

1. **Підготовка даних** — `AiRequestBuilder`: порожній або > 500 символів → `InvalidInput`; `FxSnapshot` (знімок рушія); 3 останні репліки.
2. **AI** — `AiCall.ask` (таймаут) → `HybridSource` → правила або Gemini.
3. **Перетворення результату** — `AiReplyParser` (дії + `why` + `reply`) → `ActionClamp` (діапазони + попередження)
   → `ActionExecutor` → `ChangeDiff` (до/після) → `ExplanationBuilder` (`Changed` або `NothingChanged`).

«Що змінено» рахується на пристрої з різниці станів, модель дає лише «навіщо». Правила не дають `why`,
тому для них підставляється стандартне пояснення ефекту.

## Помилки і стани

| Категорія (`AiError`) | Коли | Повідомлення | Retry |
|---|---|---|---|
| `InvalidInput` | порожній / задовгий запит | Enter a command first | ні |
| `Network` | немає мережі (зокрема `IOException` усередині винятку SDK) | No connection… прості команди працюють офлайн | так |
| `Timeout` | > 20 с або таймаут SDK | AI did not answer in time | так |
| `Busy` | `ServerException` «high demand» / 503 | AI is busy right now | так |
| `AiUnavailable` | налаштування, App Check, квота, назва моделі | Cloud AI is unavailable (setup or quota) | лише вручну, без автоповторів |
| `InvalidResponse` | невалідний JSON | The AI reply could not be understood | так |

Стани чату: очікування («AI is thinking…», поле й Send вимкнені), успіх (Changed), немає результату (NothingChanged),
помилка з Retry. Нескінченного очікування немає: таймаут завжди переводить у помилку.

## Обмеження

Див. [known-limitations.md](known-limitations.md): L-27…L-31 (недетермінованість моделі, текст моделі може не збігатися
з дією, межові значення відносних команд, чат не зберігається, перевантаження безкоштовної моделі).

## Безпека і дані

- `google-services.json`, ключі й App Check debug token не комітяться (`.gitignore`); ключі обмежуються пакетом і SHA-1.
- App Check: debug-провайдер у `app/src/debug` (`AppCheckSetup`), токен з Logcat реєструється в консолі Firebase.
- У Gemini йдуть лише `state` (параметри ефектів і BPM), до 3 реплік і текст запиту. **Не** йдуть: аудіо, назви
  й метадані проєктів, повна AI history, ідентифікатори пристрою (`AiRequestTest.userMessage_containsOnlyStateHistoryAndPrompt`).
- Значення від моделі обмежуються діапазонами на пристрої (`ActionClamp`); довільний код чи доступ до файлів через AI неможливі.
