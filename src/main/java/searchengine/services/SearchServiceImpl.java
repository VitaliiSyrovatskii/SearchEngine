package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchError;
import searchengine.dto.search.SearchOk;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchedPage;
import searchengine.model.SiteRepository;
import searchengine.model.SiteTable;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public SearchResponse search(String query, String url, int offset, int limit) {
        if (query.isBlank()) {
            SearchError searchError = new SearchError();
            searchError.setError("Задан пустой поисковый запрос");
            return searchError;
        }
        List<SiteTable> sites = new ArrayList<>();
        if (url == null) {
            siteRepository.findAll().forEach(s -> sites.add(s));
        } else {
            sites.add(siteRepository.findByUrl(url).get());
        }
        if (sites.isEmpty()) {
            SearchOk searchOk = new SearchOk();
            searchOk.setCount(0);
            searchOk.setData(new ArrayList<>());
            return searchOk;
        }
        ArrayList<SearchedPage> tempData = new ArrayList<>();
        float maxAbsRel = Float.MIN_VALUE;
        for (SiteTable site : sites) {
            List<SearchedPage> searchedPages = searchOnSite(query, site);
            if (searchedPages == null) {
                SearchError searchError = new SearchError();
                searchError.setError("Ошибка сервера");
                return searchError;
            }
            if (searchedPages.isEmpty()) {
                continue;
            }
            tempData.addAll(searchedPages);
            for (SearchedPage searchedPage : searchedPages) {
                if (maxAbsRel < searchedPage.getRelevance()) {
                    maxAbsRel = searchedPage.getRelevance();
                }
            }
        }
        for (SearchedPage searchedPage : tempData) {
            searchedPage.setRelevance(searchedPage.getRelevance() / maxAbsRel);
        }
        tempData.sort(new Comparator<SearchedPage>() {
            @Override
            public int compare(SearchedPage o1, SearchedPage o2) {
                return -Float.compare(o1.getRelevance(), o2.getRelevance());
            }
        });
        ArrayList<SearchedPage> data = new ArrayList<>();
        SearchOk searchOk = new SearchOk();
        searchOk.setCount(tempData.size());
        if (offset >= tempData.size()) {
            searchOk.setData(data);
            return searchOk;
        }
        if (offset + limit > tempData.size()) limit = tempData.size() - offset;
        for (int i = 0; i < tempData.size(); i++) {
            if (i >= offset && i < offset + limit) {
                data.add(tempData.get(i));
            }
        }
        searchOk.setData(data);
        return searchOk;
    }

    private List<SearchedPage> searchOnSite(String query, SiteTable site) {
        Map<String, Integer> queryLemmas;
        try {
            queryLemmas = LemmaFinder.getInstance().collectLemmas(query);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        if (queryLemmas.isEmpty()) return new ArrayList<>();
        StringBuilder selectLemma = new StringBuilder();
        for (String lemma : queryLemmas.keySet()) {
            selectLemma.append((selectLemma.isEmpty() ? "" : " OR ") + "lemma='" + lemma + "'");
        }
        List<SearchedPage> searchedPages = new ArrayList<>();
        jdbcTemplate.query("SELECT count(*) quantity, SUM(ind.rank) abs_rel, path, content FROM lemma " +
                        "JOIN `index` ind on lemma.id=ind.lemma_id " +
                        "JOIN page on ind.page_id=page.id and page.site_id=" + site.getId() +
                        " WHERE " + selectLemma +
                        " GROUP BY `path`",
                ((rs, rowNum) -> {
                    if (rs.getInt("quantity") == queryLemmas.size()) {
                        SearchedPage searchedPage = new SearchedPage();
                        searchedPage.setSite(site.getUrl());
                        searchedPage.setSiteName(site.getName());
                        searchedPage.setUri(rs.getString("path"));
                        searchedPage.setRelevance(rs.getFloat("abs_rel"));
                        String content = rs.getString("content");
                        setTitleAndSnippet(searchedPage, queryLemmas.keySet(), content);
                        searchedPages.add(searchedPage);
                    }
                    return null;
                }));
        return searchedPages;
    }

    private void setTitleAndSnippet(SearchedPage searchedPage, Set<String> lemmas, String content) {
        ArrayList<String> sortLemmas = new ArrayList<>(lemmas);
        Document doc = Jsoup.parse(content);
        searchedPage.setTitle(doc.title());
        String text = doc.body().text();
        HashSet<Integer> indexes = new HashSet<>();
        for (String lemma : sortLemmas) {
            String regexLemma = "[^а-яА-Я]?" + lemma.substring(0, lemma.length() - 1) + "[а-я]*[^а-яА-Я]?";
            Pattern pattern = Pattern.compile(regexLemma);
            Matcher matcher = pattern.matcher(text.toLowerCase(Locale.ROOT));
            while (matcher.find()) {
                if (matcher.start() == 0) indexes.add(matcher.start());
                else indexes.add(matcher.start() + 1);
                if (matcher.end() == text.length()) indexes.add(matcher.end());
                else indexes.add(matcher.end() - 1);
            }
        }
        ArrayList<Integer> countInsert = new ArrayList<>();
        for (int i = 0; i < text.length() - 300; i++) {
            int count = 0;
            for (Integer j : indexes) {
                if (j - i <= 300 && j - i >= 0) {
                    count++;
                }
            }
            countInsert.add(count);
        }
        int indexMaxCount = 0;
        int maxCount = Integer.MIN_VALUE;
        for (int i = 0; i < countInsert.size(); i++) {
            if (countInsert.get(i) >= maxCount) {
                maxCount = countInsert.get(i);
                indexMaxCount = i;
            }
        }
        String snippet = text.substring(indexMaxCount, indexMaxCount + 300);
        ArrayList<String> replace = new ArrayList<>();
        for (String lemma : sortLemmas) {
            String regexLemma = "[^а-яА-Я]?" + lemma.substring(0, lemma.length() - 1) + "[а-я]*[^а-яА-Я]?";
            Pattern pattern = Pattern.compile(regexLemma);
            Matcher matcher = pattern.matcher(snippet.toLowerCase(Locale.ROOT));
            while (matcher.find()) {
                int start;
                if (matcher.start() == 0) start = matcher.start();
                else start = matcher.start() + 1;
                int finish;
                if (matcher.end() == snippet.length()) finish = matcher.end();
                else finish = matcher.end() - 1;
                replace.add(snippet.substring(start, finish));
            }
        }
        for (String word : replace) {
            snippet = snippet.replaceAll(word, "<b>" + word + "</b>");
        }
        searchedPage.setSnippet(snippet);
    }
}
