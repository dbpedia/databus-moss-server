package org.dbpedia.moss.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SubResourceHandler implements org.dbpedia.moss.servlets.modules.ISubResourceHandler {

    private final Path parentDirectory;
    private final String filename;
    private final String mimeType;
    private final Predicate<String> validator;
    private final Consumer<String> onChanged;
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public SubResourceHandler(Path parentDirectory, String filename, String mimeType,
                              Predicate<String> validator, Consumer<String> onChanged) {
        this.parentDirectory = parentDirectory;
        this.filename = filename;
        this.mimeType = mimeType;
        this.validator = validator;
        this.onChanged = onChanged;
    }

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String parentId) throws IOException {
        Optional<String> content = load(parentId);
        if (content.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Subresource not found for parent: " + parentId);
            return;
        }
        resp.setContentType(mimeType);
        resp.getWriter().write(content.get());
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String parentId) throws IOException {
        String body = req.getReader().lines().reduce("", (acc, line) -> acc + line + "\n");
        save(parentId, body);
        resp.setContentType(mimeType);
        resp.getWriter().write(body);
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String parentId) throws IOException {
        boolean deleted = delete(parentId);
        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Subresource not found for parent: " + parentId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    public Optional<String> load(String parentId) throws IOException {
        Path file = parentDirectory.resolve(parentId).resolve(filename);
        if (!Files.exists(file)) return Optional.empty();

        ReentrantReadWriteLock lock = locks.computeIfAbsent(parentId, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();
        try {
            return Optional.of(Files.readString(file));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void save(String parentId, String content) throws IOException {
        if (!validator.test(content)) throw new IllegalArgumentException("Validation failed");

        Path dir = parentDirectory.resolve(parentId);
        Files.createDirectories(dir);
        Path file = dir.resolve(filename);

        ReentrantReadWriteLock lock = locks.computeIfAbsent(parentId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } finally {
            lock.writeLock().unlock();
        }

        if (onChanged != null) onChanged.accept(parentId);
    }

    public boolean delete(String parentId) throws IOException {
        Path file = parentDirectory.resolve(parentId).resolve(filename);
        if (!Files.exists(file)) return false;

        ReentrantReadWriteLock lock = locks.computeIfAbsent(parentId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Files.delete(file);
        } finally {
            lock.writeLock().unlock();
        }

        if (onChanged != null) onChanged.accept(parentId);
        return true;
    }
}
