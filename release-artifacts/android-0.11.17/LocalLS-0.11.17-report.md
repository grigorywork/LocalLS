# LocalLS Android 0.11.17 — функциональная проверка и исправления

0.11.17 / versionCode 37. Устанавливайте поверх 0.11.16 без удаления приложения. Постоянная release-подпись, ключи доступа, настройки и принятые файлы сохранены.

- Создание, импорт и удаление ключа выбранного подключения больше не изменяют независимую SSH-пару альтернативного сервера. Ранее связанные адреса одного проверенного сервера продолжают использовать общую личность.
- Остановка во время запуска не возвращает устаревший статус ожидания и закрывает созданную с задержкой очередь. Быстрый повторный старт ждёт закрытия прежнего listener, а не сталкивается с занятым портом.
- Экспорт .pub и отчёта диагностики переживает пересоздание Activity во время выбора места. Пустой отчёт не выдаётся за успешно сохранённый. В состояние экрана попадают только публичные данные экспорта; приватный ключ и парольная фраза не сохраняются.
- Диагностика использует название LocalLS и текущую версию вместо исторических заголовков.

Проверки: 182 JVM-теста + 7 Python-проверок, 0 ошибок/пропусков. Добавлены 24 сценария служб, ключей, профилей, документов, удаления/выбора файлов, истории, диагностики, мониторинга, оформления, системного Wi-Fi и поиска SSH. Lint: 0 ошибок, 26 предупреждений. Аудиты проекта/архитектуры без ошибок; apksigner/zipalign и метаданные обновления с подписанной 0.11.16 проверены.

