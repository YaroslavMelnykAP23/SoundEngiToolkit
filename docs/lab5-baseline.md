# Лабораторна робота 5: baseline (до рефакторингу)

Зафіксовано **до** будь-яких змін у продакшн-коді.

- Commit / тег: `<git rev-parse --short HEAD>`, тег `lab5-baseline`
- Дата: <дата>
- Пристрій: <модель або емулятор, версія Android>
- Сценарій: «AI-команда» (текст у полі AI command → Run → зміна BPM і ефектів)

## 1. Контрольні приклади

| # | Вхід | Очікуваний результат | Тест | Статус |
|---|---|---|---|---|
| 1 | `RulesSource`: `add delay 1/8 at 120 bpm and turn on compressor` | 3 дії: `bpm=120`, `delay{on, division=8}`, `compressor{on}` | `rules_fullExample` | <pass/fail> |
| 2 | `RulesSource`: `hello` | `{"actions":[]}` | `rules_unrecognized_isEmpty` | <pass/fail> |
| 3 | `RulesSource`: `ділей 1/16` | `division=16` | `rules_ukrainianDelay_division16` | <pass/fail> |
| 4 | `Ai.apply`: delay on, division 16, feedback 0.5, mix 0.2 | поля `Delay` змінено, лог починається з `✓` | `apply_delay_setsFields` | <pass/fail> |
| 5 | `Ai.apply`: `{"fx":"foo"}` | лог `? foo`, параметри не змінені | `apply_unknownFx_isLogged` | <pass/fail> |
| 6 | `Ai.apply`: `{"fx":"eq","low":3}` | `eq.on=true`, `low=3`, `mid=0`, `high=0` (L-04) | `apply_eqWithoutOn_enablesAndResets` | <pass/fail> |
| 7 | `Ai.apply`: compressor без числових полів | `threshold=-18`, `ratio=4` | `apply_compressorMissingFields_resetToDefaults` | <pass/fail> |
| 8 | `Ai.apply`: `not json` | `JSONException` | `apply_invalidJson_throws` | <pass/fail> |
| 9 | `Ai.apply`: `[reverb on, {}]` | виняток, але `reverb.on=true` (часткове застосування) | `apply_partialOnError_firstActionStaysApplied` | <pass/fail> |
| 10 | `HybridSource`: правила порожні, хмара кидає виняток | `{"actions":[]}`, `lastUsed="rules (cloud failed)"` | `hybrid_cloudFails_fallsBackToRules` | <pass/fail> |
| 11 | `HybridSource`: правила розпізнали фразу | хмара не викликається, `lastUsed="rules"` | `hybrid_rulesRecognized_cloudNotNeeded` | <pass/fail> |

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

Заповнюється під час Задачі 3.

| Що змінилось | Крок | Чому | Тест |
|---|---|---|---|
| Парсинг усіх дій перед застосуванням (немає часткового застосування) | 3 | атомарність | оновлений тест 9 |