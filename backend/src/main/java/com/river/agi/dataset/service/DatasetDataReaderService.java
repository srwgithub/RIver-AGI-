package com.river.agi.dataset.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.prediction.service.PredictionData;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Reads the complete uploaded dataset for deterministic MVP analysis. */
@Service
public class DatasetDataReaderService {
    private final LocalStorageService localStorageService;
    private final ObjectMapper objectMapper;
    private final DatasetMapper datasetMapper;

    public DatasetDataReaderService(LocalStorageService localStorageService, ObjectMapper objectMapper) {
        this.localStorageService = localStorageService;
        this.objectMapper = objectMapper;
        this.datasetMapper = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DatasetDataReaderService(LocalStorageService localStorageService, ObjectMapper objectMapper, DatasetMapper datasetMapper) {
        this.localStorageService = localStorageService;
        this.objectMapper = objectMapper;
        this.datasetMapper = datasetMapper;
    }

    public List<Map<String, String>> readRows(Dataset dataset) {
        try (InputStream input = localStorageService.getFileInputStream(fileName(dataset))) {
            return switch (dataset.getFileType().toLowerCase(Locale.ROOT)) {
                case "csv" -> readCsv(input);
                case "xlsx", "xls" -> readWorkbook(input, dataset.getFileType());
                case "json" -> readJson(input);
                default -> throw new BusinessException("Unsupported file type: " + dataset.getFileType());
            };
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Failed to read dataset: " + e.getMessage());
        }
    }

    private String fileName(Dataset dataset) {
        String url = dataset.getFileUrl();
        if (url == null || url.isBlank()) throw new BusinessException("Dataset file is missing");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private List<Map<String, String>> readCsv(InputStream input) throws Exception {
        try (com.opencsv.CSVReader reader = new com.opencsv.CSVReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<String[]> all = reader.readAll();
            if (all.isEmpty()) return List.of();
            return toRows(Arrays.asList(all.get(0)), all.subList(1, all.size()));
        }
    }

    private List<Map<String, String>> readWorkbook(InputStream input, String type) throws Exception {
        Workbook workbook = "xlsx".equalsIgnoreCase(type) ? new XSSFWorkbook(input) : new HSSFWorkbook(input);
        try {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) return List.of();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) headers.add(cellValue(header.getCell(i)));
            List<String[]> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String[] values = new String[headers.size()];
                for (int c = 0; c < headers.size(); c++) values[c] = cellValue(row.getCell(c));
                rows.add(values);
            }
            return toRows(headers, rows);
        } finally {
            workbook.close();
        }
    }

    private List<Map<String, String>> readJson(InputStream input) throws Exception {
        return objectMapper.readValue(input, new TypeReference<List<Map<String, String>>>() {});
    }

    private List<Map<String, String>> toRows(List<String> headers, List<String[]> rows) {
        List<Map<String, String>> result = new ArrayList<>();
        for (String[] values : rows) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) row.put(headers.get(i), i < values.length ? values[i] : "");
            result.add(row);
        }
        return result;
    }

    private String cellValue(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        return formatter.formatCellValue(cell).trim();
    }

    public List<PredictionData.SeriesPoint> loadSeriesData(Long datasetId, String timeField, String targetField) {
        if (datasetId == null) throw new BusinessException("datasetId 不能为空");
        if (datasetMapper == null) throw new BusinessException("DatasetMapper 未注入，无法加载数据集");
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("数据集不存在");
        List<Map<String, String>> rows = readRows(dataset);
        List<PredictionData.SeriesPoint> points = new ArrayList<>();
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        };
        for (Map<String, String> row : rows) {
            String timeStr = timeField != null ? row.get(timeField) : "";
            String targetStr = targetField != null ? row.get(targetField) : null;
            LocalDate date = LocalDate.now();
            try {
                if (timeStr != null && !timeStr.isBlank()) {
                    for (DateTimeFormatter fmt : formatters) {
                        try { date = LocalDate.parse(timeStr.trim(), fmt); break; } catch (Exception ignored) { }
                    }
                }
            } catch (Exception ignored) { }
            double value = 0.0;
            try {
                if (targetStr != null && !targetStr.isBlank()) value = Double.parseDouble(targetStr);
            } catch (NumberFormatException ignored) { }
            points.add(new PredictionData.SeriesPoint(date, value));
        }
        return points;
    }
}
