# Лабораторна робота 5: baseline (до рефакторингу)

Зафіксовано **до** будь-яких змін у продакшн-коді.

- Commit / тег: `<git rev-parse --short HEAD>`, тег `lab5-baseline`
- Дата: <дата>
- Пристрій: <модель або емулятор, версія Android>
- Сценарій: «AI-команда» (текст у полі AI command → Run → зміна BPM і ефектів)

## 1. Контрольні приклади

| # | Вхід | Очікуваний результат | Тест | Статус |
|---|---|---|---|---|
| 1 | `RulesSource`: `add delay 1/8 at 120 bpm and turn on compressor` | 3 дії: `bpm=120`, `delay{on, division=8}`, `compressor{on}` | `rules_fullExample` | pass |
| 2 | `RulesSource`: `hello` | `{"actions":[]}` | `rules_unrecognized_isEmpty` | pass |
| 3 | `RulesSource`: `ділей 1/16` | `division=16` | `rules_ukrainianDelay_division16` | pass |
| 4 | `Ai.apply`: delay on, division 16, feedback 0.5, mix 0.2 | поля `Delay` змінено, лог починається з `✓` | `apply_delay_setsFields` | pass |
| 5 | `Ai.apply`: `{"fx":"foo"}` | лог `? foo`, параметри не змінені | `apply_unknownFx_isLogged` | pass |
| 6 | `Ai.apply`: `{"fx":"eq","low":3}` | `eq.on=true`, `low=3`, `mid=0`, `high=0` (L-04) | `apply_eqWithoutOn_enablesAndResets` | pass |
| 7 | `Ai.apply`: compressor без числових полів | `threshold=-18`, `ratio=4` | `apply_compressorMissingFields_resetToDefaults` | pass |
| 8 | `Ai.apply`: `not json` | `JSONException` | `apply_invalidJson_throws` | pass |
| 9 | `Ai.apply`: `[reverb on, {}]` | `JSONException`, `reverb.on=false` (часткового застосування більше немає; свідома зміна, крок 3) | `apply_invalidAction_appliesNothing` | <pass/fail> |
| 10 | `HybridSource`: правила порожні, хмара кидає виняток | `{"actions":[]}`, `reply.source="rules (cloud failed)"` | `hybrid_cloudFails_fallsBackToRules` | pass |
| 11 | `HybridSource`: правила розпізнали фразу | хмара не викликається, `reply.source="rules"` | `hybrid_rulesRecognized_cloudNotNeeded` | pass |

Команда запуску: `gradlew testDebugUnitTest`
Результат: <N tests, N passed, N failed>. Якщо щось впало, опишіть, що саме і чому очікування довелося змінити.

## 2. Ручний baseline

UI-обробник Run не можна перевірити юніт-тестом (немає ViewModel), тому сценарії пройдено вручну.

| # | Сценарій | Фактичний результат | Скриншот |
|---|---|---|---|
| 1 | Run з командою за замовчуванням (без мережі) | BPM 120; Delay і Compressor увімкнені; у логу 3 рядки `✓`; в історії запис із джерелом `rules` | `img/baseline/s1-after.png`, `s1-history.png` |
| 2 | Run «make it warmer» без мережі/Firebase | лог `No actions recognized`; перемикачі не змінились; в історії `✗`, джерело `rules (cloud failed)`; додаток не впав | `img/baseline/s2-log.png`, `s2-history.png` |
| 3 | Три швидкі натискання Run підряд | кнопка не блокується; в історії <кількість> записів | `img/baseline/s3-history.png` |

## 3. Відомі особливості, що фіксуються як є

Це не помилки baseline, а поточна поведінка, яку рефакторинг або зберігає, або змінює свідомо (з записом у `decisions.md`):

- L-04: `eq` без `on` вмикається; пропущені числові поля `eq` і `compressor` скидаються до значень за замовчуванням.
- `Ai.apply` не атомарна: помилка на другій дії лишає першу застосованою (тест 9).
- Помилки в UI показуються текстом `Error: <клас>: <повідомлення>`.
- Немає станів Idle/Loading/Success/Error; Run можна натискати повторно.

## 4. Свідомі зміни поведінки в рефакторингу

Заповнено під час рефакторингу (кроки 2-7).

| Що змінилось | Крок | Чому | Тест |
|---|---|---|---|
| CancellationException більше не вважається збоєм хмари (раніше runCatching її ковтав) | 2 | скасування корутини має пропагуватися | hybrid_cancellation_isNotSwallowed |
| Джерело в історії береться з відповіді, а не зі стану lastUsed (раніше при помилці могло бути від попереднього запуску; тепер unknown) | 2 | прибрано прихований стан | ручний сценарій 2 |
| Парсинг усіх дій перед застосуванням (немає часткового застосування) | 3 | атомарність | оновлений тест 9 |
| Усі дії парсяться до застосування: помилка в одній дії не лишає попередні застосованими | 3 | атомарність | apply_invalidAction_appliesNothing |
| Формат логу: ✓ delay on=true division=8 замість ✓ {"fx":"delay",...} (сирого JSON уже немає на цьому шарі) | 3 | executor працює з типізованими діями | ручний сценарій 1 |
| Run неактивна під час запиту, повторні натискання ігноруються (було: кілька паралельних запитів і записів в історії) | 6 | стан Loading, проблема №8 | secondRunWhileLoading_isIgnored, ручний сценарій 3 |
| Порожній ввід дає Enter a command first (було: No actions recognized) | 6 | категорія InvalidInput | blankPrompt_isInvalidInput_andSourceNotCalled |
| Помилки показуються текстом за категорією, а не Error: <клас>: <повідомлення> | 4, 6 | проблема №3 | networkError_isErrorState_withoutApplying |
| Після помилки з'являється кнопка Retry, яка повторює той самий запит (було: вводити вручну) | 7 | розширення | retry_afterFailure_rerunsSamePrompt_andSucceeds |
