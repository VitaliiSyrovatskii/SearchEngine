package searchengine.services;

import org.springframework.jdbc.core.JdbcTemplate;
import searchengine.dto.indexing.IndexingError;
import searchengine.dto.indexing.IndexingOk;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;

import java.io.IOException;
import java.util.Map;

final class AddLemmaAndIndex {

    static JdbcTemplate jdbcTemplate;

    static synchronized IndexingResponse addInTables(SiteTable site, Page page) {
        if (page.getCode() >= 400){
            IndexingOk indexingOk = new IndexingOk();
            return indexingOk;
        }
        Map<String, Integer> lemmas;
        try {
            lemmas = LemmaFinder.getInstance().collectLemmas(page.getContent());
        } catch (IOException e) {
            e.printStackTrace();
            IndexingError indexingError = new IndexingError();
            indexingError.setError("Ошибка получения лемм");
            return indexingError;
        }
        if (lemmas.isEmpty()) {
            IndexingOk indexingOk = new IndexingOk();
            return indexingOk;
        }
        StringBuilder insertLemma = new StringBuilder();
        StringBuilder selectLemma = new StringBuilder();
        int siteId = site.getId();
        int pageId = page.getId();
        for (String lemma : lemmas.keySet()){
            insertLemma.append((insertLemma.length() == 0 ? "" : ",") +
                    "(" + siteId + ", '" + lemma + "', 1)");
            selectLemma.append((selectLemma.length() == 0 ? "" : " OR ") +
                    "site_id=" + siteId + " AND lemma='" + lemma + "'");
        }
        String sqlInsertForLemma = "INSERT INTO lemma (site_id, lemma, frequency) " +
                "VALUES " + insertLemma +
                "ON DUPLICATE KEY UPDATE frequency=frequency+1";
        String sqlSelectForLemma = "SELECT id, lemma FROM lemma WHERE " + selectLemma;
        jdbcTemplate.execute(sqlInsertForLemma);
        StringBuilder insertIndex = new StringBuilder();
        jdbcTemplate.query(sqlSelectForLemma, ((rs, rowNum) -> {
            insertIndex.append((insertIndex.length() == 0 ? "" : ",") +
            "(" + pageId + ", " + rs.getInt("id")+ ", " + lemmas.get(rs.getString("lemma")) + ")");
            return null;
        }));
        String sqlInsertForIndex = "INSERT INTO `index` (page_id, lemma_id, `rank`) " +
                "VALUES " + insertIndex;
        jdbcTemplate.execute(sqlInsertForIndex);
        IndexingOk indexingOk = new IndexingOk();
        return indexingOk;
    }

}
