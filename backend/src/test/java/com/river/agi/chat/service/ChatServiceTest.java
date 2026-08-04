package com.river.agi.chat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.chat.dto.ChatRequest;
import com.river.agi.chat.dto.ChatResponse;
import com.river.agi.chat.entity.ChatMessage;
import com.river.agi.chat.entity.ChatSession;
import com.river.agi.chat.mapper.ChatMessageMapper;
import com.river.agi.chat.mapper.ChatSessionMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("聊天服务测试")
class ChatServiceTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;
    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private Authentication auth;

    private ChatService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ChatService(chatSessionMapper, chatMessageMapper, datasetMapper, securityUtils);
        // chatClient 字段注入为 null 走演示模式
        Field chatClientField = ChatService.class.getDeclaredField("chatClient");
        chatClientField.setAccessible(true);
        chatClientField.set(service, null);
        lenient().when(securityUtils.getCurrentUserId(any(Authentication.class))).thenReturn(1L);
    }

    @Test
    @DisplayName("createSession - 无 datasetId 创建新对话")
    void createSession_noDataset() {
        when(chatSessionMapper.insert(any())).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            s.setId(1L);
            return 1;
        });

        ChatSession session = service.createSession(null, auth);
        assertNotNull(session);
        assertEquals("新对话", session.getTitle());
        assertEquals(1L, session.getUserId());
    }

    @Test
    @DisplayName("createSession - 数据集存在时使用数据集名称")
    void createSession_withDataset() {
        Dataset dataset = new Dataset();
        dataset.setId(10L);
        dataset.setName("sales_data");
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(chatSessionMapper.insert(any())).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            s.setId(1L);
            return 1;
        });

        ChatSession session = service.createSession(10L, auth);
        assertEquals("分析: sales_data", session.getTitle());
        assertEquals(10L, session.getDatasetId());
    }

    @Test
    @DisplayName("createSession - 数据集不存在抛异常")
    void createSession_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createSession(999L, auth));
    }

    @Test
    @DisplayName("sendMessage - 不带 sessionId 自动创建")
    void sendMessage_noSessionId() {
        // 模拟创建新会话
        when(chatSessionMapper.insert(any())).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            s.setId(1L);
            return 1;
        });
        when(chatMessageMapper.selectBySessionId(anyLong())).thenReturn(new ArrayList<>());
        when(chatMessageMapper.insert(any())).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        });
        when(chatSessionMapper.updateById(any())).thenReturn(1);

        ChatRequest request = new ChatRequest();
        request.setMessage("hello");
        ChatResponse response = service.sendMessage(request, auth);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        assertNotNull(response.getReply());
        // chatClient 为 null 走演示模式
        assertTrue(response.getReply().contains("演示模式"));
    }

    @Test
    @DisplayName("sendMessage - 带 sessionId 但会话不存在")
    void sendMessage_sessionNotFound() {
        when(chatSessionMapper.selectById(anyLong())).thenReturn(null);
        ChatRequest request = new ChatRequest();
        request.setSessionId(999L);
        request.setMessage("hello");
        assertThrows(BusinessException.class, () -> service.sendMessage(request, auth));
    }

    @Test
    @DisplayName("sendMessage - 带 sessionId 且会话存在")
    void sendMessage_withExistingSession() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        when(chatMessageMapper.selectBySessionId(anyLong())).thenReturn(new ArrayList<>());
        when(chatMessageMapper.insert(any())).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        });
        when(chatSessionMapper.updateById(any())).thenReturn(1);

        ChatRequest request = new ChatRequest();
        request.setSessionId(1L);
        request.setMessage("test message");
        ChatResponse response = service.sendMessage(request, auth);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
    }

    @Test
    @DisplayName("sendMessage - 会话带数据集上下文")
    void sendMessage_withDatasetContext() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setDatasetId(10L);
        session.setUserId(1L);

        Dataset dataset = new Dataset();
        dataset.setId(10L);
        dataset.setName("sales");
        dataset.setRowCount(100);
        dataset.setColumnCount(5);
        dataset.setFileType("CSV");
        dataset.setStatus("PARSED");

        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(chatMessageMapper.selectBySessionId(anyLong())).thenReturn(new ArrayList<>());
        when(chatMessageMapper.insert(any())).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        });
        when(chatSessionMapper.updateById(any())).thenReturn(1);

        ChatRequest request = new ChatRequest();
        request.setSessionId(1L);
        request.setMessage("分析数据");
        ChatResponse response = service.sendMessage(request, auth);

        assertNotNull(response);
    }

    @Test
    @DisplayName("sendMessage - 历史消息按角色加载")
    void sendMessage_withHistory() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);

        ChatMessage userMsg = new ChatMessage();
        userMsg.setRole("USER");
        userMsg.setContent("previous question");

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setRole("ASSISTANT");
        aiMsg.setContent("previous answer");

        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        when(chatMessageMapper.selectBySessionId(anyLong())).thenReturn(List.of(userMsg, aiMsg));
        when(chatMessageMapper.insert(any())).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        });
        when(chatSessionMapper.updateById(any())).thenReturn(1);

        ChatRequest request = new ChatRequest();
        request.setSessionId(1L);
        request.setMessage("continue");
        ChatResponse response = service.sendMessage(request, auth);

        assertNotNull(response);
    }

    @Test
    @DisplayName("getSessions - 分页返回会话")
    void getSessions_success() {
        Page<ChatSession> page = new Page<>(1, 10);
        ChatSession s = new ChatSession();
        s.setId(1L);
        page.setRecords(List.of(s));
        page.setTotal(1L);
        when(chatSessionMapper.selectByUserId(any(), anyLong())).thenReturn(page);

        PageResult<ChatSession> result = service.getSessions(1, 10, auth);
        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("getSessions - 空结果")
    void getSessions_empty() {
        Page<ChatSession> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(0L);
        when(chatSessionMapper.selectByUserId(any(), anyLong())).thenReturn(page);

        PageResult<ChatSession> result = service.getSessions(1, 10, auth);
        assertNotNull(result);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("getSession - 找到会话")
    void getSession_found() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        when(chatSessionMapper.selectById(1L)).thenReturn(session);

        ChatSession result = service.getSession(1L);
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("getSession - 不存在抛异常")
    void getSession_notFound() {
        when(chatSessionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getSession(999L));
    }

    @Test
    @DisplayName("getMessages - 返回消息列表")
    void getMessages_success() {
        ChatMessage msg = new ChatMessage();
        msg.setId(1L);
        msg.setContent("hello");
        when(chatMessageMapper.selectBySessionId(1L)).thenReturn(List.of(msg));

        List<ChatMessage> result = service.getMessages(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getMessages - 空列表")
    void getMessages_empty() {
        when(chatMessageMapper.selectBySessionId(anyLong())).thenReturn(new ArrayList<>());
        List<ChatMessage> result = service.getMessages(999L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("deleteSession - 删除会话和消息")
    void deleteSession_success() {
        when(chatMessageMapper.delete(any())).thenReturn(5);
        when(chatSessionMapper.deleteById(anyLong())).thenReturn(1);
        assertDoesNotThrow(() -> service.deleteSession(1L));
        verify(chatMessageMapper).delete(any());
        verify(chatSessionMapper).deleteById(1L);
    }
}
