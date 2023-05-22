package searchengine.dto.indexing;

import lombok.Data;

@Data
public class IndexingError implements IndexingResponse{
    private final boolean result = false;
    private String error;
}
