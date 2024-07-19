package searchengine.services;

import searchengine.dto.Response;

public interface SearchingService {
    Response search(String query, String site, int offset, int limit);
}
