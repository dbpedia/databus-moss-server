package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ModuleStore {

    private final Path modulesRoot;
    private final Map<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public ModuleStore(Path modulesRoot) {
        this.modulesRoot = modulesRoot;
    }

    public List<String> listModules() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modulesRoot)) {
            List<String> ids = new ArrayList<>();
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    ids.add(entry.getFileName().toString());
                }
            }
            return ids;
        }
    }

    public Optional<String> loadModule(String moduleId) throws IOException {
        Path moduleFile = modulesRoot.resolve(moduleId).resolve("module.yml");
        if (!Files.exists(moduleFile)) {
            return Optional.empty();
        }
        ReentrantReadWriteLock lock = locks.computeIfAbsent(moduleId, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();
        try {
            return Optional.of(Files.readString(moduleFile));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveModule(String moduleId, String yamlContent) throws IOException {
        Path moduleDir = modulesRoot.resolve(moduleId);
        Files.createDirectories(moduleDir);
        Path moduleFile = moduleDir.resolve("module.yml");

        ReentrantReadWriteLock lock = locks.computeIfAbsent(moduleId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Files.writeString(moduleFile, yamlContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteModule(String moduleId) throws IOException {
        Path moduleDir = modulesRoot.resolve(moduleId);
        if (!Files.exists(moduleDir)) {
            return false;
        }
        ReentrantReadWriteLock lock = locks.computeIfAbsent(moduleId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Files.walk(moduleDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            return true;
        } finally {
            lock.writeLock().unlock();
            locks.remove(moduleId); // cleanup lock map
        }
    }
}
