package org.dbpedia.moss.indexer;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * Takes care of reindexing resources with index groups.
 * An index group is a range of index configuration files that need to be run for a resource
 */
public class IndexerManager {

    private final int THREAD_POOL_SIZE = 1;

    private ConcurrentLinkedDeque<IndexingTask> tasks;
    
    private HashMap<String, IndexGroup> indexGroups;
    
    private ThreadPoolExecutor executor;

    private ScheduledExecutorService scheduler;

    private static final Logger logger = LoggerFactory.getLogger(IndexerManager.class);

    public IndexerManager(List<IndexGroup> groups) {
        this.tasks = new ConcurrentLinkedDeque<IndexingTask>();
        this.executor = new ThreadPoolExecutor(
            THREAD_POOL_SIZE, 
            THREAD_POOL_SIZE, 
            0L, 
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>()
        );
        this.indexGroups = new HashMap<String, IndexGroup>();
       
        for(IndexGroup group : groups) {
            logger.info("Added index group {}", group.getName());
            this.indexGroups.put(group.getName(), group);
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start(int tickIntervalSeconds) {
        this.scheduler.scheduleAtFixedRate(() -> tick(), 0, tickIntervalSeconds, TimeUnit.SECONDS); 
    }

    public void stop() {
        try {
            this.scheduler.shutdown();
        } catch (SecurityException e) {
            System.err.println(e);
        }
    }

    private void tick() {
        // frage prozessmanager, ob wir kapazitäten haben für einen neuen prozess
        if(executor.getActiveCount() >= THREAD_POOL_SIZE) {
            return;
        }

        IndexingTask task = tasks.poll();

        if(task == null) {
            return;
        }

        executor.submit(task);
    }

    public void updateResource(String resourceURI, String indexGroupName) {


        if(indexGroupName != null && !indexGroups.containsKey(indexGroupName)) {
            throw new IllegalArgumentException(String.format("Index group not found: %s", indexGroupName));
        }

        IndexGroup indexGroup = null;

        if(indexGroupName != null) {
            indexGroup = indexGroups.get(indexGroupName);
        }

        IndexingTask indexingTask = new IndexingTask(resourceURI, indexGroup);

        // Leave, if a task like this already exists in the list
        for(IndexingTask existingTask : tasks) {
            if(existingTask.equals(indexingTask)) {
                return;
            }
        }

        tasks.add(indexingTask);
    }
}
