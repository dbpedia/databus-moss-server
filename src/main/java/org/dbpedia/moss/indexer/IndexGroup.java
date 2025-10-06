package org.dbpedia.moss.indexer;

public class IndexGroup {

    private final String name;

    private final String[] indexConfigurations;
    
    public IndexGroup(String name, String[] indexConfigurations) {
        this.indexConfigurations = indexConfigurations;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String[] getIndexConfigurations() {
        return indexConfigurations;
    }
}
