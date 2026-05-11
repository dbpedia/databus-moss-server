package org.dbpedia.moss.utils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LookupServer implements Closeable {

    public static final String FIELD_ID = "id";
    public static final String FIELD_LABEL = "label";

    public static final String BINDING_KEY = "key";
    public static final String BINDING_VALUE = "value";

    private final Path indexPath;
    //private final StandardAnalyzer analyzer;

    private static final Logger logger = LoggerFactory.getLogger(LookupServer.class);

    public LookupServer(Path indexPath) throws IOException {
        this.indexPath = indexPath;
        // this.analyzer = new StandardAnalyzer();
        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath);
        }
    }

    public void index(Model model, String sparqlQuery) throws IOException {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(sparqlQuery, "sparqlQuery");

        Path tmpIndexPath = indexPath.getParent().resolve(indexPath.getFileName() + "_" + UUID.randomUUID());
        Files.createDirectories(tmpIndexPath);

        Map<String, List<String>> entries = new LinkedHashMap<>();

        try (QueryExecution qexec = QueryExecutionFactory.create(sparqlQuery, model)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                RDFNode keyNode = sol.get(BINDING_KEY);
                RDFNode valueNode = sol.get(BINDING_VALUE);

                if (keyNode == null || valueNode == null) {
                    continue;
                }

                String key = keyNode.isLiteral() ? keyNode.asLiteral().getString() : keyNode.toString();
                String label = valueNode.isLiteral() ? valueNode.asLiteral().getString() : valueNode.toString();

                entries.computeIfAbsent(key, k -> new ArrayList<>()).add(label);
            }
        }

        int count = 0;
        String firstBinding = null;
        String lastBinding = null;

        /*
        try (Directory tmpDir = FSDirectory.open(tmpIndexPath); IndexWriter writer = new IndexWriter(tmpDir, new IndexWriterConfig(analyzer))) {

            for (var entry : entries.entrySet()) {
                String key = entry.getKey();
                List<String> labels = entry.getValue();

                if (firstBinding == null) {
                    firstBinding = key + " -> " + String.join(", ", labels);
                }
                lastBinding = key + " -> " + String.join(", ", labels);

                Document doc = new Document();
                doc.add(new StringField(FIELD_ID, key, Field.Store.YES));
                for (String label : labels) {
                    doc.add(new TextField(FIELD_LABEL, label, Field.Store.YES));
                }

                writer.updateDocument(new Term(FIELD_ID, key), doc);
                count++;
            }

            writer.commit();
        } catch (Exception e) {
            FileUtils.deleteDirectory(tmpIndexPath.toFile());
            throw new IOException("Failed to build temporary index", e);
        }
         */
        Path backupIndex = indexPath.getParent().resolve(indexPath.getFileName() + "_backup");
        if (Files.exists(backupIndex)) {
            FileUtils.deleteDirectory(backupIndex.toFile());
        }

        if (Files.exists(indexPath)) {
            Files.move(indexPath, backupIndex, StandardCopyOption.ATOMIC_MOVE);
        }

        Files.move(tmpIndexPath, indexPath, StandardCopyOption.ATOMIC_MOVE);

        if (Files.exists(backupIndex)) {
            FileUtils.deleteDirectory(backupIndex.toFile());
        }

        logger.info("Indexed {} documents. First binding: {} Last binding: {}", count, firstBinding, lastBinding);
    }

    public List<SearchResult> search(String queryInput, int maxHits) throws IOException {

        List<SearchResult> results = new ArrayList<>();
        return results;

        /*
        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(indexPath))) {
            IndexSearcher searcher = new IndexSearcher(reader);

            List<String> tokens = tokenize(queryInput);
            BooleanQuery.Builder builder = new BooleanQuery.Builder();

            for (String token : tokens) {
                builder.add(new TermQuery(new Term(FIELD_LABEL, token)), BooleanClause.Occur.SHOULD);

                builder.add(new FuzzyQuery(
                        new Term(FIELD_LABEL, token),
                        2,
                        1
                ), BooleanClause.Occur.SHOULD);

                builder.add(new PrefixQuery(
                        new Term(FIELD_LABEL, token)
                ), BooleanClause.Occur.SHOULD);
            }

            Query combined = builder.build();
            TopDocs topDocs = searcher.search(combined, maxHits);

            List<SearchResult> results = new ArrayList<>();
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document d = searcher.storedFields().document(sd.doc);
                String[] labels = d.getValues(FIELD_LABEL);
                results.add(new SearchResult(
                        d.get(FIELD_ID),
                        labels,
                        sd.score
                ));
            }

            return results;
        }
         */
    }

        /*
    private List<String> tokenize(String text) throws IOException {
        List<String> tokens = new ArrayList<>();

        try (TokenStream stream = analyzer.tokenStream(FIELD_LABEL, new StringReader(text))) {
            CharTermAttribute attr = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                tokens.add(attr.toString());
            }
            stream.end();
        } 
        return tokens;
    }*/

    public record SearchResult(String id, String[] label, float score) {

    }

    @Override
    public void close() throws IOException {
        // No persistent resources
    }
}
