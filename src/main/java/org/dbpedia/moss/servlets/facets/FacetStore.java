package org.dbpedia.moss.servlets.facets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class FacetStore {

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public FacetStore(Path directory) {
        this.directory = directory;
    }

    public List<MossFacet> listFacets() throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }

        try (var stream = Files.list(directory)) {
            return stream
                    .filter(p -> p.toString().endsWith(".yml"))
                    .map(this::readFacetUnchecked)
                    .sorted((f1, f2) -> Integer.compare(f1.getSortOrder(), f2.getSortOrder()))
                    .toList();
        }
    }

    public Optional<MossFacet> loadFacet(String id) throws IOException {
        Path path = directory.resolve(id + ".yml");
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(mapper.readValue(path.toFile(), MossFacet.class));
    }

    public void saveFacet(MossFacet facet) throws IOException {
        Files.createDirectories(directory);
        mapper.writeValue(directory.resolve(facet.getId() + ".yml").toFile(), facet);
    }

    public boolean deleteFacet(String id) throws IOException {
        Path path = directory.resolve(id + ".yml");
        return Files.deleteIfExists(path);
    }

    private MossFacet readFacetUnchecked(Path path) {
        try {
            return mapper.readValue(path.toFile(), MossFacet.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
