package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.Site;
import searchengine.dto.Response;
import searchengine.services.IndexingService;
import searchengine.services.SearchingService;
import searchengine.services.StatisticsService;

import java.util.logging.Logger;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    @Autowired
    private final StatisticsService statisticsService;
    @Autowired
    private final IndexingService indexingService;
    @Autowired
    private final SearchingService searchingService;

    @GetMapping("/statistics")
    public ResponseEntity<Response> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }


    @GetMapping("/startIndexing")
    public ResponseEntity<Response> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Response> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Response> startIndexingOne(Site site) {
        return ResponseEntity.ok(indexingService.indexPage(site.getUrl()));
    }

    @GetMapping("/search")
    public ResponseEntity<Response> startSearching(@RequestParam String query,
                                                 @RequestParam(required = false) String site,
                                                 @RequestParam int offset,
                                                 @RequestParam int limit) {
        return ResponseEntity.ok(searchingService.search(query, site, offset, limit));
    }
}
