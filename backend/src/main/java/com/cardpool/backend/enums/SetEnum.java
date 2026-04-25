package com.cardpool.backend.enums;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;

//TODO Add localized names

@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum SetEnum {
    CORE("CORE", "Beyond the gates"),
    COREKS("COREKS", "Beyond the gates KS"),
    ALIZE("ALIZE", "Trial By Frost"),
    BISE("BISE", "Whispers from the maze"),
    CYCLONE("CYCLONE", "Skybound Odyssey"),
    EOLE("EOLE", "Roots of Corruption"),
    DUSTER("DUSTER", "Seeds of Unity"),;

    private String reference;
    private String name;

    SetEnum(String reference, String name) {
        this.name = name;
        this.reference = reference;
    }
}
