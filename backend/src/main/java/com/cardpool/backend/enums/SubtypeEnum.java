package com.cardpool.backend.enums;

import lombok.Getter;

@Getter
public enum SubtypeEnum {
    DIVINITY("divinity"),
    ADVENTURER("adventurer"),
    PILOT("pilot"),
    ENGINEER("engineer"),
    ROBOT("robot"),
    ELEMENTAL("elemental"),
    DISRUPTION("disruption"),
    TITAN("titan"),
    CAT("cat"),
    TRAINER("trainer"),
    BLADEMASTER("blade master"),
    SUPPORT("support"),
    SQUIRREL("squirrel"),
    DEMON("demon"),
    ARTIST("artist"),
    CITIZEN("citizen"),
    DRUID("druid"),
    PLANT("plant"),
    SPIRIT("spirit"),
    SOLDIER("soldier"),
    BUREAUCRAT("bureaucrat"),
    MAGE("mage"),
    NOBLE("noble"),
    SONG("song"),
    LEVIATHAN("leviathan"),
    DRAGON("dragon"),
    DEITY("deity"),
    MESSENGER("messenger"),
    CONJURATION("conjuration"),
    BOON("boon"),
    FAIRY("fairy"),
    APPRENTICE("apprentice"),
    MANEUVER("maneuver"),
    SCHOLAR("scholar"),
    ANIMAL("animal"),
    GEAR("gear"),
    EXPEDITION("expedition"),
    RUCTION("ruction"),
    SITE("site"),
    ILLUSION("illusion"),
    ORE("ore"),
    SCIENTIST("scientist"),
    SAP("sap"),
    MERCHANT("merchant"),
    ROGUE("rogue"),
    FEAT("feat"),
    CORRUPTION("corruption");

    private String name;

    SubtypeEnum(String name) {
        this.name = name;
    }
}
