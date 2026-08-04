package com.river.agi.dataset.service;

import com.river.agi.common.BusinessException;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.entity.DatasetField;
import com.river.agi.dataset.mapper.DatasetFieldMapper;
import com.river.agi.dataset.mapper.DatasetMapper;
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

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("数据集解析服务测试")
class DatasetParserServiceTest {

    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetFieldMapper datasetFieldMapper;
    @Mock
    private LocalStorageService localStorageService;

    @TempDir
    Path tempDir;

    private DatasetParserService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DatasetParserService(datasetMapper, datasetFieldMapper, localStorageService);
        Field localPathField = DatasetParserService.class.getDeclaredField("localPath");
        localPathField.setAccessible(true);
        localPathField.set(service, tempDir.toString());
    }

    @Test
    @DisplayName("parseDataset - 数据集不存在抛异常")
    void parseDataset_notFound_throws() {
        when(datasetMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.parseDataset(99L));
    }

    @Test
    @DisplayName("parseDataset - 文件不存在抛异常并标记 PARSE_FAILED")
    void parseDataset_fileNotFound_throwsAndMarksFailed() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/missing.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        assertThrows(BusinessException.class, () -> service.parseDataset(1L));

        // 应该先设置 PARSING，然后设置 PARSE_FAILED
        verify(datasetMapper, atLeast(2)).updateById(any());
        assertEquals("PARSE_FAILED", dataset.getStatus());
    }

    @Test
    @DisplayName("parseDataset - 成功解析 CSV 文件")
    void parseDataset_csv_success() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/test.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        // 创建 CSV 测试文件
        Path csvPath = tempDir.resolve("test.csv");
        Files.write(csvPath, "name,age,city\nAlice,30,Beijing\nBob,25,Shanghai\n".getBytes());

        service.parseDataset(1L);

        assertEquals("PARSED", dataset.getStatus());
        assertEquals(2, dataset.getRowCount());
        assertEquals(3, dataset.getColumnCount());
        assertNotNull(dataset.getPreviewJson());
        assertNotNull(dataset.getSchemaJson());
        verify(datasetMapper, atLeast(2)).updateById(any());
        verify(datasetFieldMapper, times(3)).insert(any());
    }

    @Test
    @DisplayName("parseDataset - CSV 仅含表头")
    void parseDataset_csv_onlyHeader() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/header.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path csvPath = tempDir.resolve("header.csv");
        Files.write(csvPath, "col1,col2\n".getBytes());

        service.parseDataset(1L);

        assertEquals("PARSED", dataset.getStatus());
        assertEquals(0, dataset.getRowCount());
        assertEquals(2, dataset.getColumnCount());
        verify(datasetFieldMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("parseDataset - CSV 超过 10 行，预览仅保留 10 行")
    void parseDataset_csv_moreThan10Rows() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/big.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        StringBuilder csvContent = new StringBuilder("val\n");
        for (int i = 0; i < 15; i++) {
            csvContent.append(i).append("\n");
        }
        Path csvPath = tempDir.resolve("big.csv");
        Files.write(csvPath, csvContent.toString().getBytes());

        service.parseDataset(1L);

        assertEquals("PARSED", dataset.getStatus());
        assertEquals(15, dataset.getRowCount());
        // 预览数据仅含 10 行
        assertTrue(dataset.getPreviewJson().contains("9"));
        verify(datasetFieldMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("parseDataset - 成功解析 XLSX 文件")
    void parseDataset_xlsx_success() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("xlsx");
        dataset.setFileUrl("http://test/files/test.xlsx");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path xlsxPath = tempDir.resolve("test.xlsx");
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(xlsxPath.toFile())) {
            Sheet sheet = workbook.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("name");
            header.createCell(1).setCellValue("score");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Alice");
            row1.createCell(1).setCellValue(95.0);
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Bob");
            row2.createCell(1).setCellValue(87.0);
            workbook.write(fos);
        }

        service.parseDataset(1L);

        assertEquals("PARSED", dataset.getStatus());
        assertEquals(2, dataset.getRowCount());
        assertEquals(2, dataset.getColumnCount());
        verify(datasetFieldMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("parseDataset - JSON 类型抛异常")
    void parseDataset_json_throws() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("json");
        dataset.setFileUrl("http://test/files/test.json");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path jsonPath = tempDir.resolve("test.json");
        Files.write(jsonPath, "[{\"a\":\"1\"}]".getBytes());

        assertThrows(BusinessException.class, () -> service.parseDataset(1L));
        assertEquals("PARSE_FAILED", dataset.getStatus());
    }

    @Test
    @DisplayName("parseDataset - 不支持的文件类型抛异常")
    void parseDataset_unsupportedType_throws() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("txt");
        dataset.setFileUrl("http://test/files/test.txt");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path txtPath = tempDir.resolve("test.txt");
        Files.write(txtPath, "some text".getBytes());

        assertThrows(BusinessException.class, () -> service.parseDataset(1L));
        assertEquals("PARSE_FAILED", dataset.getStatus());
    }

    @Test
    @DisplayName("parseDataset - XLS 类型成功解析")
    void parseDataset_xls_success() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("xls");
        dataset.setFileUrl("http://test/files/test.xls");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path xlsPath = tempDir.resolve("test.xls");
        try (Workbook workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(xlsPath.toFile())) {
            Sheet sheet = workbook.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("col");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("val1");
            workbook.write(fos);
        }

        service.parseDataset(1L);

        assertEquals("PARSED", dataset.getStatus());
        assertEquals(1, dataset.getRowCount());
        assertEquals(1, dataset.getColumnCount());
        verify(datasetFieldMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("parseDataset - 大写 XLSX 类型")
    void parseDataset_uppercaseXlsx_success() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("XLSX");
        dataset.setFileUrl("http://test/files/test2.xlsx");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path xlsxPath = tempDir.resolve("test2.xlsx");
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(xlsxPath.toFile())) {
            Sheet sheet = workbook.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("h");
            workbook.write(fos);
        }

        service.parseDataset(1L);

        assertEquals("PARSED", dataset.getStatus());
    }

    @Test
    @DisplayName("parseDataset - 数值列类型推断为 NUMERIC")
    void parseDataset_numericInference() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/numeric.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path csvPath = tempDir.resolve("numeric.csv");
        Files.write(csvPath, "value\n1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n".getBytes());

        service.parseDataset(1L);

        assertEquals("PARSED", dataset.getStatus());
        // schemaJson 应该包含 NUMERIC 类型
        assertTrue(dataset.getSchemaJson().contains("NUMERIC"));
    }

    @Test
    @DisplayName("parseDataset - 日期列类型推断为 DATE")
    void parseDataset_dateInference() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/dates.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path csvPath = tempDir.resolve("dates.csv");
        Files.write(csvPath, "date\n2024-01-01\n2024-01-02\n2024-01-03\n".getBytes());

        service.parseDataset(1L);

        assertEquals("PARSED", dataset.getStatus());
        assertTrue(dataset.getSchemaJson().contains("DATE"));
    }

    @Test
    @DisplayName("parseDataset - 字符串列类型推断为 STRING")
    void parseDataset_stringInference() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/strings.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path csvPath = tempDir.resolve("strings.csv");
        Files.write(csvPath, "name\nAlice\nBob\nCharlie\n".getBytes());

        service.parseDataset(1L);

        assertEquals("PARSED", dataset.getStatus());
        assertTrue(dataset.getSchemaJson().contains("STRING"));
    }

    @Test
    @DisplayName("parseDataset - 含空值的列正确统计 nullCount")
    void parseDataset_withEmptyValues() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/empty.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path csvPath = tempDir.resolve("empty.csv");
        Files.write(csvPath, "a,b\n1,\n,2\n".getBytes());

        service.parseDataset(1L);

        assertEquals("PARSED", dataset.getStatus());
        verify(datasetFieldMapper, times(2)).insert(argThat(f -> {
            DatasetField field = (DatasetField) f;
            // 第一列有一个空值，第二列有一个空值
            return field.getNullCount() != null;
        }));
    }

    @Test
    @DisplayName("parseDataset - 成功解析时设置字段位置")
    void parseDataset_setsFieldPosition() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileType("csv");
        dataset.setFileUrl("http://test/files/pos.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Path csvPath = tempDir.resolve("pos.csv");
        Files.write(csvPath, "col1,col2,col3\na,b,c\n".getBytes());

        service.parseDataset(1L);

        verify(datasetFieldMapper, times(3)).insert(argThat(f -> {
            DatasetField field = (DatasetField) f;
            return field.getPosition() != null && field.getPosition() >= 0 && field.getPosition() < 3;
        }));
    }
}
