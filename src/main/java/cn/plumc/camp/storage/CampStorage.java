package cn.plumc.camp.storage;

import cn.plumc.camp.camp.CampInfo;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Only supplementary data lives here. Team entries remain the source of membership and ranks. */
public final class CampStorage implements AutoCloseable {
    private final Path directory;
    private final Logger logger;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Camp-storage");
        thread.setDaemon(true);
        return thread;
    });

    public CampStorage(Path directory, Logger logger) throws IOException {
        this.directory = directory;
        this.logger = logger;
        Files.createDirectories(directory);
    }

    private Path path(String id, String suffix) {
        String safe = Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
        return directory.resolve(safe + suffix);
    }

    public void load(CampInfo camp, int limit) {
        Path reasons = path(camp.id, ".yml");
        if (Files.exists(reasons)) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(reasons.toFile());
                for (String key : yaml.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        if (camp.members.containsKey(uuid) && camp.members.get(uuid).inIDLE())
                            camp.applicationReasons.put(uuid, yaml.getString(key, "未填写理由"));
                    } catch (IllegalArgumentException ignored) {
                        logger.warning("Invalid application UUID in " + reasons + ": " + key);
                    }
                }
            } catch (Exception e) { logger.log(Level.SEVERE, "Cannot load " + reasons, e); }
        }
        Path logs = path(camp.id, ".log");
        if (!Files.exists(logs)) return;
        // Read line by line so the permanent journal does not have to fit into memory.
        TreeMap<Long, String> recent = new TreeMap<>();
        try (BufferedReader reader = Files.newBufferedReader(logs, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    String[] fields = line.split("\t", 2);
                    recent.put(Long.parseLong(fields[0]), new String(Base64.getDecoder().decode(fields[1]), StandardCharsets.UTF_8));
                    while (recent.size() > Math.max(1, limit)) recent.pollFirstEntry();
                } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                    logger.warning("Skipping damaged log line in " + logs);
                }
            }
            camp.tempLogs.putAll(recent);
        } catch (IOException e) { logger.log(Level.SEVERE, "Cannot load " + logs, e); }
    }

    public void saveReasons(String id, Map<UUID, String> reasons) {
        YamlConfiguration yaml = new YamlConfiguration();
        reasons.forEach((uuid, reason) -> yaml.set(uuid.toString(), reason));
        String snapshot = yaml.saveToString();
        submit(() -> {
            Path target = path(id, ".yml");
            Path temp = Files.createTempFile(directory, "applications-", ".tmp");
            try {
                Files.writeString(temp, snapshot, StandardCharsets.UTF_8);
                try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        });
    }

    public void appendLog(String id, long time, String message) {
        String line = time + "\t" + Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8)) + "\n";
        submit(() -> Files.writeString(path(id, ".log"), line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND));
    }

    public void archive(String id) {
        submit(() -> {
            Path archive = directory.resolve("archive");
            Files.createDirectories(archive);
            for (String suffix : List.of(".log", ".yml")) {
                Path source = path(id, suffix);
                if (Files.exists(source)) Files.move(source, archive.resolve(source.getFileName() + "." + UUID.randomUUID()));
            }
        });
    }

    private void submit(IOAction action) {
        writer.submit(() -> {
            try { action.run(); }
            catch (IOException e) { logger.log(Level.SEVERE, "Failed to persist camp data", e); }
        });
    }

    @Override public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) logger.severe("Camp storage has not finished flushing after 30 seconds.");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @FunctionalInterface private interface IOAction { void run() throws IOException; }
}
