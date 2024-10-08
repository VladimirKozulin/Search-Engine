package searchengine.processors;

import lombok.Getter;
import lombok.Setter;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveAction;
import java.util.logging.Logger;


public class SiteIndexator extends RecursiveAction {
    @Getter
    @Setter
    private Site site;

    @Getter
    @Setter
    private Page page;

    @Getter
    @Setter
    private String pageUrl;

    @Getter
    @Setter
    private Set<Page> pages;

    @Getter
    @Setter
    private HashMap<String, Lemma> lemmas;

    @Getter
    @Setter
    private Set<Index> indexes;

    @Setter
    private SiteRepository siteRepository;

    @Setter
    private PageRepository pageRepository;

    @Setter
    private LemmaRepository lemmaRepository;

    @Setter
    private IndexRepository indexRepository;

    private Logger logger = Logger.getLogger(SiteIndexator.class.getName());

    private void saveSite() {
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    private Document getHtml(String url) {
        Document htmlDoc = null;
        String errMessage = "";
        page = new Page();

        page.setPath(url.replaceAll("http(s)?://(www\\.)?[^/]*/?", "/"));
        page.setSite(site);
        synchronized (pages) {
            if (!pages.add(page)) {
                return htmlDoc;
            }
        }

        try {
            Thread.sleep(100);
            Connection.Response response = Jsoup.connect(url).execute();
            page.setCode(response.statusCode());
            if (response.statusCode() == 200) {
                htmlDoc = Jsoup.connect(url).get();
                page.setContent(htmlDoc.toString());
            }
        } catch (UnsupportedMimeTypeException e) {
            errMessage = url + " - не является страницей";
        } catch (SocketTimeoutException e) {
            errMessage = url + " - время ожидания истекло";
        } catch (IllegalArgumentException e) {
            errMessage = url + " - неверная ссылка";
        } catch (HttpStatusException e) {
            errMessage = url + e.getMessage();
            page.setCode(e.getStatusCode());
        } catch (
                UnknownHostException e) {
            errMessage = url + " - не удается получить доступ к сайту";
        } catch (IOException e) {
            errMessage = url + e.getMessage();
        } catch (InterruptedException e){
            errMessage = url + " - прерывание пользователя";
        }

        if (pageUrl == null && !errMessage.equals("")) {
            site.setLastError(errMessage);
            site.setStatus(Status.FAILED);
            logger.info(errMessage);
        }
        return htmlDoc;
    }

    private void lemmatization(String htmlText) {
        String pageText = Jsoup.parse(htmlText).text();
        pageText = Jsoup.clean(pageText, Safelist.none());
        try {
            LemmaFinder lemmaFinder = LemmaFinder.getInstance();
            Map<String, Integer> lemmaList = lemmaFinder.collectLemmas(pageText);
            lemmaList.keySet().forEach(l -> {
                Lemma lemma;
                synchronized (lemmas) {
                    if (lemmas.containsKey(l)) {
                        lemma = lemmas.get(l);
                        lemma.IncreaseFrequency();
                    } else {
                        lemma = new Lemma();
                        lemma.setSite(site);
                        lemma.setLemma(l);
                        lemma.setFrequency(1);
                        lemmas.put(l, lemma);
                    }
                }
                synchronized (indexes) {
                    Index index = new Index();
                    index.setPage(page);
                    index.setLemma(lemma);
                    index.setRank(lemmaList.get(l));
                    indexes.add(index);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processPage(String url) throws InterruptedException {
        List<SiteIndexator> pageIndexators = new ArrayList<>();
        Document htmlDoc = getHtml(url);
        if (htmlDoc == null) {
            return;
        }
        lemmatization(htmlDoc.toString());
        Elements links = htmlDoc.select("a[abs:href~=^" + htmlDoc.location() + "((?!(#|\\?)).)*$]");
        logger.info(url + " - найдено ссылок: " + links.size());

        links.stream().map(l -> l.absUrl("href"))
                .filter(l -> !htmlDoc.baseUri().equals(l))
                .distinct()
                .forEach(l -> {
                    SiteIndexator indexator = new SiteIndexator();
                    setSiteRepository(siteRepository);
                    setPageRepository(pageRepository);
                    setLemmaRepository(lemmaRepository);
                    setIndexRepository(indexRepository);
                    indexator.setSite(site);
                    indexator.setPageUrl(l);
                    indexator.setPages(pages);
                    indexator.setLemmas(lemmas);
                    indexator.setIndexes(indexes);
                    pageIndexators.add(indexator);
                    indexator.fork();
                });
        logger.info(url + " - потоков запущено " + (long) pageIndexators.size());
        pageIndexators.forEach(SiteIndexator::join);
    }


    protected void compute() {
        try {
            if (pageUrl == null) { // Проверка, является ли pageUrl null
                logger.info(site.getUrl() + " - запуск индексации");
                Site existSite = siteRepository.findFirstSiteByUrl(site.getUrl());
                if (existSite != null) { // Если сайт существует
                    site = existSite;
                    site.setStatus(Status.INDEXING);
                    site.setLastError("");
                    saveSite();
                    logger.info(site.getUrl() + " - удаление данных");
                    indexRepository.deleteIndexesBySiteId(site.getId());
                    pageRepository.deletePagesBySiteId(site.getId());
                    lemmaRepository.deleteLemmasBySiteId(site.getId());
                }else {
                    saveSite();
                }
                logger.info(site.getUrl() + " - обработка " +site.getStatus());
                processPage(site.getUrl());
                pageRepository.saveAll(pages.stream().toList());
                lemmaRepository.saveAll(lemmas.values());
                indexRepository.saveAll(indexes.stream().toList());

                if (site.getStatus()==Status.INDEXING){
                    site.setStatus(Status.INDEXED);
                }
                saveSite();
                logger.info(site.getUrl() + " - завершение обработки " +site.getStatus());
            } else {
                processPage(pageUrl);
            }
        } catch (InterruptedException | CancellationException e) {
            String errMessage = site.getUrl() + " - индексация прервана пользователем";
            site.setStatus(Status.FAILED);
            site.setLastError(errMessage);
            logger.info(errMessage);
        }
    }
}