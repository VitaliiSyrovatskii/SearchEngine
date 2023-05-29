package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.config.Referrer;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.UserAgent;
import searchengine.dto.indexing.IndexingError;
import searchengine.dto.indexing.IndexingOk;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SitesList sites;
    private final UserAgent userAgent;
    private final Referrer referrer;

    private HashSet<SiteTable> siteInIndexing = new HashSet<>();

    @Override
    public IndexingResponse startIndexing() {
        for (SiteTable siteTable : siteRepository.findAll()){
            if (siteTable.getStatus().equals(StatusType.INDEXING)){
                IndexingError indexingError = new IndexingError();
                indexingError.setError("Индексация уже запущена");
                return indexingError;
            }
        }
        Parsing.siteRepository = siteRepository;
        Parsing.pageRepository = pageRepository;
        AddLemmaAndIndex.jdbcTemplate = jdbcTemplate;
        Parsing.userAgent = userAgent;
        Parsing.referrer = referrer;
        for (Site site : sites.getSites()) {
            Optional<SiteTable> optionalSiteTable = siteRepository.findByUrl(site.getUrl());
            if (optionalSiteTable.isPresent()){
                siteRepository.deleteById(optionalSiteTable.get().getId());
            }
            SiteTable siteTable = new SiteTable();
            siteTable.setUrl(site.getUrl());
            siteTable.setName(site.getName());
            siteTable.setStatus(StatusType.INDEXING);
            siteTable.setStatusTime(new Date());
            siteRepository.save(siteTable);
            startParsing(siteTable);
        }
        return new IndexingOk();
    }

    private void startParsing(SiteTable siteTable){
        Parsing parsing = new Parsing();
        parsing.setSite(siteTable);
        parsing.setUrl(siteTable.getUrl());
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        siteInIndexing.add(siteTable);
        new Thread(() -> {
            try {
                forkJoinPool.invoke(parsing);
            } catch (Exception e) {
                if (siteTable.getStatus().equals(StatusType.INDEXING)) {
                    siteTable.setLastError(e.getMessage());
                    siteTable.setStatus(StatusType.FAILED);
                }
            } finally {
                siteTable.setStatusTime(new Date());
                if (siteTable.getStatus().equals(StatusType.INDEXING)) {
                    siteTable.setStatus(StatusType.INDEXED);
                }
                siteRepository.save(siteTable);
            }
        }).start();
    }

    @Override
    public IndexingResponse stopIndexing() {
        boolean isIndexing = false;
        for (SiteTable siteTable : siteInIndexing){
            if (siteTable.getStatus().equals(StatusType.INDEXING)){
                siteTable.setLastError("Индексация остановлена пользователем");
                siteTable.setStatus(StatusType.FAILED);
                isIndexing = true;
            }
        }
        if (!isIndexing) {
            IndexingError indexingError = new IndexingError();
            indexingError.setError("Индексация не запущена");
            return indexingError;
        }
        Parsing.isStop = true;
        siteInIndexing.clear();
        return new IndexingOk();
    }
}
