package com.cardpool.backend.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.cardpool.backend.model.excel.CardEffectAnalyser;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a card Excel file (.xlsx) and returns a list of
 * {@link CardEffectAnalyser.CardRecord}.
 *
 * <p>
 * Expected columns (detected by header name, case-insensitive):
 * <ul>
 * <li>{@code Main Effect} — the raw effect text</li>
 * <li>{@code Set} — the set the card belongs to</li>
 * </ul>
 * All other columns are ignored.
 */
@Service
public class ExcelCardReaderService {

    private static final String COL_EFFECT = "main effect";
    private static final String COL_SET = "set";

    /**
     * Reads the first sheet of the given xlsx file.
     *
     * @param path path to the .xlsx file
     * @return list of card records; never null
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if required columns are missing
     */
    public List<CardEffectAnalyser.CardRecord> read(Path path) throws IOException {
        return read(new FileInputStream(path.toFile()));
    }

    public List<CardEffectAnalyser.CardRecord> read(InputStream inputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("The Excel file has no header row.");
            }

            int effectCol = -1;
            int setCol = -1;

            for (Cell cell : header) {
                String name = getCellString(cell).toLowerCase().strip();
                if (COL_EFFECT.equals(name))
                    effectCol = cell.getColumnIndex();
                if (COL_SET.equals(name))
                    setCol = cell.getColumnIndex();
            }

            if (effectCol == -1)
                throw new IllegalArgumentException(
                        "Required column 'Main Effect' not found in header row.");
            if (setCol == -1)
                throw new IllegalArgumentException(
                        "Required column 'Set' not found in header row.");

            List<CardEffectAnalyser.CardRecord> records = new ArrayList<>();
            final int effectColFinal = effectCol;
            final int setColFinal = setCol;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                String effect = getCellString(row.getCell(effectColFinal));
                String set = getCellString(row.getCell(setColFinal));

                if (effect.isBlank() && set.isBlank())
                    continue; // skip empty rows

                records.add(new CardEffectAnalyser.CardRecord() {
                    public String getMainEffect() {
                        return effect.isBlank() ? null : effect;
                    }

                    public String getSet() {
                        return set;
                    }
                });
            }

            return records;
        }
    }

    private static String getCellString(Cell cell) {
        if (cell == null)
            return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.STRING
                    ? cell.getStringCellValue()
                    : String.valueOf(cell.getNumericCellValue());
            default -> "";
        };
    }
}
