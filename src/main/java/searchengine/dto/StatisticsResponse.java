package searchengine.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatisticsResponse extends Response {
    private StatisticsData statistics;
}
