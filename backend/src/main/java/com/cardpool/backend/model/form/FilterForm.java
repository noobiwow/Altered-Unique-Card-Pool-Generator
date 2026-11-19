package com.cardpool.backend.model.form;

import java.util.List;
import java.util.Map;

import lombok.Getter;

@Getter
public class FilterForm {
    private String faction;
    private String set;
    private List<String> subTypes;
    private String type;
    private boolean checkExcludeBanned;
    private boolean checkExcludeSuspended;
    /** Cost expressions such as "4+" (4 or more), "2-" (2 or less) or "3". */
    private String handCost;
    private String reserveCost;
    /** Legacy min/max hand-cost fields kept for backwards compatibility. */
    private Integer minCost;
    private Integer maxCost;
    private String fieldSearch;
    private String numberOfCards;
    private Map<String, Double> setWeights;
}
