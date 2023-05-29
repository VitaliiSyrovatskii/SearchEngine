package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.SiteTable;

import java.util.Optional;

@Repository
public interface LemmaRepository extends CrudRepository<Lemma, Integer> {

    Optional<Lemma> findBySiteAndLemma(SiteTable site, String lemma);

    int countAllBySite(SiteTable siteTable);

}
