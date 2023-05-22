package searchengine.dto.search;

import lombok.Data;

@Data
public class SearchError implements SearchResponse {
    private final boolean result = false;
    private String error;
}
