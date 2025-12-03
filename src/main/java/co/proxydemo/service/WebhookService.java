package co.proxydemo.service;

import co.proxydemo.dto.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    private final List<WebhookEvent> eventQueue = new ArrayList<>();

    public void sendWebhook(WebhookEvent event) {
        logger.info("Sending webhook: {} (Event ID: {})", event.getEventType(), event.getEventId());
        logger.debug(
                "Webhook details - Transaction: {}, Product: {}",
                event.getTransactionId(),
                event.getProductId() != null ? event.getProductId() + " - " + event.getDescription() : "N/A"
        );
        eventQueue.add(event);
    }

    public List<WebhookEvent> getEvents() {
        return new ArrayList<>(eventQueue);
    }
}
