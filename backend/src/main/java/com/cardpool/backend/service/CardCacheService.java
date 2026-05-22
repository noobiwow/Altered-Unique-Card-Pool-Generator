package com.cardpool.backend.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.cardpool.backend.model.Card;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import reactor.core.publisher.Flux;

@Service
public class CardCacheService {

    private static final Path UNIQUE_CACHE_DIRECTORY = Path.of("cache");
    private static final Path UNIQUE_CACHE_FILE = Path.of("cache/uniques-cards.smile");
    private static final ObjectMapper SMILE_MAPPER = new ObjectMapper(new SmileFactory());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = Logger.getLogger(CardCacheService.class.getName());

    public Flux<Card> getAllUniqueCards() {
        return loadUniqueFromDisk();
    }

    private Flux<Card> loadUniqueFromDisk() {
        return Flux.using(
                () -> Files.newDirectoryStream(
                        UNIQUE_CACHE_DIRECTORY,
                        "*.smile"),
                stream -> Flux.fromIterable(stream)
                        .concatMap(this::readSmileFile),
                t -> {
                    try {
                        t.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    private Flux<Card> readSmileFile(Path smileFile) {
        return Flux.create(sink -> {
            try (InputStream in = new BufferedInputStream(
                    Files.newInputStream(smileFile),
                    1024 * 1024);
                    MappingIterator<Card> iterator = SMILE_MAPPER
                            .readerFor(Card.class)
                            .readValues(in)) {
                while (iterator.hasNextValue()) {
                    sink.next(iterator.nextValue());
                }
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    public void loadFromUniquesFolder(Path uniquesFolderPath)
            throws IOException {

        List<Path> folders;
        try (Stream<Path> dirs = Files.list(uniquesFolderPath)) {
            folders = dirs
                    .filter(Files::isDirectory)
                    .toList();
        }
        Files.createDirectories(
                UNIQUE_CACHE_FILE.getParent());
        SmileFactory smileFactory = new SmileFactory();
        ObjectMapper mapper = new ObjectMapper(smileFactory);
        try (OutputStream out = new BufferedOutputStream(
                Files.newOutputStream(
                        UNIQUE_CACHE_FILE));
                JsonGenerator generator = mapper.getFactory()
                        .createGenerator(out)) {
            generator.writeStartArray();
            for (Path folder : folders) {
                Files.walkFileTree(folder,
                        new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(
                                    Path file,
                                    BasicFileAttributes attrs)
                                    throws IOException {
                                if (!attrs.isRegularFile()) {
                                    return FileVisitResult.CONTINUE;
                                }
                                if (!file.getFileName().toString().endsWith(".json")) {
                                    return FileVisitResult.CONTINUE;
                                }
                                try {
                                    Card card = parseUniqueFile(file);
                                    if (card != null) {
                                        mapper.writeValue(generator, card);
                                    }
                                } catch (Exception e) {
                                    log.warning("Failed " + file);
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
            }
            generator.writeEndArray();
        }
    }

    private Card parseUniqueFile(Path path) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(path.toFile());

            transformNameToLocalizedElement(root, "cardType");

            JsonNode subTypes = root.path("cardSubTypes");
            if (subTypes.isArray()) {
                for (JsonNode st : subTypes) {
                    if (st.isObject()) {
                        transformNameToLocalizedElement((ObjectNode) st, null);
                    }
                }
            }

            mapReferenceToCode(root, "mainFaction");
            mapReferenceToCode(root, "cardSet");

            JsonNode elements = root.path("elements");
            if (elements.isObject()) {
                root.put("mainEffect", elements.path("MAIN_EFFECT").asText(""));
                root.put("echoEffect", elements.path("ECHO_EFFECT").asText(""));
            }

            return objectMapper.treeToValue(root, Card.class);
        } catch (Exception e) {
            log.warning("Failed to parse unique card: " + path + " — " + e.getMessage());
            return null;
        }
    }

    private void transformNameToLocalizedElement(ObjectNode parent, String field) {
        JsonNode target = field != null ? parent.get(field) : parent;
        if (target == null || !target.isObject())
            return;

        ObjectNode targetObj = (ObjectNode) target;
        JsonNode translations = targetObj.path("translations");
        JsonNode flatName = targetObj.path("name");
        ObjectNode nameObj = objectMapper.createObjectNode();

        if (translations.isObject()) {
            translations.fieldNames().forEachRemaining(locale -> {
                String shortLocale = locale.substring(0, 2);
                nameObj.put(shortLocale, translations.path(locale).path("name").asText());
            });
        } else if (flatName.isTextual()) {
            String name = flatName.asText();
            nameObj.put("en", name);
            nameObj.put("fr", name);
            nameObj.put("de", name);
            nameObj.put("es", name);
            nameObj.put("it", name);
        }

        targetObj.set("name", nameObj);
    }

    private void mapReferenceToCode(ObjectNode parent, String field) {
        JsonNode child = parent.path(field);
        if (!child.isObject())
            return;
        ObjectNode obj = (ObjectNode) child;
        if (!obj.has("code") && obj.has("reference")) {
            obj.put("code", obj.get("reference").asText());
        }
    }
}
