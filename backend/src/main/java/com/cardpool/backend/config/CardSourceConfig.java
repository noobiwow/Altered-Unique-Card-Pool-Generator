package com.cardpool.backend.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cardpool.backend.source.CardSource;
import com.cardpool.backend.source.csv.CsvCardSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Cards come from the CardsData CSV files in {@code cardpool.cardsdata-dir}
 * ({@code backend/cardsdata/}, filled by {@code update-cardsdata.sh}).
 */
@Slf4j
@Configuration
public class CardSourceConfig {

    @Bean
    public CardSource cardSource(@Value("${cardpool.cardsdata-dir:cardsdata}") String cardsDataDir) {
        log.info("Card source: CardsData CSV in {}", Path.of(cardsDataDir).toAbsolutePath().normalize());
        return new CsvCardSource(Path.of(cardsDataDir));
    }
}
