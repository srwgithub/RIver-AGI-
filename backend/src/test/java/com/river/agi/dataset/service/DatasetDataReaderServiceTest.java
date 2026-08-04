package com.river.agi.dataset.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.prediction.service.PredictionData;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("数据集数据读取服务测试")
class DatasetDataReaderServiceTest {

    @Mock
    private LocalStorageService localStorageService;
    @Mock
    private DatasetMapper datasetMapper;

    @TempDir
    Path tempDir;

    private DatasetDataReaderService service;

    @BeforeEach
    void setUp() {
        service = new DatasetDataReaderService(localStorageService, new ObjectMapper(), datasetMapper);
    }

    @Test
    @DisplayName("readRows - 读取 CSV 文件")
    void readRows_csv() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/test.csv");
        when(localStorageService.getFileInputStream("test.csv"))
                .thenReturn(new ByteArrayInputStream("a,b\n1,2\n3,4".getBytes()));

        List<Map<String, String>> rows = service.readRows(dataset);

        assertEquals(2, rows.size());
        assertEquals("1", rows.get(0).get("a"));
        assertEquals("2", rows.get(0).get("b"));
        assertEquals("3", rows.get(1).get("a"));
        assertEquals("4", rows.get(1).get("b"));
    }

    @Test
    @DisplayName("readRows - 读取空 CSV 文件")
    void readRows_csv_empty() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/empty.csv");
        when(localStorageService.getFileInputStream("empty.csv"))
                .thenReturn(new ByteArrayInputStream("".getBytes()));

        List<Map<String, String>> rows = service.readRows(dataset);

        assertTrue(rows.isEmpty());
    }

    @Test
    @DisplayName("readRows - 读取仅含表头的 CSV")
    void readRows_csv_onlyHeader() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/header.csv");
        when(localStorageService.getFileInputStream("header.csv"))
                .thenReturn(new ByteArrayInputStream("a,b\n".getBytes()));

        List<Map<String, String>> rows = service.readRows(dataset);

        assertTrue(rows.isEmpty());
    }

    @Test
    @DisplayName("readRows - 读取 xlsx 文件")
    void readRows_xlsx() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setFileType("xlsx");
        dataset.setFileUrl("http://test/files/test.xlsx");

        Path xlsxPath = tempDir.resolve("test.xlsx");
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(xlsxPath.toFile())) {
            Sheet sheet = workbook.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("name");
            header.createCell(1).setCellValue("value");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("foo");
            row1.createCell(1).setCellValue(100);
            workbook.write(fos);
        }

        try (InputStream is = Files.newInputStream(xlsxPath)) {
            when(localStorageService.getFileInputStream("test.xlsx")).thenReturn(is);
            List<Map<String, String>> rows = service.readRows(dataset);
            assertEquals(1, rows.size());
            assertEquals("foo", rows.get(0).get("name"));
            assertEquals("100", rows.get(0).get("value"));
        }
    }

    @Test
    @DisplayName("readRows - 读取 xls 文件")
    void readRows_xls() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setFileType("xls");
        dataset.setFileUrl("http://test/files/test.xls");

        Path xlsPath = tempDir.resolve("test.xls");
        try (Workbook workbook = new HSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(xlsPath.toFile())) {
            Sheet sheet = workbook.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("col");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("val");
            workbook.write(fos);
        }

        try (InputStream is = Files.newInputStream(xlsPath)) {
            when(localStorageService.getFileInputStream("test.xls")).thenReturn(is);
            List<Map<String, String>> rows = service.readRows(dataset);
            assertEquals(1, rows.size());
            assertEquals("val", rows.get(0).get("col"));
        }
    }

    @Test
    @DisplayName("readRows - 读取 json 文件")
    void readRows_json() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setFileType("json");
        dataset.setFileUrl("http://test/files/test.json");
        when(localStorageService.getFileInputStream("test.json"))
                .thenReturn(new ByteArrayInputStream("[{\"a\":\"1\",\"b\":\"2\"}]".getBytes()));

        List<Map<String, String>> rows = service.readRows(dataset);

        assertEquals(1, rows.size());
        assertEquals("1", rows.get(0).get("a"));
        assertEquals("2", rows.get(0).get("b"));
    }

    @Test
    @DisplayName("readRows - 不支持的文件类型抛 BusinessException")
    void readRows_unsupportedType_throwsBusinessException() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setFileType("txt");
        dataset.setFileUrl("http://test/files/test.txt");
        when(localStorageService.getFileInputStream("test.txt"))
                .thenReturn(new ByteArrayInputStream("data".getBytes()));

        assertThrows(BusinessException.class, () -> service.readRows(dataset));
    }

    @Test
    @DisplayName("readRows - fileUrl 为空抛 BusinessException")
    void readRows_blankFileUrl_throwsBusinessException() {
        Dataset dataset = new Dataset();
        dataset.setFileType("csv");
        dataset.setFileUrl("");

        assertThrows(BusinessException.class, () -> service.readRows(dataset));
    }

    @Test
    @DisplayName("readRows - fileUrl 为 null 抛 BusinessException")
    void readRows_nullFileUrl_throwsBusinessException() {
        Dataset dataset = new Dataset();
        dataset.setFileType("csv");
        dataset.setFileUrl(null);

        assertThrows(BusinessException.class, () -> service.readRows(dataset));
    }

    @Test
    @DisplayName("readRows - 读取抛异常时包装为 BusinessException")
    void readRows_readException_wrapsBusinessException() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setFileType("json");
        dataset.setFileUrl("http://test/files/bad.json");
        when(localStorageService.getFileInputStream("bad.json"))
                .thenReturn(new ByteArrayInputStream("invalid json".getBytes()));

        assertThrows(BusinessException.class, () -> service.readRows(dataset));
    }

    @Test
    @DisplayName("loadSeriesData - 成功加载时序数据")
    void loadSeriesData_success() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/series.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(localStorageService.getFileInputStream("series.csv"))
                .thenReturn(new ByteArrayInputStream("date,value\n2024-01-01,10\n2024-01-02,20".getBytes()));

        List<PredictionData.SeriesPoint> points = service.loadSeriesData(1L, "date", "value");

        assertEquals(2, points.size());
        assertEquals(LocalDate.of(2024, 1, 1), points.get(0).date());
        assertEquals(10.0, points.get(0).value());
        assertEquals(LocalDate.of(2024, 1, 2), points.get(1).date());
        assertEquals(20.0, points.get(1).value());
    }

    @Test
    @DisplayName("loadSeriesData - datasetId 为空抛 BusinessException")
    void loadSeriesData_nullDatasetId_throwsBusinessException() {
        assertThrows(BusinessException.class, () -> service.loadSeriesData(null, "date", "value"));
    }

    @Test
    @DisplayName("loadSeriesData - 数据集不存在抛 BusinessException")
    void loadSeriesData_datasetNotFound_throwsBusinessException() {
        when(datasetMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.loadSeriesData(99L, "date", "value"));
    }

    @Test
    @DisplayName("loadSeriesData - 日期格式 yyyy/MM/dd")
    void loadSeriesData_slashDateFormat() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/series.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(localStorageService.getFileInputStream("series.csv"))
                .thenReturn(new ByteArrayInputStream("date,value\n2024/01/01,10".getBytes()));

        List<PredictionData.SeriesPoint> points = service.loadSeriesData(1L, "date", "value");

        assertEquals(1, points.size());
        assertEquals(LocalDate.of(2024, 1, 1), points.get(0).date());
    }

    @Test
    @DisplayName("loadSeriesData - 日期格式 yyyy-MM-dd HH:mm:ss")
    void loadSeriesData_dateTimeFormat() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/series.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(localStorageService.getFileInputStream("series.csv"))
                .thenReturn(new ByteArrayInputStream("date,value\n2024-01-01 10:00:00,10".getBytes()));

        List<PredictionData.SeriesPoint> points = service.loadSeriesData(1L, "date", "value");

        assertEquals(1, points.size());
        assertEquals(LocalDate.of(2024, 1, 1), points.get(0).date());
    }

    @Test
    @DisplayName("loadSeriesData - 非法日期使用默认日期")
    void loadSeriesData_invalidDate_usesDefault() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/series.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(localStorageService.getFileInputStream("series.csv"))
                .thenReturn(new ByteArrayInputStream("date,value\nnotadate,10".getBytes()));

        List<PredictionData.SeriesPoint> points = service.loadSeriesData(1L, "date", "value");

        assertEquals(1, points.size());
        assertNotNull(points.get(0).date());
    }

    @Test
    @DisplayName("loadSeriesData - 非法数值使用 0")
    void loadSeriesData_invalidValue_usesZero() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/series.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(localStorageService.getFileInputStream("series.csv"))
                .thenReturn(new ByteArrayInputStream("date,value\n2024-01-01,notanumber".getBytes()));

        List<PredictionData.SeriesPoint> points = service.loadSeriesData(1L, "date", "value");

        assertEquals(1, points.size());
        assertEquals(0.0, points.get(0).value());
    }

    @Test
    @DisplayName("loadSeriesData - timeField 和 targetField 为 null")
    void loadSeriesData_nullFields_usesDefaults() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/series.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(localStorageService.getFileInputStream("series.csv"))
                .thenReturn(new ByteArrayInputStream("date,value\n2024-01-01,10".getBytes()));

        List<PredictionData.SeriesPoint> points = service.loadSeriesData(1L, null, null);

        assertEquals(1, points.size());
        assertNotNull(points.get(0).date());
        assertEquals(0.0, points.get(0).value());
    }
}
