package org.dbpedia.moss.indexer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;
import org.dbpedia.moss.servlets.modules.ModuleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes care of reindexing resources with index groups. An index group is a
 * range of index configuration strings that need to be run for a resource.
 */
public class IndexerManager {

    private static final int THREAD_POOL_SIZE = 1;

    private final ConcurrentLinkedDeque<IndexingTask> tasks;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService scheduler;

    private final HashMap<String, IndexGroup> indexGroups;

    private static final Logger logger = LoggerFactory.getLogger(IndexerManager.class);

    public IndexerManager() {
        this.tasks = new ConcurrentLinkedDeque<>();
        this.executor = new ThreadPoolExecutor(
                THREAD_POOL_SIZE,
                THREAD_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
        );
        this.indexGroups = new HashMap<>();

        var moduleStore = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());

        try {
            List<MossModule> modules = moduleStore.listModules();
            for (MossModule module : modules) {
                moduleStore.loadSubResource(module.getId(), "indexer.yml").ifPresent(content -> {
                    IndexGroup group = new IndexGroup(module.getId(), new String[]{content});
                    indexGroups.put(module.getId(), group);
                    logger.info("Added index group for module {}", module.getId());
                });
            }
        } catch (IOException e) {
            logger.error("Failed to initialize index groups from modules: {}", e.getMessage());
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void updateIndexGroup(List<IndexGroup> groups) {
        this.indexGroups.clear();
        for (IndexGroup group : groups) {
            logger.info("Updated/Created index group {}", group.getName());
            this.indexGroups.put(group.getName(), group);
        }
    }

    public void start(int tickIntervalSeconds) {
        this.scheduler.scheduleAtFixedRate(this::tick, 0, tickIntervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        try {
            this.scheduler.shutdown();
            this.executor.shutdown();
        } catch (SecurityException e) {
            logger.error("Failed to stop indexer manager: {}", e.getMessage());
        }
    }

    private void tick() {
        if (executor.getActiveCount() >= THREAD_POOL_SIZE) {
            return;
        }

        IndexingTask task = tasks.poll();
        if (task == null) {
            return;
        }

        executor.submit(task);
    }

    public void updateResource(String resourceURI, String indexGroupName) {
        if (indexGroupName != null && !indexGroups.containsKey(indexGroupName)) {
            return;
            // throw new IllegalArgumentException(String.format("Index group not found: %s", indexGroupName));
        }

        IndexGroup indexGroup = null;
        if (indexGroupName != null) {
            indexGroup = indexGroups.get(indexGroupName);
        }

        IndexingTask indexingTask = new IndexingTask(resourceURI, indexGroup);

        for (IndexingTask existingTask : tasks) {
            if (existingTask.equals(indexingTask)) {
                return;
            }
        }

        tasks.add(indexingTask);
    }

    public void removeIndexGroup(String moduleId) {
        if (moduleId == null || !indexGroups.containsKey(moduleId)) {
            logger.warn("Attempted to remove non-existent index group {}", moduleId);
            return;
        }
        indexGroups.remove(moduleId);
        logger.info("Removed index group {}", moduleId);
    }

    public void createOrUpdateIndexGroup(String moduleId, String content) {
        if (moduleId == null || content == null) {
            throw new IllegalArgumentException("Module ID and group content cannot be null");
        }
        indexGroups.put(moduleId, new IndexGroup(moduleId, new String[]{content}));
        logger.info("Created/Updated index group {}", moduleId);
    }
}
