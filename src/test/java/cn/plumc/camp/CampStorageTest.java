package cn.plumc.camp;

import cn.plumc.camp.camp.CampInfo;
import cn.plumc.camp.storage.CampStorage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CampStorageTest extends CampTestSupport {
    @Test void reasonsAndLogsSurviveRestartWithBoundedPreviewAndDamagedLineRecovery() throws Exception {
        UUID applicant = UUID.randomUUID();
        CampInfo camp = camp("a");
        camp.inviteMember(null, applicant, "理由\n第二行: & <测试>");
        for (int i = 0; i < 10; i++) storage.appendLog(camp.id, 100 + i, "记录 " + i + "\n含制表符\t和中文");
        storage.close();
        String filename = Base64.getUrlEncoder().withoutPadding().encodeToString("a".getBytes(StandardCharsets.UTF_8));
        Files.writeString(directory.resolve("data").resolve(filename + ".log"), "damaged\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        camp.tempLogs.clear(); camp.applicationReasons.clear();
        try (CampStorage reopened = new CampStorage(directory.resolve("data"), plugin.logger)) { reopened.load(camp, 3); }
        assertEquals("理由\n第二行: & <测试>", camp.applicationReasons.get(applicant));
        assertEquals(3, camp.tempLogs.size());
        // The actual application journal has a newer timestamp and is retained too.
        assertEquals("记录 9\n含制表符\t和中文", camp.tempLogs.get(109L));
        assertEquals(12, Files.readAllLines(directory.resolve("data").resolve(filename + ".log")).size());
    }

    @Test void disbandArchivesHistoryAndReusingIdDoesNotRestoreOldReasonsOrLogs() throws Exception {
        CampInfo camp = camp("a"); UUID applicant = UUID.randomUUID();
        camp.inviteMember(null, applicant, "old");
        storage.archive("a");
        storage.appendLog("a", 1, "new camp");
        storage.saveReasons("a", Map.of());
        storage.close();
        try (var archives = Files.list(directory.resolve("data/archive"))) { assertEquals(2, archives.count()); }
        camp.tempLogs.clear(); camp.applicationReasons.clear();
        try (CampStorage reopened = new CampStorage(directory.resolve("data"), plugin.logger)) { reopened.load(camp, 20); }
        assertEquals(Map.of(1L, "new camp"), camp.tempLogs);
        assertTrue(camp.applicationReasons.isEmpty());
    }

    @Test void unsafeCampIdsCannotEscapeStorageDirectory() throws Exception {
        storage.appendLog("../../escape", 1, "safe");
        storage.close();
        try (var files = Files.list(directory.resolve("data"))) {
            var saved = files.toList();
            assertEquals(1, saved.size());
            assertEquals(directory.resolve("data"), saved.get(0).getParent());
        }
    }
}
