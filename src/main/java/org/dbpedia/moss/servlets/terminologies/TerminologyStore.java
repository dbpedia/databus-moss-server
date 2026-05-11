package org.dbpedia.moss.servlets.terminologies;

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

import org.dbpedia.moss.config.MossTerminology;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public class TerminologyStore {

    private final ObjectMapper yamlMapper;
    private final Path terminologyDirectory;
    private final Map<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public TerminologyStore(Path terminologyDirectory) {
        this.terminologyDirectory = terminologyDirectory;

        yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    // ---------------- List Terminologies ----------------
    public List<MossTerminology> listTerminologies() throws IOException {
        List<MossTerminology> terminologies = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(terminologyDirectory)) {
            for (Path termDir : stream) {
                Path termFile = termDir.resolve("terminology.yml");
                if (Files.exists(termFile)) {
                    MossTerminology terminology = yamlMapper.readValue(termFile.toFile(), MossTerminology.class);
                    terminologies.add(terminology);
                }
            }
        }
        return terminologies;
    }

    // ---------------- Terminology CRUD ----------------
    public Optional<MossTerminology> loadTerminology(String terminologyId) throws IOException {
        Path terminologyFile = terminologyDirectory.resolve(terminologyId).resolve("terminology.yml");
        if (!Files.exists(terminologyFile)) {
            return Optional.empty();
        }

        ReentrantReadWriteLock lock = locks.computeIfAbsent(terminologyId, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();
        try {
            String yamlContent = Files.readString(terminologyFile);
            MossTerminology terminology = yamlMapper.readValue(yamlContent, MossTerminology.class);
            return Optional.of(terminology);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveTerminology(MossTerminology terminology) throws IOException {
        Path terminologyFolder = terminologyDirectory.resolve(terminology.getId());
        Files.createDirectories(terminologyFolder);

        Path terminologyFile = terminologyFolder.resolve("terminology.yml");

        ReentrantReadWriteLock lock = locks.computeIfAbsent(terminology.getId(), k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            yamlMapper.writeValue(terminologyFile.toFile(), terminology);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteTerminology(String terminologyId) throws IOException {
        Path terminologyFolder = terminologyDirectory.resolve(terminologyId);
        if (!Files.exists(terminologyFolder)) {
            return false;
        }

        ReentrantReadWriteLock lock = locks.computeIfAbsent(terminologyId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Files.walk(terminologyFolder)
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
    public Optional<String> loadSubResource(String terminologyId, String subResourceFile) throws IOException {
        Path file = terminologyDirectory.resolve(terminologyId).resolve(subResourceFile);
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        ReentrantReadWriteLock lock = locks.computeIfAbsent(terminologyId, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();
        try {
            return Optional.of(Files.readString(file));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveSubResource(String terminologyId, String subResourceFile, String content) throws IOException {
        Path terminologyFolder = terminologyDirectory.resolve(terminologyId);
        Files.createDirectories(terminologyFolder);
        Path file = terminologyFolder.resolve(subResourceFile);

        ReentrantReadWriteLock lock = locks.computeIfAbsent(terminologyId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteSubResource(String terminologyId, String subResourceFile) throws IOException {
        Path file = terminologyDirectory.resolve(terminologyId).resolve(subResourceFile);
        if (!Files.exists(file)) {
            return false;
        }

        ReentrantReadWriteLock lock = locks.computeIfAbsent(terminologyId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Files.delete(file);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
