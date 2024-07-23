package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.Error;
import searchengine.dto.Response;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.processors.SiteIndexator;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final Logger logger = Logger.getLogger(IndexingServiceImpl.class.getName());

    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;

    private final SitesList sites;
    private ForkJoinPool pool;

    ForkJoinPool getPoolInstance() {
        if (pool == null || pool.isShutdown()) {
            pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        }
        return pool;
    }

    private SiteIndexator InitSiteIndexator(String name, String url) {
        Site site = new Site();
        site.setName(name);
        site.setUrl(url);
        site.setStatus(Status.INDEXING);
        site.setLastError("");
        SiteIndexator siteIndexator = new SiteIndexator();
        siteIndexator.setSiteRepository(siteRepository);
        siteIndexator.setPageRepository(pageRepository);
        siteIndexator.setLemmaRepository(lemmaRepository);
        siteIndexator.setIndexRepository(indexRepository);
        siteIndexator.setSite(site);
        siteIndexator.setPages(new HashSet<>());
        siteIndexator.setLemmas(new HashMap<>());
        siteIndexator.setIndexes(new HashSet<>());
        return siteIndexator;
    }

    public Response startAll() {
        pool = getPoolInstance();

        if (pool.getActiveThreadCount() > 0) {
            return new Error("Индексация уже запущена");
        } else {
            sites.getSites().forEach(s -> {
                pool.execute(InitSiteIndexator(s.getName(), s.getUrl()));
            });
            return new Response();
        }
    }

    public Response stopAll() {
        pool = getPoolInstance();
        if (pool.getActiveThreadCount() > 0) {
            pool.shutdownNow();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Iterable<Site> siteIterable = siteRepository.findAll();
            for (Site site : siteIterable) {
                if (site.getStatus() != Status.INDEXED) {
                    site.setLastError("Индексация прервана пользователем");
                    site.setStatus(Status.FAILED);
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                }
            }
            return new Response();
        } else {
            return new Error("Индексация не запущена");
        }
    }

    public Response startPage(String url) {
        pool = getPoolInstance();
        if (pool.getActiveThreadCount() > 0) {
            return new Error("Индексация уже запущена");
        } else {
            if (sites.getSites().stream().noneMatch(s -> s.getUrl().equals(url))) {
                return new Error("Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле");
            }
            sites.getSites()
                    .stream()
                    .filter(s -> s.getUrl().equals(url))
                    .forEach(s -> {
                        pool.execute(InitSiteIndexator(s.getName(), s.getUrl()));
                    });
            return new Response();
        }
    }


      @Override
        public Response startIndexing() {
            logger.info("Запуск индексации" );
            return startAll();
        }

        @Override
        public Response stopIndexing(){
            logger.info("Остановка индексации ");
            return  stopAll();
        }

        @Override
        public Response indexPage(String url) {
            logger.info("Индексации страницы: "+url);
            return startPage(url);
        }
}
