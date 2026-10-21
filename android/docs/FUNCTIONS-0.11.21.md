# Проверка функций LocalLS Android 0.11.21

21 октября 2026. Таблица связывает функции приложения с выполненными автоматическими сценариями. Итоговые числа тестов и подпись APK — в [отчёте выпуска](RELEASE-0.11.21.md). Каждая ссылка на класс и метод проверяется по результатам завершившегося прогона, а не только по наличию исходника теста.

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
| Загрузка списка облачной папки | EXTRA_LOADING не выдаётся за пустой завершённый список | `FilePaneFlowTest#loadingDocumentCursorIsNotAConfirmedEmptyFolder` |
| Ошибка облачного провайдера | EXTRA_ERROR отклоняется без показа личных деталей провайдера | `FilePaneFlowTest#providerErrorCursorIsNotAConfirmedEmptyFolder` |
| Удаление при неполном списке | Контроль: пустая папка удаляется; папка с loading-списком не передаётся на удаление | `FilePaneFlowTest#deleteCannotTreatLoadingCloudFolderAsEmpty` |
| Частичный облачный список | Даже непустой loading-список не принимается за полное дерево; после завершения доступен | `FilePaneFlowTest#partialLoadingCursorIsRejectedUntilProviderFinishes` |
| Повтор обновления области телефона | Во время loading область не готова; ручное обновление после загрузки возвращает файлы | `FilePaneFlowTest#phonePaneRecoversAfterLoadingProviderFinishes` |
| Отказ MediaStore | null Cursor — ошибка доступа, а не успешно пустые загрузки | `FilePaneFlowTest#nullDownloadsCursorIsAnAccessFailureInsteadOfEmptySuccess` |
| Папка только для чтения | Не заменяет место записи телефона; остаётся допустимой папкой-источником импорта | `FilePaneFlowTest#readOnlyFolderCannotReplacePhoneDestinationButCanBeImported` |
| Быстрый перезапуск автосохранения | Новый обработчик ждёт закрытия прежней копии одного SQLite-задания | `LastModIntegrationTest#replacementReceiverWaitsForClosingCopyBeforeProcessingSameQueue` |
| Смена настройки во время автосохранения | Файл остаётся в выбранной при старте папке, сообщение называет фактическое место | `LastModIntegrationTest#changingAutoSaveFolderWhileClosingReportsActualDestination` |
| Недоступное перечисление места приёма | Автосохранение остаётся pending; после загрузки папки повтор успешен без смены ключей | `LastModIntegrationTest#loadingAutoSaveDestinationKeepsPendingSourceUntilRetry` |
| Имена с * / ? | Действия буквальны, соседние файлы/каталоги не удаляются | `EmbeddedSftpServerTest#deletingLiteralWildcardFileNeverDeletesItsNeighbours`, `EmbeddedSftpServerTest#globCharactersRemainLiteralAcrossBrowseCopyAndRename` |
| Одновременное совпадение имени | Не перезаписывается файл, появившийся прямо перед окончательным rename | `EmbeddedSftpServerTest#collisionAtFinalServerRenamePreservesExistingFileAndRetriesAnotherName` |
| Смена подключения при открытых файлах | Старое выделение/результат picker не отправляется новому серверу | `FileConnectionFlowTest#changingProfileCannotSendOldServerSelectionToNewAddress`, `FileConnectionFlowTest#pickerResultFromPreviousProfileIsNotSentAfterRecreation` |
| Отмена/повтор передачи | Отмена при open/close; повтор не дублирует завершённое задание | `TransferRecoveryTest#cancellationWhileClosingDestinationRemovesOnlyNewCopy`, `TransferRecoveryTest#repeatedRetryDoesNotQueueOrReplayTheSameSuccessfulCopy` |
| Несколько заданий | Успех первого задания не скрывает оставшуюся очередь | `TransferRecoveryTest#completedFirstJobKeepsQueueActiveUntilRemainingCopiesFinish` |
| Ошибка внутри очереди | Остальные задания продолжаются, неудачное доступно для повтора | `TransferRecoveryTest#failedFirstJobKeepsRemainingQueueActiveAndRetainsRetry` |
| Запоздалая отмена | Отложенная обработка старой отмены не затирает новую успешную передачу | `TransferRecoveryTest#lateCancelledCallbackCannotOverwriteNewSuccessfulQueue` |
| Пересоздание службы передачи | Callback остановленного экземпляра не меняет результат нового | `TransferRecoveryTest#stoppedInstanceCallbackCannotChangeReplacementServiceResult` |
| Исчезнувший исходный каталог | Нет ложного успеха с пустой папкой; доступен повтор | `TransferRecoveryTest#missingSourceFolderIsReportedAsFailureInsteadOfSuccessfulEmptyCopy` |
| Пустой каталог / обычный файл | Пустая папка копируется; обычный файл не считается папкой | `TransferRecoveryTest#emptySourceFolderCopiesSuccessfullyButFileCannotBeListedAsFolder` |
| Смена места сохранения во время передачи | Одно задание целиком сохраняет выбранное назначение, в том числе для повтора | `TransferRecoveryTest#changingDefaultPhoneFolderCannotSplitOneQueuedRequestAcrossDestinations` |
| Повтор и прежнее назначение | После отказа исходника и смены настроек повтор сохраняет файл в первоначально выбранную папку | `TransferRecoveryTest#retryKeepsOriginallySelectedDefaultFolderAfterSettingsChange` |
| Локальное сохранение в фоне | 60 МиБ сохраняются после закрытия экрана | `LocalFileExportIntegrationTest#sixtyMiBExportContinuesAfterBrowserActivityIsDestroyed` |
| Повторная доставка команды сохранения | Во время копирования не создаёт вторую загрузку; служба завершается с последним startId | `LocalFileExportIntegrationTest#duplicateIntentDuringCopyDoesNotCreateSecondDownload` |
| Уже завершённый запрос | Повторная доставка после пересоздания службы не копирует ещё раз в том же процессе | `LocalFileExportIntegrationTest#completedIntentCannotReplayAfterServiceRecreation` |
| Некорректная команда во время сохранения | Пустая команда не сбрасывает статус и не останавливает принятое задание | `LocalFileExportIntegrationTest#invalidCommandCannotStopAnAlreadyAcceptedExport` |
| Питание при завершении | CPU lock освобождается сразу после результата, до onDestroy | `LocalFileExportIntegrationTest#completedCopyReleasesPowerWithoutWaitingForServiceDestroy` |
| Явная новая копия | Новый запрос позволяет сохранить файл повторно; прежний lock не остаётся удержанным | `LocalFileExportIntegrationTest#deliberateSecondExportUsesNewRequestAndReleasesSamePowerLock` |
| Нехватка места во время записи | ENOSPC после частичной записи: новая копия удалена, исходник сохранён, повтор успешен | `LocalFileExportIntegrationTest#fullStorageDuringWriteAbortsOnlyNewDownloadAndCanBeRetried` |
| Запрет фонового запуска | Нет созданной загрузки, безопасная диагностика; тот же запрос допускает повтор | `LocalFileExportIntegrationTest#foregroundDenialCreatesNoDownloadAndSameRequestCanRetry` |
| Тайм-аут Android | Копия не публикуется, исходник сохранён, питание освобождено | `LocalFileExportIntegrationTest#androidTimeoutAbortsPendingCopyAndReleasesPowerImmediately` |
| Запоздалый тайм-аут | Уже опубликованный успешный результат не заменяется ошибкой позднего системного callback | `LocalFileExportIntegrationTest#lateAndroidTimeoutCannotReplaceAlreadyCompletedSuccess` |
| Список принятых файлов | Скрыты служебные части загрузки; обычный скрытый .notes виден | `LocalFileExportIntegrationTest#receivedFilesScreenHidesPartialUploadsButKeepsOrdinaryHiddenFiles` |
| Экспорт незавершённого файла | Даже известный точный путь временного файла отклонён до открытия назначения | `LocalFileExporterTest#temporaryUploadCannotBeExportedEvenWithItsExactPath` |
| Обрыв скачивания SSH | Нет ложного успеха, исходник сохранён; новая сессия с прежней личностью скачивает полностью | `EmbeddedSftpServerTest#interruptedDownloadIsNotSuccessAndCanRestartWithSameIdentity` |
| Отказ исходного потока при загрузке | Неполный файл не публикуется; новая сессия удалённого доступа успешно повторяет задание | `EmbeddedSftpServerTest#failingUploadSourceRemovesPartialAndRetryPublishesOnlyCompleteFile` |
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

