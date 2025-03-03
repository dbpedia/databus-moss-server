package org.dbpedia.moss.indexer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;


public class LayerIndexer {
    private String id;
    private HashSet<String> todos;
    // private ExecutorService worker;
    private LayerIndexerConfiguration config;
    @SuppressWarnings("rawtypes")
    private Future indexingFuture;
    private String configRootPath;
    private String lookupBaseURL;
    // private final int fixedPoolSize = 1;

    public LayerIndexer(LayerIndexerConfiguration config, String configRootPath, String lookupBaseURL) {
        this.config = config;
        this.configRootPath = configRootPath;
        this.todos = new HashSet<String>();
        this.id = UUID.randomUUID().toString();
        this.lookupBaseURL = lookupBaseURL;
        // this.worker = Executors.newFixedThreadPool(fixedPoolSize);
    }


    public String getId() {
        return id;
    }

    public LayerIndexerConfiguration getConfig() {
        return this.config;
    }

    public void setConfig(LayerIndexerConfiguration config) {
        this.config = config;
    }

    public HashSet<String> getTodos() {
        return this.todos;
    }

    public void setTodos(HashSet<String> todos) {
        this.todos = todos;
    }

    public void addTodo(String todo) {
        this.todos.add(todo);
    }

    public void clearTodos() {
        this.todos.clear();
    }

    public void run(ExecutorService executor) {
        List<String> resources = new ArrayList<String>();
        resources.addAll(this.todos);
        this.todos.clear();

        String configPath = configRootPath + "/" + config.getConfigPath();

        String indexEndpoint = lookupBaseURL + "/api/index/run";
        IndexingTask task = new IndexingTask(configPath, indexEndpoint, resources);

        if(executor != null) {
            this.indexingFuture = executor.submit(task);
            return;
        }

        task.run();
    }


    public boolean isBusy() {
        return this.indexingFuture != null && !this.indexingFuture.isDone();
    }
}
