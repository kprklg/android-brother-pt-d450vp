# Шаг 3. Архитектура приложения и возможности редактора

## Стек и структура кода

Приложение написано на **Kotlin** с использованием **Jetpack Compose** и **Material 3** (`minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`).

```text
app/src/main/java/com/ptd450vp/labelprinter/
├── MainActivity.kt              # Точка входа, обработка USB_DEVICE_ATTACHED, Photo Picker
├── model/
│   ├── TapeSpec.kt              # Спецификации лент TZe (3.5/6/9/12/18 мм, 180 dpi, 128 пинов)
│   ├── LabelDocument.kt         # Модель документа, блоков (Text/QR/Barcode/Image) и PrintOptions
│   └── LabelTemplate.kt         # Модель шаблона этикетки
├── render/
│   ├── BarcodeGenerator.kt      # Генерация QR (UTF-8), Code 128, EAN-13, Code 39, Data Matrix (ZXing)
│   ├── Dithering.kt             # Пороговая бинаризация и дизеринг Флойда–Стейнберга
│   └── LabelRenderer.kt         # Композиция блоков в 1-битный MonochromeImage и WYSIWYG Bitmap
├── printer/
│   ├── PrinterStatus.kt         # Парсер 32-байтного ответа ESC i S
│   ├── PackBitsEncoder.kt       # TIFF PackBits (RLE) кодирование/декодирование
│   ├── RasterProtocol.kt        # Сборка бинарного задания печати Brother P-touch Raster
│   ├── PrinterTransport.kt      # Интерфейс транспорта + MockPrinterTransport (симулятор)
│   └── UsbPrinterTransport.kt   # Android USB Host API (USB OTG) транспорт
├── data/
│   └── TemplateRepository.kt    # 6 встроенных шаблонов + хранение пользовательских в JSON
└── ui/
    ├── Theme.kt                 # Цветовая схема Material 3
    ├── LabelViewModel.kt        # Состояние редактора, рендеринг, USB и печать
    ├── EditorScreen.kt          # Главный экран редактора, предпросмотр и диалог отреза
    ├── TemplatesSheet.kt        # Диалог выбора, сохранения и удаления шаблонов
    └── PrinterDialog.kt         # Диалог USB OTG / симулятора, статуса, калибровки и hex-дампа
```

## Ключевые особенности реализации

1. **Единый 1-битный конвейер рендеринга:**
   `LabelRenderer.render(document)` формирует `MonochromeImage` (1 бит на точку, без сглаживания) и сразу строит `previewBitmap` напрямую из тех же битов. Пользователь видит на экране ровно те пиксели, которые уйдут на термоголовку.
2. **Автоподбор шрифта:**
   Для многострочного текста реализован бинарный поиск оптимального `textSize`, чтобы все строки аккуратно поместились в печатную высоту выбранной ленты TZe.
3. **Разделение транспорта (`PrinterTransport`):**
   Весь редактор, шаблоны и генератор растровых пакетов работают одинаково как с реальным `UsbPrinterTransport`, так и с `MockPrinterTransport` — это позволяет отлаживать и тестировать приложение без физического принтера.
4. **Безопасная работа с USB OTG:**
   Перед чтением статуса `UsbPrinterTransport` очищает буфер `Bulk IN` (`drainInputBuffer`), а при отправке задания делит поток на чанки по 4096 байт и после печати пытается считать статус завершения/ошибок.
5. **Подсказка об отрезе после печати:**
   При успешной печати `LabelViewModel` выставляет `showCutPrompt = true`, и `EditorScreen` показывает диалог «Нажмите на нож, чтобы отрезать ленту». Диалог один на всю партию: при `Chain Print` этикетки выходят встык, поэтому дополнительно напоминает разрезать ленту между ними. Закрытие диалога — `dismissCutPrompt()`.
