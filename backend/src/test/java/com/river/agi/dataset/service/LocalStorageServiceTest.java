package com.river.agi.dataset.service;

import com.river.agi.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("本地存储服务测试")
class LocalStorageServiceTest {

    @Mock
    private MultipartFile file;

    @TempDir
    Path tempDir;

    private LocalStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new LocalStorageService();
        Field localPathField = LocalStorageService.class.getDeclaredField("localPath");
        localPathField.setAccessible(true);
        localPathField.set(service, tempDir.toString());

        Field baseUrlField = LocalStorageService.class.getDeclaredField("baseUrl");
        baseUrlField.setAccessible(true);
        baseUrlField.set(service, "http://test");
    }

    @Test
    @DisplayName("uploadFile - 成功上传带扩展名的文件")
    void uploadFile_withExtension_success() throws IOException {
        when(file.getOriginalFilename()).thenReturn("test.csv");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        String url = service.uploadFile(file);

        assertNotNull(url);
        assertTrue(url.startsWith("http://test/api/v1/files/"));
        assertTrue(url.endsWith(".csv"));
    }

    @Test
    @DisplayName("uploadFile - 文件名无扩展名")
    void uploadFile_noExtension_success() throws IOException {
        when(file.getOriginalFilename()).thenReturn("noext");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        String url = service.uploadFile(file);

        assertNotNull(url);
        assertTrue(url.startsWith("http://test/api/v1/files/"));
        assertFalse(url.contains("."));
    }

    @Test
    @DisplayName("uploadFile - 文件名为 null")
    void uploadFile_nullFilename_success() throws IOException {
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        String url = service.uploadFile(file);

        assertNotNull(url);
        assertTrue(url.startsWith("http://test/api/v1/files/"));
    }

    @Test
    @DisplayName("uploadFile - 输入流抛 IOException 抛 BusinessException")
    void uploadFile_ioException_throwsBusinessException() throws IOException {
        when(file.getOriginalFilename()).thenReturn("test.csv");
        when(file.getInputStream()).thenThrow(new IOException("disk error"));

        assertThrows(BusinessException.class, () -> service.uploadFile(file));
    }

    @Test
    @DisplayName("readFile - 成功读取文件")
    void readFile_success() throws IOException {
        Path filePath = tempDir.resolve("read.txt");
        Files.write(filePath, "hello".getBytes());

        byte[] content = service.readFile("read.txt");

        assertEquals("hello", new String(content));
    }

    @Test
    @DisplayName("readFile - 文件不存在抛 BusinessException")
    void readFile_notFound_throwsBusinessException() {
        assertThrows(BusinessException.class, () -> service.readFile("missing.txt"));
    }

    @Test
    @DisplayName("getFileInputStream - 成功获取输入流")
    void getFileInputStream_success() throws IOException {
        Path filePath = tempDir.resolve("stream.txt");
        Files.write(filePath, "content".getBytes());

        try (InputStream is = service.getFileInputStream("stream.txt")) {
            assertNotNull(is);
            assertEquals("content", new String(is.readAllBytes()));
        }
    }

    @Test
    @DisplayName("getFileInputStream - 文件不存在抛 BusinessException")
    void getFileInputStream_notFound_throwsBusinessException() {
        assertThrows(BusinessException.class, () -> service.getFileInputStream("missing.txt"));
    }

    @Test
    @DisplayName("deleteFile - 成功删除已存在文件")
    void deleteFile_success() throws IOException {
        Path filePath = tempDir.resolve("delete.txt");
        Files.write(filePath, "data".getBytes());
        assertTrue(Files.exists(filePath));

        service.deleteFile("delete.txt");

        assertFalse(Files.exists(filePath));
    }

    @Test
    @DisplayName("deleteFile - 文件不存在不抛异常")
    void deleteFile_notFound_noException() {
        assertDoesNotThrow(() -> service.deleteFile("missing.txt"));
    }

    @Test
    @DisplayName("writeFile - 成功写入带扩展名的文件")
    void writeFile_withExtension_success() {
        String filename = service.writeFile("content".getBytes(), "report.xlsx");

        assertNotNull(filename);
        assertTrue(filename.endsWith(".xlsx"));
        assertTrue(Files.exists(tempDir.resolve(filename)));
    }

    @Test
    @DisplayName("writeFile - 文件名无扩展名")
    void writeFile_noExtension_success() {
        String filename = service.writeFile("content".getBytes(), "noext");

        assertNotNull(filename);
        assertFalse(filename.contains("."));
        assertTrue(Files.exists(tempDir.resolve(filename)));
    }

    @Test
    @DisplayName("writeFile - 文件名为 null")
    void writeFile_nullFilename_success() {
        String filename = service.writeFile("content".getBytes(), null);

        assertNotNull(filename);
        assertTrue(Files.exists(tempDir.resolve(filename)));
    }

    @Test
    @DisplayName("fileUrl - 返回拼接后的 URL")
    void fileUrl_success() {
        String url = service.fileUrl("test.csv");
        assertEquals("http://test/api/v1/files/test.csv", url);
    }
}
