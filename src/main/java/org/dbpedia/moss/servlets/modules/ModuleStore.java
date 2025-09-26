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

import org.dbpedia.moss.config.MossModule;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public class ModuleStore {

    private final ObjectMapper yamlMapper;
    private final Path moduleDirectory;
    private final Map<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public ModuleStore(Path moduleDirectory) {
        this.moduleDirectory = moduleDirectory;

        yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    // ---------------- List Modules ----------------
    public List<MossModule> listModules() throws IOException {
        List<MossModule> modules = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(moduleDirectory)) {
            for (Path moduleDir : stream) {
                Path moduleFile = moduleDir.resolve("module.yml");
                if (Files.exists(moduleFile)) {
                    MossModule module = yamlMapper.readValue(moduleFile.toFile(), MossModule.class);
                    modules.add(module);
                }
            }
        }
        return modules;
    }

    // ---------------- Module CRUD ----------------
    public Optional<MossModule> loadModule(String moduleId) throws IOException {
        Path moduleFile = moduleDirectory.resolve(moduleId).resolve("module.yml");
        if (!Files.exists(moduleFile)) {
            return Optional.empty();
        }

        ReentrantReadWriteLock lock = locks.computeIfAbsent(moduleId, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();
        try {
            String yamlContent = Files.readString(moduleFile);
            MossModule module = yamlMapper.readValue(yamlContent, MossModule.class);
            return Optional.of(module);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveModule(MossModule module) throws IOException {
        Path moduleFolder = moduleDirectory.resolve(module.getId());
        Files.createDirectories(moduleFolder);

        Path moduleFile = moduleFolder.resolve("module.yml");
        // Path indexerFile = moduleFolder.resolve("indexer.yml");
        // LookupIndexer indexer = module.generateIndexer();

        // validateIndexer(indexer); // throws if invalid

        ReentrantReadWriteLock lock = locks.computeIfAbsent(module.getId(), k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            yamlMapper.writeValue(moduleFile.toFile(), module);
            // yamlMapper.writeValue(indexerFile.toFile(), indexer);
        } finally {
            lock.writeLock().unlock();
        }
    }

    
    public boolean deleteModule(String moduleId) throws IOException {
        Path moduleFolder = moduleDirectory.resolve(moduleId);
        if (!Files.exists(moduleFolder)) {
            return false;
        }

        ReentrantReadWriteLock lock = locks.computeIfAbsent(moduleId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Files.walk(moduleFolder)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                        }
                    });
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ---------------- Subresource CRUD ----------------
    public Optional<String> loadSubResource(String moduleId, String subResourceFile) throws IOException {
        Path file = moduleDirectory.resolve(moduleId).resolve(subResourceFile);
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        ReentrantReadWriteLock lock = locks.computeIfAbsent(moduleId, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();
        try {
            return Optional.of(Files.readString(file));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveSubResource(String moduleId, String subResourceFile, String content) throws IOException {
        Path moduleFolder = moduleDirectory.resolve(moduleId);
        Files.createDirectories(moduleFolder);
        Path file = moduleFolder.resolve(subResourceFile);

        ReentrantReadWriteLock lock = locks.computeIfAbsent(moduleId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteSubResource(String moduleId, String subResourceFile) throws IOException {
        Path file = moduleDirectory.resolve(moduleId).resolve(subResourceFile);
        if (!Files.exists(file)) {
            return false;
        }

        ReentrantReadWriteLock lock = locks.computeIfAbsent(moduleId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Files.delete(file);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
