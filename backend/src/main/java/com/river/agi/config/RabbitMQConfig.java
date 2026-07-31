package com.river.agi.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    
    // 数据处理队列
    public static final String DATASET_PARSE_QUEUE = "dataset.parse.queue";
    public static final String DATASET_ANALYSIS_QUEUE = "dataset.analysis.queue";
    public static final String SECURITY_SCAN_QUEUE = "security.scan.queue";
    public static final String PREDICTION_TASK_QUEUE = "prediction.task.queue";
    public static final String REPORT_GENERATE_QUEUE = "report.generate.queue";
    
    // 交换机
    public static final String DATA_EXCHANGE = "data.exchange";
    
    @Bean
    public Exchange dataExchange() {
        return ExchangeBuilder.topicExchange(DATA_EXCHANGE).durable(true).build();
    }
    
    @Bean
    public Queue datasetParseQueue() {
        return QueueBuilder.durable(DATASET_PARSE_QUEUE).build();
    }
    
    @Bean
    public Queue datasetAnalysisQueue() {
        return QueueBuilder.durable(DATASET_ANALYSIS_QUEUE).build();
    }
    
    @Bean
    public Queue securityScanQueue() {
        return QueueBuilder.durable(SECURITY_SCAN_QUEUE).build();
    }
    
    @Bean
    public Queue predictionTaskQueue() {
        return QueueBuilder.durable(PREDICTION_TASK_QUEUE).build();
    }
    
    @Bean
    public Queue reportGenerateQueue() {
        return QueueBuilder.durable(REPORT_GENERATE_QUEUE).build();
    }
    
    @Bean
    public Binding parseBinding(Queue datasetParseQueue, Exchange dataExchange) {
        return BindingBuilder.bind(datasetParseQueue).to(dataExchange).with("dataset.parse").noargs();
    }
    
    @Bean
    public Binding analysisBinding(Queue datasetAnalysisQueue, Exchange dataExchange) {
        return BindingBuilder.bind(datasetAnalysisQueue).to(dataExchange).with("dataset.analysis").noargs();
    }
    
    @Bean
    public Binding scanBinding(Queue securityScanQueue, Exchange dataExchange) {
        return BindingBuilder.bind(securityScanQueue).to(dataExchange).with("security.scan").noargs();
    }
    
    @Bean
    public Binding predictionBinding(Queue predictionTaskQueue, Exchange dataExchange) {
        return BindingBuilder.bind(predictionTaskQueue).to(dataExchange).with("prediction.task").noargs();
    }
    
    @Bean
    public Binding reportBinding(Queue reportGenerateQueue, Exchange dataExchange) {
        return BindingBuilder.bind(reportGenerateQueue).to(dataExchange).with("report.generate").noargs();
    }
}
