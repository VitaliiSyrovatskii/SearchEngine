package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.SiteTable;

import java.util.Optional;

@Repository
public interface PageRepository extends CrudRepository<Page, Integer> {

    Optional<Page> findByPathAndSite(String path, SiteTable site);

    int countAllBySite(SiteTable siteTable);
}
