package com.omarazzam.paymentguard.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omarazzam.paymentguard.evaluation.entity.message.FraudStatus;
import com.omarazzam.paymentguard.evaluation.entity.message.PaymentTransactionEvaluation;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;


@Log4j2
@Service
public class ReceiveMessage {


    public static final String READER_URL  = "http://ENTRYPOINT//api/v1/fraud-status";

    @Autowired
    private EvaluateMessageService evaluateMessageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;


    @KafkaListener(topics = "transaction", groupId = "evaluation-service")
    public void receiveMessage(String value, Acknowledgment acknowledgment) {

        log.info("Received message from KAFKA {} " , value);
        try {
        PaymentTransactionEvaluation message = objectMapper.readValue(value , PaymentTransactionEvaluation.class);

        boolean result = evaluateMessageService.evaluateMessage(message);
        FraudStatus flag = result ? FraudStatus.FRAUD : FraudStatus.NOTFRAUD;
        message.setFlag(flag);
        sendToReader(message);
        acknowledgment.acknowledge();

        } catch (final Exception e) {
            log.error(e);
        }

    }

    @Async
    public void sendToReader(PaymentTransactionEvaluation message) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Sending  message back to reader ");
                restTemplate.postForEntity(READER_URL, message, Void.class);
            } catch (Exception e) {

                throw new RuntimeException("Failed to send message to reader", e);
            }
        });
    }
}
