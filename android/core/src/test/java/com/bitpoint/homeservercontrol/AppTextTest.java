package com.bitpoint.homeservercontrol;
import org.junit.Test;
import static org.junit.Assert.*;
public class AppTextTest {
 @Test public void englishUsesLocalCatalogAndDoesNotTranslateUserNamesOrPaths(){try{AppText.language("en");assertEquals("Server",AppText.tr("Сервер"));assertEquals("Connection",AppText.tr("Подключение"));assertEquals("Queue",AppText.tr("Очередь"));assertEquals("/tmp/Фото отпуск.png",AppText.tr("/tmp/Фото отпуск.png"));assertEquals("report.pdf",AppText.tr("report.pdf"));}finally{AppText.language("ru");}}
 @Test public void russianAndUnknownLanguagesPreserveExistingLabels(){AppText.language("ru");assertEquals("Сервер",AppText.tr("Сервер"));AppText.language("unknown");assertEquals("ru",AppText.language());}
 @Test public void translatedFormattingPreservesAllValues(){try{AppText.language("en");assertEquals("Version 1.41 · build 63",String.format(java.util.Locale.US,AppText.tr("Версия %1$s · сборка %2$d"),"1.41",63));}finally{AppText.language("ru");}}
}
