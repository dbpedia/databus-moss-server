package org.dbpedia.moss.indexer;

import java.io.File;

public class IndexGroup {

    private String name;

    private File[] indexConfigurations;
    
    public IndexGroup(String name, File[] indexConfigurations) {
        this.indexConfigurations = indexConfigurations;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public File[] getIndexConfigurations() {
        return indexConfigurations;
    }
}
