# LocalLS Fedora — проверка комплекта

Подготовлен отдельный проект linux/ и distributions/Fedora/ с инструкциями RU/EN.
Существующие Android/Windows-исходники и установщики не изменялись.

Локальный комплект LocalLS-Fedora-0.1.0-with-guides.zip проверен полностью:
ZIP CRC успешен, пять файлов находятся в отдельной папке Fedora, извлечённый
RPM побайтно совпадает с настоящим собранным и установленным пакетом.
Инструкции имеют UTF-8 BOM и CRLF; отчёт содержит фактические проверки и ограничения.

RPM: 110658328 байт, SHA-256
`8e14601ceec0bee99fcbe7a3a9cefaa9be907a9040496147e6bfee8f1786b923`.
ZIP: 110508170 байт, SHA-256
`65d1b538df5c25886058386ec66f51943e3ba5fbcf96733b15fd33ee6be6520f`.

Ниже приведён результат фактической полной загрузки файлов с GitHub Releases
и проверки SHA-256; ссылки доступны публично без входа в GitHub.

## Публичное скачивание проверено

Релиз: https://github.com/grigorywork/LocalLS/releases/tag/fedora-v0.1.0

Исходный commit: `0f448b9fcad99044d924ea29f2cdf9ba27726db3`. Все шесть файлов опубликованы и полностью
скачаны по публичным URL без авторизации; размеры и SHA-256 совпали.
Из опубликованного ZIP извлечён и повторно проверен исходный RPM, CRC и инструкции.

| Файл | Размер, байт | SHA-256 |
| --- | ---: | --- |
| [LocalLS-0.1.0-Fedora-x86_64.rpm](https://github.com/grigorywork/LocalLS/releases/download/fedora-v0.1.0/LocalLS-0.1.0-Fedora-x86_64.rpm) | 110658328 | `8e14601ceec0bee99fcbe7a3a9cefaa9be907a9040496147e6bfee8f1786b923` |
| [LocalLS-Fedora-0.1.0-with-guides.zip](https://github.com/grigorywork/LocalLS/releases/download/fedora-v0.1.0/LocalLS-Fedora-0.1.0-with-guides.zip) | 110508170 | `65d1b538df5c25886058386ec66f51943e3ba5fbcf96733b15fd33ee6be6520f` |
| [INSTRUCTIONS_RU.txt](https://github.com/grigorywork/LocalLS/releases/download/fedora-v0.1.0/INSTRUCTIONS_RU.txt) | 23365 | `76a8a47413e0338cc4938c1772d2a0acda1852d3d7f2565947611c97cfd6b613` |
| [INSTRUCTIONS_EN.txt](https://github.com/grigorywork/LocalLS/releases/download/fedora-v0.1.0/INSTRUCTIONS_EN.txt) | 14436 | `e2dc7f578a5f6d94896344afa207c71a4abd426742890f938cde398b297fd46a` |
| [BUILD_REPORT.md](https://github.com/grigorywork/LocalLS/releases/download/fedora-v0.1.0/BUILD_REPORT.md) | 13430 | `04030e720a480d26b6a6eac9766778766694f801104c611e89c6a99f736a727f` |
| [SHA256SUMS.txt](https://github.com/grigorywork/LocalLS/releases/download/fedora-v0.1.0/SHA256SUMS.txt) | 455 | `10adf7c93cd66b5b13d861d1ecfd09c587ac6ff4d129eda698abbf9721bc1dfc` |
