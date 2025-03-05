package org.dbpedia.moss.indexer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.dbpedia.moss.config.MossIndexerConfiguration;
import org.dbpedia.moss.utils.ENV;


public class LayerIndexer {
    private String id;
    private HashSet<String> todos;
    // private ExecutorService worker;
    private MossIndexerConfiguration config;
    @SuppressWarnings("rawtypes")
    private Future indexingFuture;
    // private final int fixedPoolSize = 1;

    public LayerIndexer(MossIndexerConfiguration config) {
        this.config = config;
        this.todos = new HashSet<String>();
        this.id = UUID.randomUUID().toString();
        // this.worker = Executors.newFixedThreadPool(fixedPoolSize);
    }


    public String getId() {
        return id;
    }

    public MossIndexerConfiguration getConfig() {
        return this.config;
    }

    public void setConfig(MossIndexerConfiguration config) {
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

        String indexEndpoint = ENV.LOOKUP_BASE_URL + "/api/index/run";
        IndexingTask task = new IndexingTask(config.getConfigFile(), indexEndpoint, resources);

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
