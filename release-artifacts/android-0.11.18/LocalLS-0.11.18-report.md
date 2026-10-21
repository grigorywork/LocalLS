# LocalLS Android 0.11.18 — исправления очереди и проверка функций

0.11.18 / versionCode 38. Подписанный APK для установки поверх 0.11.17 без удаления приложения. Постоянный сертификат выпуска сохранён; доступ к ключам и сохранённым файлам не меняется.

- Очередь остаётся активной, пока есть ожидающие задания, даже после успеха или ошибки первого файла. При ошибке остальных заданий не останавливаем; повтор остаётся доступным.
- Запоздалое завершение отменённой очереди не заменяет результат новой передачи. Отложенная остановка привязана к конкретной команде; остановленная служба не публикует новый прогресс и не захватывает питание повторно.
- Удалённая или недоступная исходная папка больше не выдаётся за успешно скопированную пустую. Настоящие пустые папки продолжают копироваться.
- Папка назначения фиксируется для всего задания и его повтора. Смена настройки во время копирования не раскладывает файлы одного задания по разным папкам.

Проверки: 190 JVM-тестов + 7 Python-проверок, без ошибок и пропусков. Добавлены 8 сценариев очереди, отмены, жизненного цикла, папок и выбора назначения. На исходной 0.11.17 пять новых сценариев воспроизвели сбои; на исправленной версии они проходят. Lint: 0 ошибок, 26 предупреждений. Проверены apksigner/zipalign, неизменность package и сертификата, рост versionCode относительно подписанной 0.11.17.

