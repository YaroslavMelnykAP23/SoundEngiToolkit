# Архітектура

Додаток складається з інтерфейсу (Compose), аудіорушія (три потоки), AI-шару та запланованих хмарних сервісів Firebase.

## Схема

```mermaid
flowchart TB
    UI["UI: MainActivity.kt<br/>Compose: keys, sliders, AI prompt"]
    AI["Ai.kt: command layer<br/>Prompt to JSON actions"]
    SRC["CommandSource<br/>Rules, Firebase AI Logic (Gemini)"]
    STORE["ProjectStore: Room + WAV<br/>projects, AI history"]

    subgraph FB["Firebase (planned)"]
        AUTH["Authentication"]
        FS["Firestore<br/>projects, presets"]
        ST["Cloud Storage<br/>samples, recordings"]
    end

    subgraph ENGINE["Engine.kt: audio thread"]
        SYNTH["Synth<br/>Oscillator, ADSR"]
        SAMPLER["Sampler<br/>Mic recording"]
        FX["Effect chain<br/>EQ, Comp, Delay, Reverb"]
        VOL["Master volume (dB)"]
        OUT["AudioTrack to speaker"]
        SYNTH --> FX
        SAMPLER --> FX
        FX --> VOL --> OUT
    end

    UI -->|prompt| AI
    UI -->|keys, switches, volume| ENGINE
    AI -->|params| ENGINE
    AI <-->|JSON actions| SRC
    UI -->|save, load| STORE
    AI -->|history| STORE
    STORE -->|sample, effects| ENGINE
    UI -.->|sign in| AUTH
    UI -.->|save, load| FS
    SAMPLER -.->|upload, download| ST

    classDef planned stroke-dasharray: 4 3
    class SRC,AUTH,FS,ST planned
```

## Компоненти

| Компонент | Файл | Роль |
|---|---|---|
| UI | `MainActivity.kt` | `Screen` (головний екран з вкладками Studio / Library), `ScopePanel` (осцилограф, курсор, loop, сітка) |
| Рушій | `Engine.kt` | аудіопотоки, запис, ланцюжок ефектів, master volume, кільцевий буфер осцилографа |
| DSP | `Dsp.kt` | `Synth`, `Sampler`, `Eq`, `Comp`, `Delay`, `Reverb` |
| AI-шар | `Ai.kt` | схема JSON-дій (`Ai.SYSTEM`), виконання дій (`Ai.apply`) |
| Джерела команд | `CommandSource.kt` | `RulesSource`, `FirebaseSource`, `HybridSource` |
| Сховище | `Store.kt` | `ProjectStore` (збереження, завантаження, видалення, історія AI), `FxState` (ефекти в JSON), `ViewState` (курсор і маркери) |
| База даних | `Db.kt` | Room: `ProjectEntity`, `AiEntryEntity`, `ProjectDao`, `AiDao`, `AppDb` |
| WAV | `Wav.kt` | запис WAV 16 біт stereo (L=R), читання mono і stereo |
| UI збереження | `LibraryUi.kt` | `SaveDialog`, `ProjectsPanel`, `HistoryPanel` |

Backend: окремого сервера немає. Хмарні функції заплановані через Firebase (див. [decisions.md](decisions.md)).
Інтерфейс має дві вкладки (`TabRow`): **Studio** (запис, осцилограф, клавіші, ефекти, AI-команди, кнопка Save) і **Library** (список збережених записів з Load і Delete). Після Load додаток повертається на Studio. Під час запису перехід на Library заблоковано.
## Потоки виконання

1. **UI-потік (Compose)** малює екран і обробляє дотики.
2. **`audio-out`** цикл по 256 семплів: обнулення буфера → синтезатор і семплер → ефекти (якщо ввімкнені) → master volume → обрізання до ±1 → запис у `scopeBuf` → `AudioTrack.write`.
3. **`audio-in`** читає `AudioRecord`, збирає шматки в пам'ять; після зупинки нормалізує і передає в `Sampler.load`.
4. **Корутина `Dispatchers.IO`** виконує AI-запит і `Ai.apply`, коли користувач натискає Run, а також усі операції з файлами та базою (`ProjectStore`).

Потоки обмінюються даними через поля `@Volatile` без блокувань. В аудіопотоці не можна виділяти пам'ять, блокуватися або викликати мережу.

## Потік даних

**Запис і відтворення:** мікрофон → `audio-in` → `Sampler.data` → UI читає той самий масив для малювання; `audio-out` читає його ж під час відтворення.

**Осцилограф Live:** `audio-out` пише вихід у кільцевий буфер (8192 семпли) → `Engine.scope` вирівнює по переходу через нуль → UI малює лінію.

**AI-команда:** текст → `Ai.ask` → `HybridSource` (правила, інакше Gemini) → JSON `{"actions":[...]}` → `Ai.apply` змінює поля ефектів → `audio-out` бачить нові значення на наступному блоці → UI синхронізує перемикачі.

**Збереження:** Save → `ProjectStore.save` (IO): `sampler.data` → WAV `filesDir/projects/<uuid>.wav` (спершу `.tmp`, потім перейменування) → запис у Room з BPM, курсором, маркерами та JSON ефектів. Load → читання WAV → `e.bpm`, `FxState.apply`, `sampler.load` → `ScopePanel` бачить новий масив і відновлює курсор та маркери зі `ViewState`. Зберігається сухий запис, а не результат з ефектами.

**Історія AI:** після кожного Run `ProjectStore.logAi` пише запит, JSON, журнал і джерело (rules, cloud) у Room; зберігається до 200 записів.

Формат JSON: [ai-component.md](ai-component.md), [api-contract.md](api-contract.md).

## Аудіопараметри

Mono, 44 100 Гц, `PCM_FLOAT`, блок 256 семплів, `PERFORMANCE_MODE_LOW_LATENCY`.