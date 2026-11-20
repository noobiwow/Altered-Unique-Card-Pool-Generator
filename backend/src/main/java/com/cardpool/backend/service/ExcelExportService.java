package com.cardpool.backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import com.cardpool.backend.model.Card;

/** Exports a pool as an .xlsx workbook, one card per row. */
@Service
public class ExcelExportService {

    /** A column: header, width in characters, value of a card. */
    private record Column(String header, int width, Function<Card, String> value) {
    }

    private static final List<Column> COLUMNS = List.of(
            new Column("Reference", 30, Card::getReference),
            new Column("Name", 28, Card::getName),
            new Column("Rarity", 10, Card::getRarityName),
            new Column("Faction", 10, Card::getFactionName),
            new Column("Set", 24, Card::getSetName),
            new Column("Type", 14, c -> c.getCardType() == null || c.getCardType().getName() == null
                    ? null : c.getCardTypeName()),
            new Column("Sub Types", 20, ExcelExportService::subTypes),
            new Column("Main Cost", 10, c -> c.getElementValue("MAIN_COST")),
            new Column("Recall Cost", 10, c -> c.getElementValue("RECALL_COST")),
            new Column("Main Effect", 80, Card::getMainEffect),
            new Column("Echo Effect", 60, Card::getEchoEffect));

    public byte[] exportCards(List<Card> cards) throws IOException {
        // Streaming workbook: constant memory even for 30 000-card pools.
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Cards");
            writeHeader(workbook, sheet);
            int rowIndex = 1;
            for (Card card : cards) {
                if (card == null) {
                    continue;
                }
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < COLUMNS.size(); i++) {
                    String value = COLUMNS.get(i).value().apply(card);
                    row.createCell(i).setCellValue(value == null ? "" : value);
                }
            }
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        }
    }

    private static void writeHeader(Workbook workbook, Sheet sheet) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        Row header = sheet.createRow(0);
        for (int i = 0; i < COLUMNS.size(); i++) {
            header.createCell(i).setCellValue(COLUMNS.get(i).header());
            header.getCell(i).setCellStyle(style);
            sheet.setColumnWidth(i, COLUMNS.get(i).width() * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    private static String subTypes(Card card) {
        if (card.getCardSubTypes() == null) {
            return "";
        }
        return card.getCardSubTypes().stream()
                .filter(st -> st != null && st.getName() != null)
                .map(st -> st.getName().getEn_label())
                .collect(Collectors.joining(", "));
    }
}
