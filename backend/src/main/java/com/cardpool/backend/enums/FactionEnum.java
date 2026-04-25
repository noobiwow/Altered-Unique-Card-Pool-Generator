package com.cardpool.backend.enums;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;

@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum FactionEnum {
    AXIOM("1", "AX", "Axiom"),
    BRAVOS("2", "BR", "Bravos"),
    LYRA("3", "LY", "Lyra"),
    MUNA("4", "MU", "Muna"),
    ORDIS("5", "OR", "Ordis"),
    YZMIR("6", "YZ", "Yzmir");

    private String id;
    private String code;
    private String name;

    FactionEnum(String id, String code, String name) {
        this.id = id;
        this.name = name;
        this.code = code;
    }
}
