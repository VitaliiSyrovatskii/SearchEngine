package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.config.Referrer;
import searchengine.config.UserAgent;
import searchengine.dto.indexing.IndexingError;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IndexingSinglePageServiceImpl implements IndexingSinglePageService {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UserAgent userAgent;
    private final Referrer referrer;

    @Override
    public IndexingResponse indexPage(String url) {
        SiteTable site = null;
        for (SiteTable siteTable : siteRepository.findAll()) {
            if (url.indexOf(siteTable.getUrl()) == 0) {
                site = siteTable;
                break;
            }
        }
        if (site == null) {
            IndexingError indexingError = new IndexingError();
            indexingError.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return indexingError;
        }
        Page page = getPage(url, site);
        if (page == null) {
            IndexingError indexingError = new IndexingError();
            indexingError.setError("Ошибка чтения страницы");
            return indexingError;
        }
        synchronized (pageRepository) {
            Optional<Page> optionalPage = pageRepository.findByPathAndSite(page.getPath(), site);
            if (optionalPage.isPresent()) {
                Iterable<Index> optionalIndexList = indexRepository.findByPage(optionalPage.get());
                HashSet<Lemma> lemmaList = new HashSet<>();
                for (Index index : optionalIndexList){
                    Lemma lemma = index.getLemma();
                    lemma.setFrequency(lemma.getFrequency() - 1);
                    lemmaList.add(lemma);
                }
                lemmaRepository.saveAll(lemmaList);
                pageRepository.deleteById(optionalPage.get().getId());
            }
            pageRepository.save(page);
        }
        AddLemmaAndIndex.jdbcTemplate = jdbcTemplate;
        return AddLemmaAndIndex.addInTables(site, page);
    }

    private Page getPage(String url, SiteTable site) {
        Page page = new Page();
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent.getUserAgent())
                    .referrer(referrer.getReferrer())
                    .ignoreHttpErrors(true)
                    .followRedirects(false)
                    .ignoreContentType(true)
                    .execute();
            if (!response.contentType().contains("text/html")) return null;
            Document doc = response.parse();
            page.setSite(site);
            String path = url.equals(site.getUrl()) ?
                    "/" : url.replaceFirst(site.getUrl(), "");
            page.setPath(path);
            page.setCode(response.statusCode());
            page.setContent(doc.html().replaceAll("'", ""));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return page;
    }
}