Проверка ENOSPC использует управляемый поток, который записывает часть данных и выбрасывает IOException с причиной ErrnoException(ENOSPC). Диск среды не заполнялся. Тайм-аут и запрет foreground запуска моделируются Robolectric; это не реальные шесть часов работы Android. Дедупликация локального экспорта хранит последние 256 успешных ID в памяти процесса и не является долговременной очередью. После обрыва не предполагается повторное использование повреждённого SFTP-канала: приложение закрывает сессию, а повтор открывает новую с той же парой и pin.

Сценарии облачных папок используют управляемый DocumentsProvider и настоящие временные файлы. В тесте удаления провайдер умеет рекурсивное удаление: сначала контроль подтверждает работающее удаление пустого каталога, затем проверяется запрет при loading. Реальный аккаунт облака не подключался. Потребители AutoReceive сериализованы внутри процесса; зависший при close провайдер задержит следующий обработчик, исходники и pending-строки при этом сохраняются. Android SAF не предоставляет атомарного удаления «только если папка пуста»: между успешным полным перечислением и удалением остаётся возможность внешнего изменения каталога.

Что остаётся за пределами этих проверок:

- Настоящее подключение Android к Wi-Fi, изоляция клиентов роутера, гостевая сеть, модем/точка доступа и произвольные OEM-имена интерфейсов. Поиск /24 проверен с изолированными адресами; выбор реального интерфейса зависит от устройства.
- Установленный внешний Tailscale, системный диалог VPN и маршрут между разными сетями. Проверены поля/маршруты профиля, а не реальное подключение к аккаунту VPN. Новая сетевая функциональность не внедрялась.
- Аппаратный Android Keystore, шифрование на конкретном устройстве, разрешения/особенности реальных DocumentsProvider, ограничения батареи, долгое пребывание в Doze и убийство процесса системой.
- Физическая установка обновления. Проверены подпись, package, versionCode и метаданные совместимости; instrumentation APK собран, но не запускался на телефоне.
- Полный перебор всех комбинаций состояний каждой функции. Таблица описывает конкретные выполненные сценарии, а не доказательство отсутствия всех ошибок. Прочие ограничения [аудита 0.11.16](AUDIT-0.11.16.md) сохраняются.


