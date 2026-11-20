package com.cardpool.backend.source;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.cardpool.backend.dto.Catalog;
import com.cardpool.backend.dto.EffectFragment;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;

/** Where unique cards come from: the CardsData CSV files (see CsvCardSource). */
public interface CardSource {

    /** Short name for logs and diagnostics. */
    String name();

    /**
     * Calls {@code sink} for every card matching {@code filter}, optionally
     * restricted to one set (null = all sets). Matching must agree with
     * {@link CardFilter#test(Card)}.
     */
    void scan(CardFilter filter, String setCode, Consumer<Candidate> sink);

    /**
     * Sets and sub types available in this source, for the generator form.
     * Default set weights are filled in by the caller.
     */
    Catalog catalog();

    /** The cards with the given references (unknown references are skipped). */
    List<Card> findByReferences(Collection<String> references);

    /**
     * Effect fragments used by the cards, for ability rules matching fragment
     * ids. Empty for sources without fragment ids.
     */
    default List<EffectFragment> fragments() {
        return List.of();
    }
}
