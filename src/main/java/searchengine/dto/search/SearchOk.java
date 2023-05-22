package searchengine.dto.search;

import lombok.Data;

import java.util.List;

@Data
public class SearchOk implements SearchResponse{
    private final boolean result = true;
    private int count;
    private List<SearchedPage> data;
}
