package searchengine.dto;

import lombok.Data;

import java.util.List;

@Data
public class SearchingResponse  extends Response{
    private int count;
    private List<SearchingData> data;
}
