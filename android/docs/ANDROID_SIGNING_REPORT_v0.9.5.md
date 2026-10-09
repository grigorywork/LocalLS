# LocalLS Android 0.9.5 — отчёт о восстановлении совместимой подписи

Дата: 9 октября 2026 (UTC).
Итог: готовый APK из указанного коммита переподписан сохранённым ключом опубликованной 0.9.4. Содержимое приложения не изменялось.

| Параметр | Значение |
|---|---|
| Коммит preview | `d32b4062557d66000848dd9a9dea6e67e0d0cb53` |
| Файл | `LocalLS-v0.9.5-debug.apk` |
| Размер | 1418730 байт |
| SHA-256 APK | `4cf2a043914dba45658fd372ca23210d609eedbb9a0e4d90306f382dfd88311d` |
| Сертификат SHA-256 | `3b4c5a568105f73bf999f288a041255a57ee5cfc83253b99e5f2c07789f1f9ba` |
| Package | `com.bitpoint.homeservercontrol` |
| versionName / versionCode | 0.9.5 / 14 |
| minSdk / targetSdk | 24 / 37 |
| Подпись | APK Signature Scheme v2, один подписант |
| SDK tools | Android Build Tools 36.0.0 |

## Происхождение и совместимость

Preview скачан непосредственно из `grigorywork/LocalLS`, ветка `codex/android-ui-modules`, строго по указанному коммиту. Его SHA-256 совпадает с заданным: `22d8892b725232835cd9f3037f2701386547309318c99bbad7d73c95132e1b07`.

Опубликованная 0.9.4 скачана отдельно с https://github.com/grigorywork/LocalLS/releases/download/android-v0.9.4/LocalLS-v0.9.4-debug.apk.
SHA-256 этого файла: `473aeade948671d24b696843e2f61d6b36d1eda70f511dcf6fecc3b5d6b62291`.
Сертификаты сохранённого ключа, опубликованной 0.9.4 и итоговой 0.9.5 совпадают. Package сохранён, versionCode повышен с 13 до 14: устранена несовместимость подписи при обновлении поверх официальной 0.9.4. Установка на физическое устройство в этом этапе не выполнялась.

## Реально выполненные проверки

- Проверены SHA-256 preview и публичный сертификат сохранённого ключа.
- `apksigner verify --verbose --print-certs --min-sdk-version 24` прошёл для опубликованной 0.9.4, preview и итоговой 0.9.5.
- Перед подписью выполнен `zipalign -f -P 16 4`; после подписи `zipalign -c -P 16 -v 4` прошёл.
- `aapt dump badging` подтвердил package, versionName/versionCode, minSdk/targetSdk.
- `aapt dump xmltree ... AndroidManifest.xml` успешно прочитал Manifest.
- ZIP CRC проверены. Дублирующих ZIP entries нет.
- Сравнены наборы имён, размеры и SHA-256 всех 72 содержательных ZIP entries: полное совпадение. Добавленных, удалённых и изменённых содержательных файлов: 0.
- Из сравнения исключается только стандартная метаинформация JAR-подписи. Блок APK-подписи вне ZIP entries и выравнивающие промежутки изменились; это объясняет изменение общего размера файла. Manifest, resources и dex не изменялись.

По поручению исходного чата сборка preview уже выполнена там с 8 тестами, 18 снимками и Lint без ошибок. Здесь Gradle-сборка и эти тесты повторно не запускались: задача состояла только в переподписи именно готового проверенного APK. Машинный исходный `android/docs/apk-verification-0.9.5.json` соответствует SHA-256 preview. В коммите `android/BUILD_REPORT.md` и `android/LOCAL_BUILD_REPORT.md` описывают более ранние версии; их нельзя выдавать за свежий отчёт 0.9.5.

## Публикация и сохранность

Файлы предназначены только для черновика единого релиза `v0.9.5`. Релиз не публикуется: сборки других платформ ещё проходят. Код, main, теги и опубликованные старые релизы этой операцией не изменяются. Приватный ключ и пароль не экспортированы и не загружены в GitHub. Данные установленного приложения не удалялись.

Сертификат остаётся прежним debug-сертификатом. Переподпись обеспечивает совместимость с официальной 0.9.4, но не превращает debug APK в production release-сборку.

Машинное сравнение: `APK_CONTENT_COMPARISON_v0.9.5.json`.
Проверка подписи: `APKSIGNER_VERIFY_v0.9.5.txt`.
Метаданные: `AAPT_BADGING_v0.9.5.txt`.
Контрольная сумма: `SHA256SUMS-v0.9.5-Android.txt`.

## English

The supplied 0.9.5 preview APK was re-signed using the preserved signer of the published 0.9.4 APK. The signer certificate matches exactly. Package remains `com.bitpoint.homeservercontrol`, versionCode is 14, versionName is 0.9.5, minimum Android API is 24.

APK signature verification for minimum API 24, ZIP CRC, 4-byte/16-KiB alignment and Android Manifest decoding passed. All 72 substantive ZIP entries have identical names, lengths and SHA-256 hashes before and after re-signing. Only signing metadata and alignment/signature padding changed. No rebuild, instrumentation or physical device installation was performed in this signing task. Upstream tests were reported by the requesting thread; stale upstream build reports must not be presented as current 0.9.5 results.

Output SHA-256: `4cf2a043914dba45658fd372ca23210d609eedbb9a0e4d90306f382dfd88311d`.
Signer certificate SHA-256: `3b4c5a568105f73bf999f288a041255a57ee5cfc83253b99e5f2c07789f1f9ba`.
The APK and public verification reports are staged in a GitHub draft release only. No release publication, source/main/tag updates, private-key/password export or application-data deletion is authorized or performed.