Дополнительно в 0.11.21:

| Функция | Проверка | Сценарии |
|---|---|---|
| Список файлов | directoriesStayFirstAndSourceListIsNotMutated | `FileListQueryTest#directoriesStayFirstAndSourceListIsNotMutated` |
| Список файлов | sizeOrderIsNumericAndDoesNotUseDirectoryStorageSize | `FileListQueryTest#sizeOrderIsNumericAndDoesNotUseDirectoryStorageSize` |
| Список файлов | unicodeCaseAndCombiningMarksMatchButWildcardsRemainLiteral | `FileListQueryTest#unicodeCaseAndCombiningMarksMatchButWildcardsRemainLiteral` |
| Список файлов | invalidSavedOrderAndLongFilterAreBounded | `FileListQueryTest#invalidSavedOrderAndLongFilterAreBounded` |
| Список файлов | readablePhoneMenuOpensBothPickersAndIndependentDestination | `FileManagerToolsTest#readablePhoneMenuOpensBothPickersAndIndependentDestination` |
| Список файлов | transferErrorCanBeOpenedWithoutTruncatingDetails | `FileManagerToolsTest#transferErrorCanBeOpenedWithoutTruncatingDetails` |
| Список файлов | filteredSelectAllQueuesOnlyVisiblePhoneSources | `FileManagerToolsTest#filteredSelectAllQueuesOnlyVisiblePhoneSources` |
| Список файлов | sizeSortPreservesSelectedFileAndCheckboxIdentity | `FileManagerToolsTest#sizeSortPreservesSelectedFileAndCheckboxIdentity` |
| Список файлов | inverseSelectionTouchesOnlyVisibleItems | `FileManagerToolsTest#inverseSelectionTouchesOnlyVisibleItems` |
| Список файлов | phoneAndServerFiltersAreIndependentAndNoMatchesIsExplicit | `FileManagerToolsTest#phoneAndServerFiltersAreIndependentAndNoMatchesIsExplicit` |
| Список файлов | recreationKeepsFiltersAndFreshOpenKeepsOnlySort | `FileManagerToolsTest#recreationKeepsFiltersAndFreshOpenKeepsOnlySort` |
| Список файлов | propertiesShowsHumanMetadataWithoutDocumentUri | `FileManagerToolsTest#propertiesShowsHumanMetadataWithoutDocumentUri` |
| Список файлов | cancelledFilterKeepsExistingViewAndSelection | `FileManagerToolsTest#cancelledFilterKeepsExistingViewAndSelection` |
| Список файлов | filterResetDoesNotRestoreHiddenSelection | `FileManagerToolsTest#filterResetDoesNotRestoreHiddenSelection` |
| Список файлов | serverSelectAllCopiesOnlyItsVisibleSourcesToPhoneFolder | `FileManagerToolsTest#serverSelectAllCopiesOnlyItsVisibleSourcesToPhoneFolder` |
| Список файлов | selectedFolderSurvivesSortingAndFilteringAndIsQueuedAsDirectory | `FileManagerToolsTest#selectedFolderSurvivesSortingAndFilteringAndIsQueuedAsDirectory` |

Крупный текст, подписанные кнопки, перенос и закреплённые направления копирования: `UiRenderingTest#fileListRemainsUsableWithLargeControls`. Размеры проверяются при 360×640 dp и шрифте 100%, 150%, 200%; это рендер Robolectric, не физический телефон.
