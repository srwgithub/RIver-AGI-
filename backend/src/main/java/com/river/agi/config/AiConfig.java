package com.river.agi.config;

import com.river.agi.chat.tool.AnalysisTools;
import com.river.agi.chat.tool.ChartTools;
import com.river.agi.chat.tool.PredictionTools;
import com.river.agi.chat.tool.SecurityTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:deepseek-v4-flash}")
    private String model;

    @Bean
    @ConditionalOnExpression("!'${spring.ai.openai.api-key:}'.trim().isEmpty()")
    public OpenAiApi openAiApi() {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnExpression("!'${spring.ai.openai.api-key:}'.trim().isEmpty()")
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnExpression("!'${spring.ai.openai.api-key:}'.trim().isEmpty()")
    public ChatClient chatClient(OpenAiChatModel chatModel, ToolCallbackProvider toolCallbackProvider) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是 RIver AGI 数据智能分析平台的助手。" +
                        "你必须使用提供的工具来获取真实数据，严禁编造或生成虚假数据。" +
                        "当用户请求分析、扫描、预测、图表等功能时，必须调用对应的工具。" +
                        "如果工具调用失败，如实告知用户错误，不要生成假结果。")
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }
    
    @Bean
    @ConditionalOnExpression("!'${spring.ai.openai.api-key:}'.trim().isEmpty()")
    public ToolCallbackProvider toolCallbackProvider(AnalysisTools analysisTools,
                                                     SecurityTools securityTools,
                                                     ChartTools chartTools,
                                                     PredictionTools predictionTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(analysisTools, securityTools, chartTools, predictionTools)
                .build();
    }
}
