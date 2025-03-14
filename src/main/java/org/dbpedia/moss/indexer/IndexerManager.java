package org.dbpedia.moss.indexer;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.naming.ConfigurationException;

import org.dbpedia.moss.GstoreConnector;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossDataLoaderConfig;
import org.dbpedia.moss.config.MossIndexerConfiguration;
import org.dbpedia.moss.utils.ENV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;


public class IndexerManager {

    final static Logger logger = LoggerFactory.getLogger(IndexerManager.class);

    // Alle indexer

    private List<LayerIndexer> indexers;
    //Ein mod kann in 1 oder mehreren Indexern vorkommen -> rebuild index for entsprechenden indexern für die der mod wichtig ist
    private HashMap<String, LayerIndexer> indexerMap;
    
    private ThreadPoolExecutor worker;

    private final int fixedPoolSize = 1;

    private ScheduledExecutorService scheduler;

    public IndexerManager() throws ConfigurationException {

        GstoreConnector gstoreConnector = new GstoreConnector(ENV.GSTORE_BASE_URL);
        MossConfiguration config = MossConfiguration.get();

        this.indexers = new ArrayList<LayerIndexer>();
        this.indexerMap = new HashMap<String, LayerIndexer>();

        for(MossDataLoaderConfig loaderConfig : config.getLoaders()) {
            DataLoader loader = new DataLoader(loaderConfig, gstoreConnector);
                
            loader.load();
        }

        for(MossIndexerConfiguration indexerConfig : config.getIndexers()) {

            LayerIndexer modIndexer = new LayerIndexer(indexerConfig);
            this.indexers.add(modIndexer);

            logger.info("Created indexer \"{}\" for layer(s) {}", modIndexer.getId(), modIndexer.getConfig().getLayers());
        }
        
        this.worker = new ThreadPoolExecutor(fixedPoolSize, fixedPoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

        for(LayerIndexer indexer : this.indexers) {

            indexerMap.put(indexer.getConfig().getId(), indexer);

            /* 
            for(String modType : indexer.getConfig().getLayers()) {
                if(!this.indexerMap.containsKey(modType)) {
                    this.indexerMap.put(modType, new ArrayList<LayerIndexer>());
                }

                this.indexerMap.get(modType).add(indexer);
            }*/
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

    /*
    public HashMap<String,List<LayerIndexer>> getIndexerMap() {
        return this.indexerMap;
    }

    public void setIndexerMappings(HashMap<String,List<LayerIndexer>> indexerMappings) {
        this.indexerMap = indexerMappings;
    } */

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

    
    public void updateIndices(String contentUri, String layerId) {

        MossConfiguration mossConfiguration = MossConfiguration.get();

        for(MossIndexerConfiguration indexer : mossConfiguration.getIndexers()) {
           
            if(indexer.hasLayer(layerId)) {

                LayerIndexer layerIndexer = indexerMap.get(indexer.getId());
                layerIndexer.addTodo(contentUri);
                logger.info("Indexer {} task list updated: {}", layerIndexer.getId(), layerIndexer.getTodos());

            }

        }
    }
}
