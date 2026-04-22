package com.cardpool.backend.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.cardpool.backend.model.Card;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ExcelExportService {

    public static byte[] exportCards(List<Card> cards) throws IOException {

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Cards");

            // Header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Reference", "Name", "Rarity", "Faction", "Set", "Type",
                    "Sub Types", "Main Cost", "Recall Cost", "Main Effect", "Echo Effect", "Image Path"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // Data rows
            int rowIdx = 1;
            for (Card card : cards) {

                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue(card.getReference());
                row.createCell(1).setCellValue(card.getName());
                row.createCell(2).setCellValue(card.getRarityName());
                row.createCell(3).setCellValue(card.getFactionName());
                row.createCell(4).setCellValue(card.getCardSet().getName());
                row.createCell(5).setCellValue(card.getCardType().getName().getEn_label());

                // SubTypes (List -> CSV)
                row.createCell(6).setCellValue(
                        card.getCardSubTypes() != null
                                ? card.getCardSubTypes().stream()
                                        .map(localizedNameEntity -> localizedNameEntity.getName().getEn_label())
                                        .collect(Collectors.joining(", "))
                                : "");

                // Elements (Map -> key=value CSV)
                row.createCell(7).setCellValue((card.getElements().get("MAIN_COST")));

                row.createCell(8).setCellValue((card.getElements().get("RECALL_COST")));

                row.createCell(9).setCellValue(card.getMainEffect());
                row.createCell(10).setCellValue(card.getEchoEffect());
                row.createCell(11).setCellValue(card.getImagePath());
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
