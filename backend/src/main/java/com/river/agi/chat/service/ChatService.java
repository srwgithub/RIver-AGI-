package com.river.agi.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.chat.dto.ChatRequest;
import com.river.agi.chat.dto.ChatResponse;
import com.river.agi.chat.entity.ChatMessage;
import com.river.agi.chat.entity.ChatSession;
import com.river.agi.chat.mapper.ChatMessageMapper;
import com.river.agi.chat.mapper.ChatSessionMapper;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.annotation.AuditOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {
    
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final DatasetMapper datasetMapper;
    private final SecurityUtils securityUtils;
    
    @Autowired(required = false)
    @Qualifier("chatClient")
    private ChatClient chatClient;

    @Autowired
    public ChatService(ChatSessionMapper chatSessionMapper,
                       ChatMessageMapper chatMessageMapper,
                       DatasetMapper datasetMapper,
                       SecurityUtils securityUtils) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.datasetMapper = datasetMapper;
        this.securityUtils = securityUtils;
    }
    
    @AuditOperation(action = "CREATE_SESSION", resourceType = "CHAT_SESSION", description = "Create chat session")
    public ChatSession createSession(Long datasetId, Authentication authentication) {
        ChatSession session = new ChatSession();
        
        if (datasetId != null) {
            Dataset dataset = datasetMapper.selectById(datasetId);
            if (dataset != null) {
                session.setTitle("分析: " + dataset.getName());
                session.setDatasetId(datasetId);
            } else {
                throw new BusinessException("Dataset not found");
            }
        } else {
            session.setTitle("新对话");
        }
        
        session.setUserId(securityUtils.getCurrentUserId(authentication));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        
        chatSessionMapper.insert(session);
        return session;
    }
    
    @AuditOperation(action = "AI_CHAT", resourceType = "CHAT_MESSAGE", description = "Send AI chat message with tool calls")
    public ChatResponse sendMessage(ChatRequest request, Authentication authentication) {
        Long sessionId;
        ChatSession session;
        if (request.getSessionId() != null) {
            sessionId = request.getSessionId();
            session = chatSessionMapper.selectById(sessionId);
            if (session == null) {
                throw new BusinessException("Session not found");
            }
        } else {
            session = createSession(request.getDatasetId(), authentication);
            sessionId = session.getId();
        }
        
        String datasetContext = "";
        if (session.getDatasetId() != null) {
            Dataset dataset = datasetMapper.selectById(session.getDatasetId());
            if (dataset != null) {
                datasetContext = """
                    当前数据集信息:
                    - 名称: %s
                    - 行数: %d
                    - 列数: %d
                    - 文件类型: %s
                    - 状态: %s
                    """.formatted(
                    dataset.getName(),
                    dataset.getRowCount() != null ? dataset.getRowCount() : 0,
                    dataset.getColumnCount() != null ? dataset.getColumnCount() : 0,
                    dataset.getFileType() != null ? dataset.getFileType() : "UNKNOWN",
                    dataset.getStatus() != null ? dataset.getStatus() : "UNKNOWN"
                );
            }
        }
        
        String systemPrompt = """
            你是 RIver AGI 数据智能分析平台的助手。
            
            你可以使用以下工具:
            - inspectDataset: 查看数据集基本信息
            - profileDataset: 生成数据画像（列统计、数据类型、空值率等）
            - analyzeQuality: 分析数据质量（完整性、唯一性、准确性、一致性、有效性）
            - detectOutliers: 检测异常值（Z-score算法）
            - scanSensitiveData: 扫描敏感信息（手机号、身份证、银行卡等）
            - getSecurityScanResults: 获取安全扫描结果
            - recommendCharts: 推荐图表类型
            - generateChart: 生成图表数据
            - createPredictionTask: 创建并运行预测任务
            - getPredictionResults: 获取预测结果和指标
            - retrainPrediction: 重新训练预测模型
            
            %s
            
            重要规则:
            1. 必须使用工具获取真实数据，严禁编造或生成虚假数据
            2. 如果用户请求涉及数据集操作，必须先获取 datasetId 并调用对应工具
            3. 如果工具调用失败，如实告知用户错误信息
            4. 使用中文回答问题
            """.formatted(datasetContext);
        
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        
        List<ChatMessage> historyMessages = chatMessageMapper.selectBySessionId(sessionId);
        for (ChatMessage msg : historyMessages) {
            if ("USER".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("ASSISTANT".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        
        messages.add(new UserMessage(request.getMessage()));
        
        String reply;
        if (chatClient == null) {
            reply = "AI 服务未配置 API Key，当前为演示模式。您可以通过配置 DEEPSEEK_API_KEY 环境变量来启用 AI 对话功能。";
            log.warn("AI chat client is not available (no API key configured)");
        } else {
            try {
                var promptSpec = chatClient.prompt().messages(messages);
                // Tools are registered once as ChatClient defaults in AiConfig.
                reply = promptSpec.call().content();

                if (reply == null || reply.isBlank()) {
                    reply = "抱歉，我无法处理您的请求。请稍后重试。";
                }
            } catch (Exception e) {
                log.error("AI call failed for session {}", sessionId, e);
                reply = "抱歉，AI 服务暂时不可用: " + e.getMessage() + "。请稍后重试。";
            }
        }
        
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setRole("USER");
        userMessage.setContent(request.getMessage());
        userMessage.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(userMessage);
        
        ChatMessage aiMessage = new ChatMessage();
        aiMessage.setSessionId(sessionId);
        aiMessage.setRole("ASSISTANT");
        aiMessage.setContent(reply);
        aiMessage.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(aiMessage);
        
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(session);
        
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setSessionId(sessionId);
        chatResponse.setReply(reply);
        chatResponse.setTimestamp(LocalDateTime.now());
        
        return chatResponse;
    }
    
    public PageResult<ChatSession> getSessions(int page, int size, Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        Page<ChatSession> pageRequest = new Page<>(page, size);
        Page<ChatSession> pageResult = chatSessionMapper.selectByUserId(pageRequest, userId);
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }
    
    public ChatSession getSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("Session not found");
        }
        return session;
    }
    
    public List<ChatMessage> getMessages(Long sessionId) {
        return chatMessageMapper.selectBySessionId(sessionId);
    }
    
    @AuditOperation(action = "DELETE_SESSION", resourceType = "CHAT_SESSION", description = "Delete chat session")
    public void deleteSession(Long sessionId) {
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        chatSessionMapper.deleteById(sessionId);
    }
}
