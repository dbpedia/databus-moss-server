package org.dbpedia.moss.indexer;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.dbpedia.moss.GstoreConnector;
import org.dbpedia.moss.MossConfiguration;
import org.dbpedia.moss.utils.MossEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;


public class IndexerManager {

    final static Logger logger = LoggerFactory.getLogger(IndexerManager.class);

    // Alle indexer

    private List<LayerIndexer> indexers;
    //Ein mod kann in 1 oder mehreren Indexern vorkommen -> rebuild index for entsprechenden indexern für die der mod wichtig ist
    private HashMap<String, List<LayerIndexer>> indexerMappings;
    
    private ThreadPoolExecutor worker;

    private final int fixedPoolSize = 1;

    private ScheduledExecutorService scheduler;

    public IndexerManager(MossEnvironment environment) {
        GstoreConnector gstoreConnector = new GstoreConnector(environment.getGstoreBaseURL());
        
        File configFile = new File(environment.GetConfigPath());
        String configRootPath = configFile.getParent();

        MossConfiguration config = MossConfiguration.fromJson(configFile);

        this.indexers = new ArrayList<LayerIndexer>();
        this.indexerMappings = new HashMap<String, List<LayerIndexer>>();

        for(DataLoaderConfig loaderConfig : config.getLoaders()) {
            DataLoader loader = new DataLoader(loaderConfig, gstoreConnector, configRootPath, environment.GetLookupBaseURL());
                
            loader.load();
        }

        for(LayerIndexerConfiguration indexerConfig : config.getIndexers()) {

            LayerIndexer modIndexer = new LayerIndexer(indexerConfig, configRootPath, environment.GetLookupBaseURL());

            this.indexers.add(modIndexer);

            logger.info("Created indexer \"{}\" for layer(s) {}", modIndexer.getId(), modIndexer.getConfig().getLayers());
        }
        
        this.worker = new ThreadPoolExecutor(fixedPoolSize, fixedPoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

        for(LayerIndexer indexer : this.indexers) {
            for(String modType : indexer.getConfig().getLayers()) {
                if(!this.indexerMappings.containsKey(modType)) {
                    this.indexerMappings.put(modType, new ArrayList<LayerIndexer>());
                }

                this.indexerMappings.get(modType).add(indexer);
            }
        }

        // Schedule a task to run every second
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(() -> tick(), 0, 1, TimeUnit.SECONDS); 
        
        this.worker = new ThreadPoolExecutor(fixedPoolSize, fixedPoolSize,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());

    }

    private void tick() {
        rebuildIndices();
    }

    public void stop() {
        try {
            this.scheduler.shutdown();
        } catch (SecurityException e) {
            System.err.println(e);
        }
    }

    public HashMap<String,List<LayerIndexer>> getIndexerMappings() {
        return this.indexerMappings;
    }

    public void setIndexerMappings(HashMap<String,List<LayerIndexer>> indexerMappings) {
        this.indexerMappings = indexerMappings;
    }

    /**
     * Gehe über alle indexer mit todos und starte entsprechende index tasks
     */
    public void rebuildIndices() {

        for(LayerIndexer indexer : indexers) {
          
            if(indexer.getTodos().size() == 0) {
                continue;
            }

            // Hier haben wir etwas zu tun!
            // frage indexer, ob er gerade ein task bearbeitet
            if(indexer.isBusy()) {
                continue;
                
            }

            // Läuft grad nischt für indexer
            // frage prozessmanager, ob wir kapazitäten haben für einen neuen prozess
            if(worker.getActiveCount() >= fixedPoolSize) {
                continue;
            }

            indexer.run(worker);
        }
    }

    
    public void updateIndices(String contentUri, String layerName) {
        List<LayerIndexer> correspondingIndexers = indexerMappings.get(layerName);
        for (LayerIndexer indexer : correspondingIndexers) {
           
            indexer.addTodo(contentUri);
            logger.info("Indexer {} task list updated: {}", indexer.getId(), indexer.getTodos());
        }
    }
}
