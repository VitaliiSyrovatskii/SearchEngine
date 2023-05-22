package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

public interface IndexingSinglePageService {
    IndexingResponse indexPage(String url);
}
