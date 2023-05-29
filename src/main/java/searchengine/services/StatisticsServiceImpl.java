package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.model.SiteTable;

import java.util.*;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for(int i = 0; i < sitesList.size(); i++) {
            Site site = sitesList.get(i);
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            Optional<SiteTable> optionalSiteTable = siteRepository.findByUrl(site.getUrl());
            if (optionalSiteTable.isPresent()){
                SiteTable siteTable = optionalSiteTable.get();
                item.setPages(pageRepository.countAllBySite(siteTable));
                item.setLemmas(lemmaRepository.countAllBySite(siteTable));
                item.setStatus(siteTable.getStatus().name());
                item.setStatusTime(siteTable.getStatusTime().getTime());
                item.setError(siteTable.getLastError());
            } else {
                item.setPages(0);
                item.setLemmas(0);
                item.setStatus("");
                item.setStatusTime(new Date().getTime());
            }
            total.setPages(total.getPages() + item.getPages());
            total.setLemmas(total.getLemmas() + item.getLemmas());
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
