package com.river.agi.dataset.consumer;

import com.river.agi.dataset.service.DatasetParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatasetParseConsumer {
    
    private final DatasetParserService datasetParserService;
    
    @RabbitListener(queues = "dataset.parse.queue")
    public void handleParseTask(Long datasetId) {
        log.info("Received parse task for dataset: {}", datasetId);
        try {
            datasetParserService.parseDataset(datasetId);
            log.info("Parse task completed for dataset: {}", datasetId);
        } catch (Exception e) {
            log.error("Failed to parse dataset: {}", datasetId, e);
        }
    }
}
