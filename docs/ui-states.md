# Стани інтерфейсу AI-екрана (Лабораторна 7, Задача 3)

Екран з AI-функцією один — вкладка **AI** (`AiChatScreen`, стан `ChatUiState` у `AiChatViewModel`).
Сценарій і критерії: [final-scenario.md](final-scenario.md); шлях даних: [architecture.md](architecture.md).

## 1. Екран × стан

| Стан | Як визначається в коді | Що бачить користувач | Яку дію може виконати |
|---|---|---|---|
| Початковий | `messages` порожній, `loading = false` | Підказка «Describe the sound you want…» і чипи-приклади (`make it brighter`, `reverb off`, `more space`); Send неактивна, доки поле порожнє | Натиснути чип (текст підставляється в поле) або ввести свій запит |
| Завантаження | `loading = true` | Спінер «AI is thinking…» під останнім повідомленням; поле і Send вимкнені | Чекати; не більше 20 с, далі — стан помилки (таймаут) |
| Успіх | остання `Answer` з `ChatAnswer.Changed` | Підсумок, блок **Changed** (параметр: було → стало), блок **Why**, попередження ⚠ про обрізані значення, джерело (`rules` / `cloud`) | Перейти на Studio і послухати (Play / Loop), Save; або надіслати наступний запит |
| Немає придатного результату | остання `Answer` з `ChatAnswer.NothingChanged` | Чесне повідомлення, що нічого не змінено, і чому (запит не про звук; дія вже виконана; модель не дала значень) | Переформулювати запит або натиснути приклад з історії; параметри не змінено |
| Помилка | остання `Failure` | Червоний текст за категорією `AiError` без назв класів і stack trace; кнопка **Retry**, якщо повтор має сенс | Retry (без перезапуску застосунку); офлайн — надіслати просту команду (`reverb off`), яка працює правилами |

## 2. Помилки: пояснення і дія

| `AiError` | Повідомлення | Дія користувача |
|---|---|---|
| `InvalidInput` | «Enter a command (up to 500 characters)». На практиці UI не дає надіслати порожнє і обрізає введення до 500 символів (лічильник `n/500` з 400 символів) | Виправити введення |
| `Network` | «No connection. Simple commands still work offline: …» | Увімкнути мережу → Retry; або проста команда |
| `Timeout` | «AI did not answer in time. Try again» | Retry |
| `Busy` | «AI is busy right now. Try again in a minute» | Retry пізніше |
| `AiUnavailable` | «Cloud AI is unavailable (setup or quota). Simple commands still work.» | Прості команди; Retry після виправлення налаштувань (App Check, квота) |
| `InvalidResponse` | «The AI reply could not be understood» | Retry (модель недетермінована) |
| `Unknown` | «Something went wrong» | Retry |

Оригінальний виняток пишеться лише в Logcat (тег `AiChat`, `Log.w` з причиною) — виправлення W-1.
Помилки Save / Load / Delete на Studio і Library показуються зрозумілим текстом, оригінал — у Logcat з тегом `Library` (W-4).

## 3. Особливі ситуації

| Ситуація | Поведінка | Де в коді |
|---|---|---|
| Повторне натискання Send | Під час запиту кнопка і поле вимкнені; навіть якщо виклик прийде, `send` ігнорується при `loading = true` — один запит, одна відповідь | `AiChatScreen` (`enabled`), `AiChatViewModel.send` |
| Повторне натискання Retry | Retry показується лише під останньою помилкою і зникає, щойно почався запит (`canRetry` вимагає `!loading`) | `ChatUiState.canRetry` |
| Перехід у фон і повернення | Стан чату живе у `ViewModel` (`StateFlow`), запит виконується у `viewModelScope` і не обривається при згортанні; після повернення видно результат або помилку. Текст у полі — `rememberSaveable` | `AiChatViewModel`, `AiChatScreen` |
| Поворот екрана | Орієнтацію зафіксовано (`portrait`), `Activity` не перестворюється — запис, ефекти й чат зберігаються (W-3; інші зміни конфігурації — L-13) | `AndroidManifest.xml` |
| Відмова в дозволі мікрофона | AI-чат дозволів не потребує. Дозвіл `RECORD_AUDIO` потрібен для Record на Studio: при відмові — повідомлення «Microphone permission denied. Enable it in Settings > Apps > SoundEngiToolkit > Permissions»; решта застосунку (завантаження збереженого запису, синтезатор, AI-чат) працює; повторне натискання Record знову просить дозвіл | `MainActivity.Screen` (`perm`, `begin`) |
| Незавершений процес | Після вбивства процесу системою повідомлення чату і Retry губляться (L-24); параметри ефектів збережені лише в записах, зроблених через Save | — |
