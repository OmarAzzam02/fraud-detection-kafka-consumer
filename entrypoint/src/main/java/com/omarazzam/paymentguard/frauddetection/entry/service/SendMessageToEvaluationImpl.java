package com.omarazzam.paymentguard.frauddetection.entry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omarazzam.paymentguard.frauddetection.entry.entity.PaymentTransaction;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Log4j2
@Service
public class SendMessageToEvaluationImpl implements SendMessageToEvaluation {



    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;


    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    EvaluatedMessageCashe evaluatedMessageCashe;

    @Autowired
    MonitorService monitorService;

    @Autowired
    private ObjectMapper objectMapper;


    @Override
    public PaymentTransaction sendMessage(final PaymentTransaction message) {
        log.info("Sending Message to Evaluation {}", message.getId());
        kafkaTemplate.send("transaction", message);

//        while (!evaluatedMessageCashe.isResponeBack(message.getId())) {}
//        PaymentTransaction messageResultBack=evaluatedMessageCashe.getTransaction(message.getId());
//        evaluatedMessageCashe.removeTransaction(messageResultBack);
//        log.info("response is back from evaluation");
//        return messageResultBack;

        synchronized (monitorService.getSharedMonitor()) {
            while (!evaluatedMessageCashe.isResponeBack(message.getId())) {
                try {
                    monitorService.getSharedMonitor().wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            PaymentTransaction messageResultBack = evaluatedMessageCashe.getEvaluatedMessages().get(message.getId());
            evaluatedMessageCashe.removeTransaction(messageResultBack);

            log.info("Message Result Back in reader {}", messageResultBack.getId());

            return messageResultBack;
        }
    }

        @KafkaListener(topics = "evaluated-transactions", groupId = "evaluated-transactions")
    public void receiveEvaluatedMessage(String value) {
        try {
            log.info("Received evaluated message: {}", value);

            PaymentTransaction evaluatedMessage = objectMapper.readValue(value, PaymentTransaction.class);
            evaluatedMessageCashe.addTransaction(evaluatedMessage);

            synchronized (monitorService.getSharedMonitor()) {
//                ack.acknowledge();
                monitorService.getSharedMonitor().notifyAll();
            }

        } catch (final Exception e) {
            log.error("Error processing message", e);
        }
    }

}
