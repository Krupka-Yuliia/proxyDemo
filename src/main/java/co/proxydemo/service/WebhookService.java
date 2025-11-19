package co.proxydemo.service;

import co.proxydemo.dto.WebhookEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WebhookService {

    private final List<WebhookEvent> eventQueue = new ArrayList<>();

    public void sendWebhook(WebhookEvent event) {
        System.out.println("[WEBHOOK] Sending webhook: " + event.getEventType());
        System.out.println("Event ID: " + event.getEventId());
        System.out.println("Transaction: " + event.getTransactionId());
        if (event.getProductId() != null) {
            System.out.println("Product: " + event.getProductId() + " - " + event.getDescription());
        }
        eventQueue.add(event);
    }

    public List<WebhookEvent> getEvents() {
        return new ArrayList<>(eventQueue);
    }
}