package searchengine.services;

import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.config.Referrer;
import searchengine.config.UserAgent;
import searchengine.model.*;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.Semaphore;

@Setter
@NoArgsConstructor
final class Parsing extends RecursiveAction {

    private static final Semaphore SEMAPHORE = new Semaphore(1, true);
    static SiteRepository siteRepository;
    static PageRepository pageRepository;
    static UserAgent userAgent;
    static Referrer referrer;
    static boolean isStop = false;

    private SiteTable site;
    private String url;

    @Override
    protected void compute() {
        synchronized (site) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        HashSet<String> listUrl = new HashSet<>();
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent.getUserAgent())
                    .referrer(referrer.getReferrer())
                    .ignoreHttpErrors(true)
                    .followRedirects(false)
                    .ignoreContentType(true)
                    .execute();
            if (!response.contentType().contains("text/html")) return;
            Document doc = response.parse();
            Page newPage = new Page();
            newPage.setSite(site);
            newPage.setCode(response.statusCode());
            String path = url.equals(site.getUrl()) ?
                    "/" : url.replaceFirst(site.getUrl(), "");
            if (path.matches("/[/\\\\]+.*")) {
                return;
            }
            newPage.setPath(path);
            newPage.setContent(doc.html().replaceAll("'", ""));
            Elements elements = doc.select("a");
            for (String element : elements.eachAttr("abs:href")) {
                listUrl.add(element);
            }
            SEMAPHORE.acquire();
            synchronized (pageRepository) {
                if (pageRepository.findByPathAndSite(path, site).isPresent()) {
                    SEMAPHORE.release();
                    return;
                }
                pageRepository.save(newPage);
            }
            SiteTable siteTable = siteRepository.findById(site.getId()).get();
            siteTable.setStatusTime(new Date());
            siteRepository.save(siteTable);
            AddLemmaAndIndex.addInTables(site, newPage);
            SEMAPHORE.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (isStop) {
            return;
        }
        ArrayList<Parsing> taskList = new ArrayList<>();
        for (String newUrl : listUrl) {
            if (newUrl.indexOf(site.getUrl()) != 0) continue;
            Parsing task = new Parsing();
            task.setSite(site);
            task.setUrl(newUrl);
            task.fork();
            taskList.add(task);
        }
        for (Parsing task : taskList) {
            task.join();
        }
    }
}
