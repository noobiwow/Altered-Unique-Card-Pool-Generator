package com.cardpool.backend.model.externalApi;

import java.util.List;

import lombok.Getter;

@Getter
public class CardAPIOutput {
    private List<APICard> member;
    private int totalItems;
    private int currentPage;
    private int itemsPerPage;
    private int lastPage;
}