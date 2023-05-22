package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.indexing.IndexingOk;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchError;
import searchengine.dto.search.SearchOk;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.IndexingSinglePageService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final IndexingSinglePageService indexingSinglePageService;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService,
                         IndexingService indexingService,
                         IndexingSinglePageService indexingSinglePageService,
                         SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.indexingSinglePageService = indexingSinglePageService;
        this.searchService = searchService;

    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing(){
        IndexingResponse response = indexingService.startIndexing();
        if (response instanceof IndexingOk) return ResponseEntity.ok(response);
        else return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing(){
        IndexingResponse response = indexingService.stopIndexing();
        if (response instanceof IndexingOk) return ResponseEntity.ok(response);
        else return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @RequestMapping(value = "/indexPage", method = RequestMethod.POST)
    public ResponseEntity<IndexingResponse> indexPage(String url){
        IndexingResponse response = indexingSinglePageService.indexPage(url);
        if (response instanceof IndexingOk) return ResponseEntity.ok(response);
        else return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(String query, String site, int offset, int limit){
        SearchResponse response = searchService.search(query, site, offset, limit);
        if (response instanceof SearchOk) return ResponseEntity.ok(response);
        else {
            SearchError searchError = (SearchError) response;
            if (searchError.getError().equals("Ошибка сервера")){
                return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
            } else {
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
        }
    }
}
