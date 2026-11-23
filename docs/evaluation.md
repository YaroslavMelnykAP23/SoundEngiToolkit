# Лабораторна робота 6: оцінка AI-функції

## Етап 1. Вихідна версія (baseline)

| Параметр | Значення |
|---|---|
| Commit | `4415f10` (`4415f108052eee02f091ee063c4dceccfb09417b`), тег `lab6-baseline` |
| Дата | 2026-11-23 |
| Пристрій | емулятор Android Studio «Medium Phone», Android 17.0 |
| Функція | «AI-команда»: текст → `HybridSource` (правила, потім хмара) → JSON-дії → `ActionExecutor` → лог |
| Модель | `gemini-3.5-flash` (`FirebaseSource`, значення за замовчуванням), `responseMimeType = application/json`, без `responseSchema`, temperature за замовчуванням |
| Залежності | `firebase-bom 35.1.0`, `firebase-ai`, `firebase-appcheck-debug` (debug), Kotlin 2.2.10, minSdk 29 |
| Джерело за замовчуванням | `HybridSource(RulesSource, FirebaseSource(Ai.SYSTEM))` у `di/AppContainer.kt` |

Чому не приклади з Лаби 4: Лаба 4 стосувалася локальної БД (Save/Library), AI-прикладів у ній немає.
Тому контрольний набір для AI-команди визначено тут; він буде однаковим «до» і «після».

### 1.1 Контрольні приклади

Початковий стан для всіх прикладів: щойно запущений додаток (BPM 120, усі ефекти вимкнені,
delay 1/8, feedback 0.4, mix 0.35; reverb mix 0.25, size 0.84).

Офлайн-шлях зафіксовано автоматично (`Lab6BaselineTest`). Хмарний шлях (E2) пройдено вручну на пристрої 23.11.2026
потрібно пройти вручну на пристрої з мережею.

| # | Вхід | Що хотів користувач | Фактичний результат офлайн (тест) | Фактичний результат з мережею | Пояснення, що змінено | Відповідає наміру? |
|---|---|---|---|---|---|---|
| E1 | `add delay 1/8 at 120 bpm and turn on compressor` | bpm 120, delay 1/8 увімк., компресор увімк. | `rules`: 3 дії, 3 рядки `✓` (`e1_fullCommand_rulesOnly`) | те саме (хмара не викликається) | немає, лише технічний лог `✓ delay on=true division=8` | так |
| E2 | `make sound warmer` | більше низів / менше верхів (EQ) | 0 дій, джерело `rules (cloud failed)`, показується як Success «No actions recognized», Retry немає (`e2_warmer_offline_emptyAndHidden`) | `cloud`: `eq on=true low=4.0 high=-4.0` (mid скинуто в 0, L-04); скриншот `docs/img/lab6/baseline/e2-e5.png` | немає, лише технічний лог `✓ eq on=true low=4.0 high=-4.0` | так (з мережею); ні (офлайн) |
| E3 | `зроби delay довшим` | 1/8 → 1/4 | `rules`, хмара не викликається; division=8 (не змінилось), лог `✓` (`e3_longerDelay_rulesPreemptCloud_noRealChange`) | те саме | немає | ні: «успіх» без зміни |
| E4 | `зменш reverb наполовину` | reverb mix 0.25 → ~0.12 | `rules`, хмара не викликається; reverb **вмикається**, mix 0.25 (`e4_halfReverb_turnsReverbOnInstead`) | те саме | немає | ні: протилежна дія |
| E5 | `reverb off` (reverb перед цим увімкнений) | вимкнути reverb | `rules`, хмара не викликається; дія `reverb on=true`, reverb **лишається увімкненим**, лог `✓ reverb on=true` (`e5_reverbOff_staysOn`). Так само `delay off` **вмикає** delay (`e5b_delayOff_turnsDelayOn`) | те саме | немає; лог `✓` вводить в оману | ні: протилежна дія |

Підсумок baseline: намір виконано в **2/5** з мережею (E1, E2) і **1/5** офлайн; пояснень змін **0/5**;
відносні команди та вимкнення (E3, E4, E5) **0/3**; у 3 з 5 випадків лог показує `✓`, хоча намір не виконано.

