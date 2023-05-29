package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteTable;

import java.util.Optional;

@Repository
public interface SiteRepository extends CrudRepository<SiteTable, Integer> {
    Optional<SiteTable> findByUrl(String url);

}