[Матрица проверки функций и ограничения](https://github.com/grigorywork/LocalLS/blob/android-v0.11.17/android/docs/FUNCTIONS-0.11.17.md).

Android-сценарии исполняются в Robolectric; SSH/SFTP и HTTPS используют настоящие сокеты на изолированных loopback адресах. Это не испытание на физическом телефоне или полном Android AVD. Реальное переключение Wi-Fi/VPN, OEM/Doze, аппаратный Keystore и физическая установка не проверены. Матрица описывает выполненные сценарии, а не гарантирует все возможные состояния каждой функции.


Полный прогон: JVM {'tests': 182, 'failures': 0, 'errors': 0, 'skipped': 0}; Lint {'Error': 0, 'Warning': 26}. Python — 7 проверок правил обновления/подписи. Проект/архитектура: 0 ошибок, 87 production-классов / 5 модулей. Release и instrumentation APK собраны. ADB devices пуст; instrumentation-тесты на устройстве не исполнялись.

Пакет `com.bitpoint.homeservercontrol`, code 37 > 36, сертификат SHA-256 `a2230c3032135def93cd5e8224ca3e26ad8e98f294b5e372a33b1e468caa221a`, APK SHA-256 `f1a30db0b15299523c6c11b4840b0ea8aaad322d5eff31fb28cb73c43c95ee15`. Проверены apksigner/zipalign, неизменность package/сертификата и рост versionCode относительно подписанной 0.11.16. Ключ подписи вне репозитория. Windows/Fedora остаются 0.9.5.

Воспроизведение: reproduce.log содержит 5 новых сценариев и 3 подтверждённых падения production-логики — создание/удаление ключа затрагивало независимый альтернативный сервер, остановленный запуск возвращал waiting-state. Перепроверка экспортного picker выявила потерю публичного ключа при recreation; reproduce-report.log воспроизвёл потерю подготовленной диагностики. В раннем тесте импорта и двух тестах диалогов была ошибка обвязки: click кнопки AlertDialog отправляет сообщение в главный Looper, а исходная проверка читала состояние до его обработки. Обвязка исправлена явной обработкой Looper; импорт не объявляется отдельной найденной production-ошибкой. Начальная ошибка компиляции обращения к protected Activity.onSaveInstanceState также устранена через ActivityController. Финальный verified-build.log завершился успешно.

EmbeddedServerService: публикация live-state и onDestroy синхронизированы. boundPort/fingerprint/waiting/running публикуются только для незавершённого экземпляра после успешного старта и питания. Очередь, чей конструктор завершился уже после onDestroy, закрывается worker-cleanup; ветки ошибки запуска также очищают ресурсы. Семофор одного listener координирует экземпляры службы: новый старт ждёт завершения предыдущего закрытия, максимум 30 секунд. Ожидание работает в worker, не в UI. Позднее закрытие старого экземпляра не публикует новый running/waiting-state. ОС по-прежнему управляет жизнью foreground-службы.

ServerLifecycleTest использует настоящие SFTP-listener и клиентские соединения. Личность тестового сервера генерируется в памяти, а AutoReceive заменён счётчиком ресурсов и управляемым конструктором: это позволяет детерминированно остановить запуск на нужной границе. Настоящие SQLite/AutoReceive/SAF-проверки остаются в LastModIntegrationTest. Сценарии проверяют повтор start, release порта/очереди, занятый порт, destroy во время конструктора, разрешение клиента с передачей и запретом второго разрешения, быстрый stop/start с задержанным закрытием прежнего порта.

SecurityActivity сохраняет/удаляет только ключ выбранной логической привязки. Независимый alternateHost больше не используется как неявная цель. Общие алиасы ранее проверенного одного сервера сохраняют прежнюю семантику. Экспортный Bundle содержит публичную строку и host/port/user; при несовпадении контекста публичная строка не восстанавливается. Приватный ключ и импортная парольная фраза не сериализуются. MainActivity сохраняет подготовленный несекретный отчёт, восстанавливает его при recreation и не записывает пустой payload как успешный экспорт. Заголовок берётся из метаданных установленного пакета.

LocalServerDiscovery разделяет получение интерфейса и ограниченный /24-probe без изменения обычного способа выбора интерфейса. Новый тест вызывает probe с 127.0.0.1 и настоящими тестовыми SSH/HTTP-banner listener на других loopback IP. Домашняя/внешняя сеть не сканировалась. Поиск проверен при отсутствии IP и отмене с сохранением interrupt.

Матрица содержит 54 уникальные ссылки на классы/методы. Упаковка сверяет их с XML полного прогона: каждая ссылка существует и завершилась без failure/error/skipped. Это проверка заявленного покрытия сценариев, не процент покрытия всех веток и не физическая эмуляция роутера/OEM.

Оставшиеся 26 предупреждений Lint не скрывались: исторические пути Termux, неиспользуемые ресурсы, количество View, монохромный значок, строки/локализация/RTL. Ранее описанные ограничения process-local сетевой очереди, повтора папки, SAF/MediaStore, сторонних серверов и авторизованной записи сохраняются. Новые сетевые функции не внедрялись.

# Проверка функций LocalLS Android 0.11.17

21 октября 2026. Таблица связывает функции приложения с выполненными автоматическими сценариями. Итоговые числа тестов и подпись APK — в [отчёте выпуска](RELEASE-0.11.17.md). Каждая ссылка на класс и метод проверяется по результатам завершившегося прогона, а не только по наличию исходника теста.

Среда: Robolectric моделирует Android Activity, Service, диалоги, lifecycle, разрешённые документы, уведомления и JobScheduler. SSH/SFTP и HTTPS выполняются настоящими библиотеками и сокетами на изолированных loopback IP. Это не полный аппаратный Android-эмулятор: Android Emulator/AVD и физический телефон в среде отсутствуют. Там, где нужны Android Keystore или управляемое зависание провайдера, используются явно указанные тестовые замены.

| Функция | Проверенный сценарий | Доказательство |
| --- | --- | --- |
| Настройка IP, порта, пользователя | Неверные данные не заменяют рабочий профиль; VPN без выдуманного локального адреса | `ConnectionFormTest#vpnOnlyProfileSavesAndInvalidPortDoesNotOverwriteIt` |
| Вставка адреса подключения | Некорректный ввод остаётся в диалоге, корректный заполняет нужный режим | `ConnectionFormTest#invalidImportStaysOpenAndValidImportFillsActiveEndpoint` |
| Текущий Wi-Fi IP | Появление до запуска сервера, смена и отключение сети, несколько адресов | `WifiAddressTest#wifiChangeRefreshesVisibleScreenAndDisconnectRemovesOldIp` |
| Проверка порта | Открытый TCP не выдаётся за авторизованный SSH; закрытый порт показывает отказ | `AppFunctionsTest#networkPortCheckDistinguishesOpenPortFromAuthenticatedSsh` |
| Поиск SSH в сети | Найден SSH-banner, исключены HTTP и собственный адрес; проверена отмена | `LocalServerDiscoveryTest#discoveryFindsOnlySshBannerAndSkipsOwnPhone`, `LocalServerDiscoveryTest#cancellationPreservesInterruptAndStopsDiscovery` |
| SSH и fingerprint | Замена сервера блокируется до предложения клиентского ключа | `EmbeddedSftpServerTest#changedServerAtNewAddressIsRejectedBeforeClientAuthentication` |
| Смена адреса того же сервера | Разные IP, 60 МиБ, сохранение пары и pin обоими редакторами | `LanConnectionIntegrationTest#differentIpsTransfer60MiBAndBothEditorsKeepAccessWhenServerAddressChanges` |
| Восстановление утраченной привязки | Только известный pin и единственная подходящая клиентская пара | `AccessRecoveryTest#oldVersionLostAddressBindingRecoversOnlyExistingApprovedKey` |
| Неоднозначный/нечитаемый ключ | Не заменяется автоматически | `AccessRecoveryTest#multipleDifferentKeysForSameServerRequireManualChoice`, `RouteIdentityTest#addressChangeCannotReplaceUnreadableStoredDestinationIdentity` |
| Создание клиентского ключа | Подтверждение создания; независимый ключ альтернативного сервера сохранён | `KeyScreenFlowTest#replacingSelectedKeyNeverReplacesDifferentAlternateServerIdentity` |
| Удаление клиентского ключа | Удаляется выбранный, другой сервер сохраняет свой доступ | `KeyScreenFlowTest#removingSelectedKeyNeverRemovesDifferentAlternateServerIdentity` |
| Импорт приватного ключа | Отмена не меняет ключ; подтверждение сохраняет выбранный; другой сервер не меняется | `KeyScreenFlowTest#keyImportRequiresConfirmationAndNeverChangesOtherServer` |
| Экспорт .pub | Пересоздание Activity во время picker не теряет ключ; записана только публичная часть | `KeyScreenFlowTest#publicKeyExportSurvivesActivityRecreationWhilePickerIsOpen` |
| Запуск/повторный запуск службы | Настоящий SSH-listener, повторная команда без второй очереди, закрытие порта | `ServerLifecycleTest#startRepeatedStartAndStopReleasePortAndReceiveResources` |
| Остановка во время запуска | Задержано создание очереди; после destroy нет позднего статуса/оставшихся ресурсов | `ServerLifecycleTest#stopDuringReceiverCreationCannotLeakQueueOrPublishLateWaitingState` |
| Быстрый stop/start | Новый listener ждёт завершения предыдущего, затем успешно занимает тот же порт | `ServerLifecycleTest#immediateRestartWaitsForPreviousListenerCleanup` |
| Занятый порт | Ошибка запуска не оставляет открытую очередь | `ServerLifecycleTest#bindFailureDoesNotLeaveReceiveQueueOpenAfterServiceDestroy` |
| Разрешение телефона-клиента | Сначала вход закрыт; после подтверждения реальная передача; второе разрешение не заменяет ключ | `ServerLifecycleTest#approvedClientCanTransferAndSecondApprovalCannotReplaceIt` |
| Границы доступа сервера | Отказ паролю/чужому ключу, обходу папки, links, shell, exec, forwarding | `EmbeddedSftpServerTest#passwordAndWrongUsernameAndUnknownKeyCannotAuthenticate`, `EmbeddedSftpServerTest#traversalLinksCommandsAndForwardingAreDenied` |
| Файлы и папки в обе стороны | Дерево с пустой папкой/Unicode и коллизией; экран закрыт, служба копирует | `LastModIntegrationTest#folderCopyUsesServiceAfterScreenClosesAndPreservesEmptyFoldersAndCollisions` |
| Выбор всех файлов | Выбраны текущие элементы области телефона; задание направлено своему серверу | `FilePaneFlowTest#selectAllQueuesCurrentPhoneItemsForCurrentOwnServerFolder` |
| Выбор места сохранения | Папка телефона независима от серверного автосохранения и сохраняется при открытии заново | `FilePaneFlowTest#choosingPhoneFolderDoesNotChangeServerAutoSaveAndSurvivesReopen` |
| Навигация/пересоздание | Сохраняется «Вверх»; старое выделение очищается при обновлении | `FilePaneFlowTest#upNavigationRemainsAvailableAfterScreenRecreation`, `FilePaneFlowTest#refreshingPhoneClearsOldSelectionBeforeAsyncListingFinishes` |
| Новая папка | Создана только в выбранной папке телефона | `FilePaneFlowTest#phoneMenuCreatesFolderOnlyInSelectedDestination` |
| Переименование | MediaStore rename без перезаписи чужого имени | `FilePaneFlowTest#appDownloadsRenameUsesMediaStoreAndDoesNotOverwriteExistingName` |
| Удаление | Непустой каталог сохраняется; пустой удаляется; соседний файл сохранён | `FilePaneFlowTest#nonEmptyPhoneFolderIsNotDeletedAndEmptyFolderCanBeRemoved` |
| Имена с * / ? | Действия буквальны, соседние файлы/каталоги не удаляются | `EmbeddedSftpServerTest#deletingLiteralWildcardFileNeverDeletesItsNeighbours`, `EmbeddedSftpServerTest#globCharactersRemainLiteralAcrossBrowseCopyAndRename` |
| Одновременное совпадение имени | Не перезаписывается файл, появившийся прямо перед окончательным rename | `EmbeddedSftpServerTest#collisionAtFinalServerRenamePreservesExistingFileAndRetriesAnotherName` |
| Смена подключения при открытых файлах | Старое выделение/результат picker не отправляется новому серверу | `FileConnectionFlowTest#changingProfileCannotSendOldServerSelectionToNewAddress`, `FileConnectionFlowTest#pickerResultFromPreviousProfileIsNotSentAfterRecreation` |
| Отмена/повтор передачи | Отмена при open/close; повтор не дублирует завершённое задание | `TransferRecoveryTest#cancellationWhileClosingDestinationRemovesOnlyNewCopy`, `TransferRecoveryTest#repeatedRetryDoesNotQueueOrReplayTheSameSuccessfulCopy` |
| Локальное сохранение в фоне | 60 МиБ сохраняются после закрытия экрана | `LocalFileExportIntegrationTest#sixtyMiBExportContinuesAfterBrowserActivityIsDestroyed` |
| Автосохранение сервера | Сохранение без UI, дедупликация, отказ папки, повтор без перенастройки ключа | `LastModIntegrationTest#completedUploadAutomaticallySavesWithoutServerUiAndDeduplicatesEvents`, `LastModIntegrationTest#deniedAutomaticDestinationLeavesOriginalAndRetriesWithoutReauthorizingClient` |
| SQLite-очередь и остановка сохранения | Отмена при close сохраняет исходник и pending; следующий запуск повторяет | `LastModIntegrationTest#stoppingAutoSaveDuringProviderCloseKeepsOriginalAndPendingRetry` |
| Роли устройства | Клиентская сеть скрыта ползунком; локальный сервер не выключается | `RoleVisibilityTest#clientNetworkSettingsAppearOnlyWhenRoleSliderEnablesThem` |
| Клиентский мониторинг | JobScheduler включается/выключается; в серверной роли не выполняется | `AppFunctionsTest#clientMonitoringCanBeEnabledAndDisabledWithoutChangingConnection`, `RoleVisibilityTest#staleClientMonitoringJobDoesNotRunInServerOnlyRole` |
| Перенос профиля | Экспорт после picker/rotation без ключей; импорт с подтверждением и сохранением доверия | `AppFunctionsTest#profileExportSurvivesPickerRecreationAndContainsOnlyOriginalPublicParameters`, `AppFunctionsTest#profileImportNeedsConfirmationPreservesKeysAndClearsOldAgentSession` |
| Повреждённый профиль | Файл больше лимита не меняет настройки | `AppFunctionsTest#malformedProfileNeverChangesSavedConnection` |
| Графики/очистка | Отмена сохраняет; подтверждение удаляет только метрики, не файлы/подключение/журнал | `AppFunctionsTest#clearingChartsRequiresConfirmationAndPreservesTransfersAndConnection` |
| Диагностика/буфер | Секретные записи не копируются; очистка журнала не удаляет графики/профиль | `AppFunctionsTest#diagnosticCopyAndTransferClearDoNotExposeSecretsOrClearCharts` |
| Экспорт диагностики | Подготовленный отчёт переживает пересоздание и записывается в документ | `AppFunctionsTest#exportedDiagnosticReportSurvivesPickerRecreation` |
| Оформление | Тема/форма значков сохраняются без изменения доступа; размеры шрифтов/системные панели | `AppFunctionsTest#appearanceChangesPersistWithoutChangingConnectionOrTrust`, `UiRenderingTest#dashboardCaptionsFitEveryThemeAndFontSize` |
| Настройка Wi-Fi | Учётные данные уходят явному системному обработчику; поле очищено и не сохраняется | `AppFunctionsTest#wifiCredentialsGoOnlyToExplicitSystemHandlerAndAreCleared` |
| Инструкция | Открывается из APK без сети | `EmbeddedServerScreenTest#setupInstructionsAreBundledAndOpenWithoutNetwork` |

Что остаётся за пределами этих проверок:

- Настоящее подключение Android к Wi-Fi, изоляция клиентов роутера, гостевая сеть, модем/точка доступа и произвольные OEM-имена интерфейсов. Поиск /24 проверен с изолированными адресами; выбор реального интерфейса зависит от устройства.
- Установленный внешний Tailscale, системный диалог VPN и маршрут между разными сетями. Проверены поля/маршруты профиля, а не реальное подключение к аккаунту VPN. Новая сетевая функциональность не внедрялась.
- Аппаратный Android Keystore, шифрование на конкретном устройстве, разрешения/особенности реальных DocumentsProvider, ограничения батареи, долгое пребывание в Doze и убийство процесса системой.
- Физическая установка обновления. Проверены подпись, package, versionCode и метаданные совместимости; instrumentation APK собран, но не запускался на телефоне.
- Полный перебор всех комбинаций состояний каждой функции. Таблица описывает конкретные выполненные сценарии, а не доказательство отсутствия всех ошибок. Прочие ограничения [аудита 0.11.16](AUDIT-0.11.16.md) сохраняются.
