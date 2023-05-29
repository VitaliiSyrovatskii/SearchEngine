package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Page;

@Repository
public interface IndexRepository extends CrudRepository<Index, Integer> {

    Iterable<Index> findByPage(Page page);
}
