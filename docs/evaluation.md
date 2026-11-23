# Лабораторна робота 6: оцінка AI-функції

## Етап 1. Вихідна версія (baseline)

| Параметр | Значення |
|---|---|
| Commit | `4415f10` (`4415f108052eee02f091ee063c4dceccfb09417b`), тег `lab6-baseline` |
| Дата | 2026-11-23 |
| Пристрій | <модель / емулятор, версія Android> |
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
| B-7 | У коді `gemini-3.5-flash`; за сторінкою моделей Firebase AI Logic (перевірено 23.11.2026) у Gemini Developer API запити до неї перенаправляються на `gemini-3.6-flash`, рекомендована заміна `gemini-3.8-flash` (Stable, без білінгу). Тобто baseline фактично працював на 3.6-flash | `FirebaseSource(modelName = ...)` | у новій версії перейти на `gemini-3.8-flash` і зафіксувати назву (REQ-5) |
| B-8 | Формат відповіді контролюється лише промптом (`responseMimeType`), без `responseSchema`; ` ```json ` прибирається вручну | `FirebaseSource` | модель може повернути невалідну структуру |
| B-9 | `ai-component.md` описує fallback «UI ловить Throwable і показує в блоці логу», а після Лаби 5 помилки йдуть через `AiError` і `AiUiState.Error` | `docs/ai-component.md` | документацію треба оновити |
| B-11 | Правила ігнорують заперечення: `off`, `вимкни`, `без` → завжди `on=true` | `RulesSource.ask`: `put("on", true)` для delay/compressor/reverb | вимкнути ефект командою неможливо (E5); `✓` у лозі вводить в оману |
| B-10 | Нерозпізнана команда і «нічого не змінилось» не відрізняються від успіху: стан «немає результату» відсутній | `AiCommandViewModel.run` → `Success("No actions recognized")` | немає окремого стану «немає придатного результату» |