[Матрица функций и ограничения](https://github.com/grigorywork/LocalLS/blob/android-v0.11.18/android/docs/FUNCTIONS-0.11.18.md).

Android-сценарии исполняются в Robolectric; SSH/SFTP и HTTPS используют настоящие сокеты на изолированных loopback IP. Физический телефон и Android Emulator/AVD отсутствуют. Реальное переключение Wi-Fi/VPN, OEM/Doze, аппаратный Keystore и физическая установка не проверены. Матрица содержит конкретные выполненные сценарии, а не гарантию отсутствия всех возможных ошибок.

Полный прогон: JVM {'tests': 190, 'failures': 0, 'errors': 0, 'skipped': 0}; Lint {'Error': 0, 'Warning': 26}. Python — 7 проверок правил обновления и подписи. Проект/архитектура: 0 ошибок, 87 production-классов / 5 модулей. Release и instrumentation APK собраны. ADB devices пуст; instrumentation-тесты на устройстве не исполнялись.

Пакет `com.bitpoint.homeservercontrol`, code 38 > 37, сертификат SHA-256 `a2230c3032135def93cd5e8224ca3e26ad8e98f294b5e372a33b1e468caa221a`, APK SHA-256 `5dda172df811597a73a142ad4f8df08389410596d5da0729f872fcb11eff2d96`. Проверены apksigner/zipalign, прежний package/сертификат и рост versionCode относительно подписанной 0.11.17. Ключ подписи вне репозитория. Windows/Fedora остаются 0.9.5.

Воспроизведение: baseline-isolated.log — 13 сценариев TransferRecoveryTest, 5 падений на точном production-коде из source.zip выпущенной 0.11.17. Это преждевременный idle после успеха и ошибки при наличии следующего задания, поздняя отмена поверх результата новой очереди, отсутствующая исходная папка как ложная пустая копия, изменение назначения между файлами одного задания. В код подставлялись только два проверяемых production-файла; собственные исправления возвращены из побайтовых scratch-копий. Репозиторий и пользовательские изменения не сбрасывались.

В первом промежуточном прогоне двух тестов мешали общие статические latch и lastFailed из предыдущего метода Robolectric. Обвязка теперь явно сбрасывает эти тестовые состояния в Before. Повторное воспроизведение на исходной 0.11.17 проведено уже с изолированной обвязкой. Ошибка тестовой изоляции не объявляется дополнительным production-дефектом. focused-final.log — успешная перепроверка очереди и файловых панелей после исправления. Дополнительно проверен повтор с прежним назначением после смены настроек. Финальный verified-build.log содержит весь набор тестов без пропусков и успешную сборку.

TransferForegroundService: main-команды и публикация worker-состояния координируются lifecycle-lock. Успех/ошибка задания учитывает оставшиеся задания: active остаётся true, если они есть. Команда отмены отмечает поколение очереди. Итог отмены фиксируется при фактическом освобождении очереди, а не отложенным callback с устаревшим контекстом. Отложенная остановка проверяет ревизию команды, количество заданий и destroyed, а stopSelf получает сохранённый startId. Старый callback не останавливает более новый запуск. Проверка отмены, публикация прогресса и получение блокировок питания также учитывают destroyed под тем же lock. Копирование потоков и журналирование не выполняются под lifecycle-lock. Журнал не меняет успешный результат передачи.

Для SAVE/DOWNLOAD запрос без явной destination получает снимок выбранной папки при принятии службой. Этот Bundle входит в сохранённый запрос повтора. Выбранная в интерфейсе другая папка действует на будущие задания, но не разделяет уже принятое задание между каталогами. Явное назначение от файловой панели сохраняет прежнюю семантику.

FileNodes: listFiles == null для вложенной исходной папки означает отказ, а не пустое успешное перечисление. Собственный корень сервера при первом открытии создаётся отдельно; его отсутствие не ломает начальную настройку. Настоящая пустая папка остаётся корректной. Обычный файл как папка отклоняется. При ошибке копирования папок могут оставаться уже созданные подкаталоги/полные копии; автоматического отката дерева нет. Повтор дерева может создать дубликаты завершённых элементов. Исходники не удаляются.

Добавлены 8 сценариев TransferRecoveryTest с настоящими локальными потоками и управляемым тестовым DocumentsProvider. Задержки open/истории выставляются latch, а worker-barrier подтверждает завершение worker до проверки запоздалого UI-callback. Отдельный сценарий проверяет замену экземпляра службы; он был успешным и на исходной версии и служит дополнительной регрессией. Матрица содержит 62 уникальных Class#method; упаковка проверяет каждую ссылку по XML завершившегося полного прогона. Это перечень выполненных сценариев, не процент покрытия всех веток.

Оставшиеся 26 предупреждений Lint не скрывались. Сохраняются ограничения process-local очереди, внешних SFTP-серверов, SAF/MediaStore и физического Android/OEM, описанные в предыдущих аудитах и матрице. Долговременная очередь с восстановлением сетевых заданий после убийства процесса не реализована. Новые сетевые функции в этом исправлении не добавлялись.

# Проверка функций LocalLS Android 0.11.18

21 октября 2026. Таблица связывает функции приложения с выполненными автоматическими сценариями. Итоговые числа тестов и подпись APK — в [отчёте выпуска](RELEASE-0.11.18.md). Каждая ссылка на класс и метод проверяется по результатам завершившегося прогона, а не только по наличию исходника теста.

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
| Несколько заданий | Успех первого задания не скрывает оставшуюся очередь | `TransferRecoveryTest#completedFirstJobKeepsQueueActiveUntilRemainingCopiesFinish` |
| Ошибка внутри очереди | Остальные задания продолжаются, неудачное доступно для повтора | `TransferRecoveryTest#failedFirstJobKeepsRemainingQueueActiveAndRetainsRetry` |
| Запоздалая отмена | Отложенная обработка старой отмены не затирает новую успешную передачу | `TransferRecoveryTest#lateCancelledCallbackCannotOverwriteNewSuccessfulQueue` |
| Пересоздание службы передачи | Callback остановленного экземпляра не меняет результат нового | `TransferRecoveryTest#stoppedInstanceCallbackCannotChangeReplacementServiceResult` |
| Исчезнувший исходный каталог | Нет ложного успеха с пустой папкой; доступен повтор | `TransferRecoveryTest#missingSourceFolderIsReportedAsFailureInsteadOfSuccessfulEmptyCopy` |
| Пустой каталог / обычный файл | Пустая папка копируется; обычный файл не считается папкой | `TransferRecoveryTest#emptySourceFolderCopiesSuccessfullyButFileCannotBeListedAsFolder` |
| Смена места сохранения во время передачи | Одно задание целиком сохраняет выбранное назначение, в том числе для повтора | `TransferRecoveryTest#changingDefaultPhoneFolderCannotSplitOneQueuedRequestAcrossDestinations` |
| Повтор и прежнее назначение | После отказа исходника и смены настроек повтор сохраняет файл в первоначально выбранную папку | `TransferRecoveryTest#retryKeepsOriginallySelectedDefaultFolderAfterSettingsChange` |
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