Як відтворити: `gradlew testDebugUnitTest --tests "*Lab6BaselineTest"`; ручні прогони: ввести вхід у поле
AI command → Run, зробити скриншот логу та історії (`docs/img/lab6/baseline/eN.png`).

### 1.2 Відомі обмеження та розбіжності «специфікація ↔ код»

| # | Обмеження | Де в коді | Наслідок |
|---|---|---|---|
| B-1 | Немає таймауту хмарного виклику, хоча коментар до `AiError.Network` каже «No connection **or timeout**», а вимога лаби — обмежити час очікування | `FirebaseSource.ask`: `model.generateContent(prompt)` без `withTimeout` | повільна мережа = довгий Loading |
| B-2 | Правила мають пріоритет над хмарою: будь-яка фраза зі словом `delay`/`reverb`/`compress` не доходить до моделі | `HybridSource.ask` | відносні команди (E3, E4) виконуються неправильно |
| B-3 | Моделі не передається поточний стан і історія | `CommandSource.ask(prompt: String)` | «довше», «менше», «поверни як було» неможливі |
| B-4 | Немає пояснення змін для користувача, лише технічний лог `✓ fx k=v` | `ActionExecutor.describe` | користувач не бачить, що і навіщо змінено |
| B-5 | Значення не обмежуються діапазонами з `Ai.SYSTEM` (L-03) | `ActionExecutor.apply` | feedback ≥ 1 дає наростання звуку |
| B-6 | Прихований збій хмари показується як Success без Retry (L-25) | `HybridSource` + `AiCommandViewModel` | користувач не знає, що команду не зрозуміли через мережу |
| B-7 | У коді baseline `gemini-3.5-flash`; за сторінкою моделей Firebase AI Logic (перевірено 23.11.2026) у Gemini Developer API запити до неї перенаправляються на `gemini-3.6-flash`. У новій версії обрано `gemini-3.5-flash-lite` (див. 1.3) | `FirebaseSource.MODEL` | baseline і нова версія працюють на різних моделях: це враховується при порівнянні «до/після» |
| B-8 | Формат відповіді контролюється лише промптом (`responseMimeType`), без `responseSchema`; ` ```json ` прибирається вручну | `FirebaseSource` | модель може повернути невалідну структуру |
| B-9 | `ai-component.md` описує fallback «UI ловить Throwable і показує в блоці логу», а після Лаби 5 помилки йдуть через `AiError` і `AiUiState.Error` | `docs/ai-component.md` | документацію треба оновити |
| B-11 | Правила ігнорують заперечення: `off`, `вимкни`, `без` → завжди `on=true` | `RulesSource.ask`: `put("on", true)` для delay/compressor/reverb | вимкнути ефект командою неможливо (E5); `✓` у лозі вводить в оману |
| B-12 | Тимчасове перевантаження моделі (23.11.2026 17:58, `gemini-3.8-flash`: `ServerException: This model is currently experiencing high demand`) мапиться в те саме `AiUnavailable`, що й помилка налаштування, а `HybridSource` показує його як «No actions recognized» | `FirebaseSource.ask` → `AiException(AiUnavailable)`; `HybridSource` | користувач не знає, що варто просто повторити; окрема категорія `Busy` з Retry (крок 2a) |
| B-10 | Нерозпізнана команда і «нічого не змінилось» не відрізняються від успіху: стан «немає результату» відсутній | `AiCommandViewModel.run` → `Success("No actions recognized")` | немає окремого стану «немає придатного результату» |

### 1.3 Спостереження під час Етапу 4, крок 1 (23.11.2026, команда E2 `make sound warmer`)

| Час | Конфігурація | Очікування | Результат (Logcat `HybridSource`) |
|---|---|---|---|
| 17:15 | `gemini-3.5-flash`, без схеми, старий промпт (baseline) | кілька секунд | `cloud`: `eq on=true low=4.0 high=-4.0` |
| 17:58 | `gemini-3.8-flash` + `responseSchema`, новий промпт | — | `ServerException: This model is currently experiencing high demand` |
| 18:06 | `gemini-3.5-flash` + `responseSchema` | ~1 хв | `RequestTimeoutException` |
| 18:10 | `gemini-3.5-flash` без схеми | ~1 хв | `cloud`: `eq on=true low=4.0 high=-2.0` |
| 18:15 | `gemini-3.5-flash` + `responseSchema` + `thinkingLevel = LOW` | ~3 хв | `RequestTimeoutException` |
| 18:17 | **`gemini-3.5-flash-lite`** + `responseSchema` + `thinkingLevel = LOW` | кілька секунд | `cloud`: `eq on=true low=4.0` (лише низи; mid і high скинуто в 0 через L-04); скриншот `img/lab6/step1-lite.png` |
| 18:29 | `gemini-3.5-flash-lite`, крок 2a (старий блок, без стану) | кілька секунд | `cloud`: `eq on=true` **без значень** → смуги 0 dB, звук не змінився, але лог `✓`. Додано правило в `Ai.SYSTEM`: для зміни звучання завжди числові значення; приклад у промпті навмисно не з набору E1–E5 («brighter»), щоб не підганяти оцінку |
| 18:31 | те саме + правило про числові значення в `Ai.SYSTEM`, 4 запуски | кілька секунд | 3/4: `eq on=true low=…` (з числами); 1/4: знову `eq on=true` без значень. Залишок закривається кроком 2b: `ChangeDiff` покаже «нічого не змінено» замість `✓` |

Висновки:
- Без власного таймауту користувач чекає до 3 хв (таймаут SDK) і бачить «No actions recognized»: підтверджено B-1 і B-12.
- Схема не є причиною затримки (без неї теж ~1 хв). Причина — завантаженість `gemini-3.5-flash` (3.6-flash) у цей час:
  `gemini-3.5-flash-lite` з тими самими схемою і промптом відповів за кілька секунд.
- Рішення: основна модель `gemini-3.5-flash-lite` (Stable, featured, без білінгу; задача коротка, важка модель не потрібна).
- Тому в кроці 2a: таймаут 20 с, окремі категорії `Timeout` і `Busy` з Retry. Хмарні прогони для оцінки (Етап 5)
  повторюються, коли сервіс відповідає стабільно; час відповіді записується.

## Етап 5. Нова версія: вкладка AI (чат)

| Параметр | Значення |
|---|---|
| Commit | тег `lab6-final` (hash — у звіті: `git rev-parse --short lab6-final`) |
| Модель | `gemini-3.5-flash-lite`, `temperature = 0.2`, `thinkingLevel = LOW`, `responseSchema` (`FirebaseSource`) |
| Таймаут | 20 с (`AiCall.TIMEOUT_MS`) |
| Дата прогону | 23.11.2026 |
| Час відповіді | rules: миттєво; хмара (`gemini-3.5-flash-lite`): кілька секунд, у межах таймауту 20 с |

### 5.1 Контрольні приклади «до / після»

Хмарні приклади запускались по 3 рази, приклад зараховано, якщо намір виконано щонайменше у 2 з 3 (AC-1).
Скриншоти: `img/lab6/after/` (`e2-chat.png`, `e1-chat.png`, `e2-run3-e3-boundary.png`, `e3-chat.png`, `e3-runs.png`, `e4-chat.png`, `e5-weather-chat.png`).

| # | Вхід | До (baseline) | Після: запуски | Пояснення | Намір |
|---|---|---|---|---|---|
| E1 | `add delay 1/8 at 120 bpm and turn on compressor` | так (rules), пояснень немає | rules, миттєво. Delay 1/8 і BPM 120 вже були встановлені, тому Changed показав лише Compressor off → on (`e1-chat.png`) | так (стандартне для rules) | так |
| E2 | `make sound warmer` | так з мережею (EQ low +4 high −4), пояснень немає | 1) EQ on з пласкими смугами → застосунок показав «EQ is on, but all bands are 0 dB…» (не ✓); 2) EQ low 0 → +4 dB, high 0 → −2 dB; 3) EQ low +4 → +7 dB, mid 0 → −2 dB (модель написала «reducing the highs», а змінила mid: Changed показав фактичне) | так (від моделі) | **2/3** |
| E3 | `зроби delay довшим` (delay on, 1/8) | ні: rules, division 8 → 8, лог ✓ | від 1/8: 1) 1/8 → 1/4; 2) 1/8 → 1/4. Поза протоколом, від 1/4 (межа): двічі 1/4 → 1/8, тобто коротше, з текстом «зробив довшим» | так | **2/2** від 1/8; межа — обмеження L-30 |
| E4 | `зменш reverb наполовину` (reverb on) | ні: reverb вмикався, mix не змінювався | 1) Reverb mix 100% → 50%; 2) 50% → 25%; 3) 25% → 13% | так | **3/3** |
| E5 | `reverb off` (reverb on) | ні: лишався увімкненим, лог ✓ | rules: Reverb on → off; повторно: «Nothing changed: the settings already match the request» | так (стандартне) | так |

E4 показує, що модель отримує поточний стан: кожен запуск ділить навпіл **поточне** значення (100 → 50 → 25 → 13).

E2, запуск 3, показує, навіщо «що змінено» рахується на пристрої (REQ-7): текст моделі («reducing the highs»)
не збігся з фактичною дією (mid −2 dB), і користувач побачив фактичну зміну, а не текст моделі.

### 5.2 Критерії приймання

| ID | Критерій | Baseline | Після | Як перевірено |
|---|---|---|---|---|
| AC-1 | Намір виконано (з мережею) | 2/5 | **5/5**; E3–E5: **3/3** | ручні прогони, 5.1 |
| AC-2 | Кожна зміна має пояснення | 0/5 | 5/5 | скриншоти; `ExplanationBuilderTest.rules_withoutWhy_getDefaultExplanation` |
| AC-3 | «Що змінено» = фактичний diff | — | так | `ExplanationBuilderTest.changes_equalRealDiff`, `unchangedParameters_areNotListed` |
| AC-4 | E1, E5 офлайн правилами | 1/2 | 2/2 | `Lab6ExamplesTest`, `SimpleCommandParserTest` |
| AC-5 | Немає значень поза діапазоном | ні | так (feedback 1.5 → 0.9 + ⚠) | `ActionClampTest`, `RunAiChatUseCaseTest.outOfRange_isClamped_withWarning` |
| AC-6 | Порожній ввід / офлайн / налаштування / таймаут | частково | 4/4 | `AiChatViewModelTest`, `RunAiChatUseCaseTest`; скриншот офлайн |
| AC-7 | Регресія Лаби 5 | — | так: 133 тести зелені, старий блок AI command працює | `BaselineAiTest`, `AiCommandViewModelTest`, ручний E1 у старому блоці |

### 5.3 Обов'язкові перевірки

| Сценарій | Результат |
|---|---|
| Попередній мобільний сценарій | старий блок AI command на Studio працює; регресійні тести зелені |
| Некоректне / порожнє введення | Send неактивна; у ViewModel → `InvalidInput` без Retry (`AiChatViewModelTest.blank_isInvalidInput_notRetryable`); запит не про звук → пояснення моделі (скриншот «яка погода?») |
| Немає мережі | «No connection. Simple commands still work offline…» + Retry; після вмикання мережі Retry працює (скриншот `e2-chat.png`) |
| Помилка налаштування | `AiUnavailable`, без автоповторів (`AiChatViewModelTest.configError_isAiUnavailable_onlyManualRetry`) |
| Повільна відповідь | таймаут 20 с → «AI did not answer in time» + Retry (`slowModel_isTimeout_notEndlessLoading`); реальний випадок «до»: очікування до 3 хв (розділ 1.3) |
| `git diff` перед commit | `google-services.json`, ключів і debug token немає (файл у `.gitignore`) |

### 5.4 Що не можна узагальнити за цим набором

- **Межові значення (знайдено на E3):** коли delay уже 1/4 (найдовший), «зроби довшим» двічі дало 1/4 → 1/8 (коротше),
  хоча текст моделі каже «довше». Модель не враховує, що далі подовжувати нікуди. Контрольний набір стартує з 1/8,
  тому критерій це не ловить; потрібна перевірка напрямку на пристрої або окремий приклад на межу (L-30).
- 5 прикладів і по 3 запуски на хмарний приклад — це не статистика: модель недетермінована (E2: 1 з перших 2 онлайн-запусків дав EQ без значень).
- Baseline працював на `gemini-3.5-flash` (фактично 3.6-flash), нова версія — на `gemini-3.5-flash-lite`: частина різниці в E2 може бути від моделі, а не від змін.
- Перевірено лише англійські й українські фрази одного автора; інші формулювання та мови не перевірялись.
- Час відповіді виміряно в один вечір; навантаження на Gemini змінюється (розділ 1.3: «high demand», таймаути).
- Чат пам'ятає 3 останні репліки і лише до закриття застосунку.
