package com.river.agi.dataset.service;

import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.entity.DatasetField;
import com.river.agi.dataset.mapper.DatasetFieldMapper;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.common.BusinessException;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetParserService {
    
    private final DatasetMapper datasetMapper;
    private final DatasetFieldMapper datasetFieldMapper;
    private final LocalStorageService localStorageService;
    
    @Value("${app.storage.local-path:./uploads}")
    private String localPath;
    
    public void parseDataset(Long datasetId) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }
        
        // Set status to PARSING before starting
        String oldStatus = dataset.getStatus();
        dataset.setStatus("PARSING");
        datasetMapper.updateById(dataset);
        
        try {
            // Get file from local storage
            String fileName = dataset.getFileUrl().substring(dataset.getFileUrl().lastIndexOf("/") + 1);
            Path filePath = Paths.get(localPath, fileName);
            
            if (!Files.exists(filePath)) {
                throw new BusinessException("File not found: " + fileName);
            }
            
            InputStream inputStream = Files.newInputStream(filePath);
            
            List<String> headers;
            List<List<String>> rows;
            
            if ("xlsx".equalsIgnoreCase(dataset.getFileType()) || "xls".equalsIgnoreCase(dataset.getFileType())) {
                Workbook workbook = dataset.getFileType().equalsIgnoreCase("xlsx") 
                        ? new XSSFWorkbook(inputStream) 
                        : new HSSFWorkbook(inputStream);
                Sheet sheet = workbook.getSheetAt(0);
                headers = parseExcelHeaders(sheet);
                rows = parseExcelRows(sheet, headers.size());
                workbook.close();
            } else if ("csv".equalsIgnoreCase(dataset.getFileType())) {
                CSVReader reader = new CSVReader(new InputStreamReader(inputStream));
                List<String[]> allRows = reader.readAll();
                if (allRows.isEmpty()) throw new BusinessException("CSV file is empty");
                headers = Arrays.asList(allRows.get(0));
                rows = allRows.subList(1, allRows.size()).stream()
                        .map(Arrays::asList)
                        .toList();
                reader.close();
            } else if ("json".equalsIgnoreCase(dataset.getFileType())) {
                throw new BusinessException("JSON schema persistence is not yet supported by the upload parser");
            } else {
                throw new BusinessException("Unsupported file type: " + dataset.getFileType());
            }
            
            // Update dataset
            dataset.setRowCount(rows.size());
            dataset.setColumnCount(headers.size());
            dataset.setStatus("PARSED");
            
            // Build preview JSON
            int previewSize = Math.min(10, rows.size());
            List<Map<String, String>> previewData = new ArrayList<>();
            for (int i = 0; i < previewSize; i++) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    rowMap.put(headers.get(j), rows.get(i).get(j));
                }
                previewData.add(rowMap);
            }
            dataset.setPreviewJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(previewData));
            
            // Build schema JSON
            Map<String, String> schema = new LinkedHashMap<>();
            for (String header : headers) {
                schema.put(header, inferType(rows, headers.indexOf(header)));
            }
            dataset.setSchemaJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(schema));
            
            datasetMapper.updateById(dataset);
            
            // Save fields
            for (int i = 0; i < headers.size(); i++) {
                DatasetField field = new DatasetField();
                field.setDatasetId(datasetId);
                field.setFieldName(headers.get(i));
                field.setFieldType(inferType(rows, i));
                field.setPosition(i);
                field.setNullCount(countNulls(rows, i));
                field.setDistinctCount(countDistinct(rows, i));
                field.setSampleValues(getSampleValues(rows, i));
                datasetFieldMapper.insert(field);
            }
            
            log.info("Dataset {} parsed successfully with {} rows and {} columns", datasetId, rows.size(), headers.size());
            
        } catch (Exception e) {
            log.error("Failed to parse dataset {}", datasetId, e);
            dataset.setStatus("PARSE_FAILED");
            datasetMapper.updateById(dataset);
            throw new BusinessException("Failed to parse dataset: " + e.getMessage());
        }
    }
    
    private List<String> parseExcelHeaders(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                headers.add(getCellValueAsString(cell));
            }
        }
        return headers;
    }
    
    private List<List<String>> parseExcelRows(Sheet sheet, int columnCount) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                List<String> rowValues = new ArrayList<>();
                for (int j = 0; j < columnCount; j++) {
                    Cell cell = row.getCell(j);
                    rowValues.add(getCellValueAsString(cell));
                }
                rows.add(rowValues);
            }
        }
        return rows;
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
    
    private String inferType(List<List<String>> rows, int columnIndex) {
        int numericCount = 0;
        int dateCount = 0;
        int totalNonEmpty = 0;
        
        for (List<String> row : rows) {
            String value = row.get(columnIndex);
            if (!value.isEmpty()) {
                totalNonEmpty++;
                if (isNumeric(value)) numericCount++;
                if (isDate(value)) dateCount++;
            }
        }
        
        if (totalNonEmpty == 0) return "STRING";
        if ((double) numericCount / totalNonEmpty > 0.9) return "NUMERIC";
        if ((double) dateCount / totalNonEmpty > 0.5) return "DATE";
        return "STRING";
    }
    
    private boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean isDate(String value) {
        return value.matches("\\d{4}[-/]\\d{2}[-/]\\d{2}") || 
               value.matches("\\d{2}[-/]\\d{2}[-/]\\d{4}");
    }
    
    private int countNulls(List<List<String>> rows, int columnIndex) {
        int count = 0;
        for (List<String> row : rows) {
            String value = row.get(columnIndex);
            if (value == null || value.isEmpty()) {
                count++;
            }
        }
        return count;
    }
    
    private int countDistinct(List<List<String>> rows, int columnIndex) {
        Set<String> distinctValues = new HashSet<>();
        for (List<String> row : rows) {
            distinctValues.add(row.get(columnIndex));
        }
        return distinctValues.size();
    }
    
    private String getSampleValues(List<List<String>> rows, int columnIndex) {
        Set<String> samples = new LinkedHashSet<>();
        for (List<String> row : rows) {
            String value = row.get(columnIndex);
            if (!value.isEmpty()) {
                samples.add(value);
                if (samples.size() >= 5) break;
            }
        }
        return String.join(", ", samples);
    }
}
