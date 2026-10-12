# Knowledge Base: SoundEngiToolkit

Android-додаток (Kotlin, Jetpack Compose): семплер + синтезатор + ефекти + керування текстовими командами. Пакет: `com.example.soundengitoolkit`.

## З чого почати

| Мета | Файл |
|---|---|
| Зрозуміти продукт | [project-overview.md](project-overview.md) |
| Що готово, що ні, критерії готовності | [requirements.md](requirements.md) |
| Як влаштований код і потоки | [architecture.md](architecture.md) |
| Формат AI-команд і модель даних | [api-contract.md](api-contract.md) |
| Як працює AI-частина | [ai-component.md](ai-component.md) |
| Як перевіряти | [testing.md](testing.md) |
| Що зламано чи ризиковано | [known-limitations.md](known-limitations.md) |
| Чому зроблено саме так | [decisions.md](decisions.md) |
| Правила для coding-агента | [ai-instructions.md](ai-instructions.md) |
| Історія змін | [../CHANGELOG.md](../CHANGELOG.md) |

Порядок читання для нового розробника або агента: overview → architecture → requirements → known-limitations → ai-instructions.

## Налаштування

1. `app/src/main/AndroidManifest.xml`, перед `<application>`:
   ```xml
   <uses-permission android:name="android.permission.RECORD_AUDIO" />
   <uses-permission android:name="android.permission.INTERNET" />
   ```
2. `app/build.gradle.kts`, залежності для хмарного AI (версії звіряти з документацією Firebase):
   ```kotlin
   implementation(platform("com.google.firebase:firebase-bom:<версія>"))
   implementation("com.google.firebase:firebase-ai")
   implementation("com.google.firebase:firebase-appcheck-debug")
   ```
3. Для хмарного AI: `google-services.json` у `app/` і плагін `com.google.gms.google-services`. Файл **не комітити**. Без нього додаток працює, а AI обмежується правилами.
4. Запуск: `./gradlew assembleDebug`, потім Run на реальному пристрої. Версії `minSdk`, `compileSdk`, Kotlin і Compose дивитися в `app/build.gradle.kts` (рішення про `minSdk` ще відкрите, див. [decisions.md](decisions.md)).

## Ключові факти

| Параметр | Значення |
|---|---|
| Аудіо | mono, 44 100 Гц, `PCM_FLOAT`, блок 256 семплів |
| Потоки | UI, `audio-out`, `audio-in`, корутина `Dispatchers.IO` для AI |
| Файли коду | `MainActivity.kt`, `Engine.kt`, `Dsp.kt`, `Ai.kt`, `CommandSource.kt` |
| Backend | немає; заплановано Firebase (Auth, Firestore, Storage) |
| Автоматичні тести | немає |

## Словник

- **dBFS**: децибели відносно повної шкали; 0 dBFS це максимум, вище починається обрізання.
- **Peak / RMS**: найбільше миттєве значення та середньоквадратичний рівень сигналу.
- **BPM**: темп, ударів на хвилину; бит = чверть, такт = 4 біти (розмір 4/4).
- **Snap to beats**: прив'язка курсора й маркерів до найближчого біта.
- **Курсор**: точка, з якої починається відтворення. **Loop A-B**: ділянка між двома маркерами, що зациклюється.
- **Master volume**: гучність виходу в дБ (змінює звук). **View gain**: масштаб картинки осцилографа (звук не змінює).
- **Ефектний ланцюжок**: EQ → компресор → delay → reverb.
- **AI-шар** (`Ai.kt`): код, що виконує JSON-дії. **Джерело команд** (`CommandSource`): те, що перетворює текст на JSON (правила або модель Gemini). Сама модель звуку не обробляє.
