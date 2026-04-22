package com.cardpool.backend.model.form;

import lombok.Getter;

@Getter
public class FilterForm {
    private String faction;
    private String set;
    private String subType;
    private String type;
    private boolean checkExcludeBanned;
    private boolean checkExcludeSuspended;
    private Integer minCost;
    private Integer maxCost;
    private String fieldSearch;
    private String numberOfCards;
}
