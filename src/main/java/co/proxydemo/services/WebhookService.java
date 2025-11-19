package co.proxydemo.services;

import co.proxydemo.dtos.WebhookEvent;
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
        eventQueue.add(event);
    }

    public List<WebhookEvent> getEvents() {
        return new ArrayList<>(eventQueue);
    }
}