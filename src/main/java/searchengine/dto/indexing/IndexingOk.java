package searchengine.dto.indexing;

import lombok.Data;

@Data
public class IndexingOk implements IndexingResponse{
    private final boolean result = true;
}
