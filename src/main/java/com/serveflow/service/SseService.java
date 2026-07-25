package com.serveflow.service;

import com.serveflow.dto.response.LiveStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.serveflow.event.StateChangedEvent;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SseService — manages Server-Sent Events (SSE) connections for real-time dashboard updates.
 *
 * This entirely replaces the old 3-second short-polling mechanism.
 * The Biller frontend opens a single connection to /api/biller/stream, and this
 * service holds that connection open. Whenever state changes (new bill, paid bill),
 * this service pushes the new LiveStatusDTO down the open connection.
 */
@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);

    private final BillingService billingService;

    // Thread-safe list of active client connections.
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseService(@Lazy BillingService billingService) {
        this.billingService = billingService;
    }

    /**
     * Registers a new client connection (emitter).
     * @return the SseEmitter to be returned by the controller.
     */
    public SseEmitter createEmitter() {
        // Set timeout to 0 (infinite) or a very large value to keep the connection open.
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);

        // Remove the emitter from the list when it completes or times out.
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        return emitter;
    }

    /**
     * Broadcasts the latest LiveStatusDTO to all connected clients.
     * @param liveStatus the fresh data to push
     */
    public void broadcastLiveStatus(LiveStatusDTO liveStatus) {
        // If nobody is listening, do nothing.
        if (emitters.isEmpty()) {
            return;
        }

        // Send the payload to all active emitters.
        for (SseEmitter emitter : emitters) {
            try {
                // Send as JSON data.
                emitter.send(SseEmitter.event()
                        .name("live-status")
                        .data(liveStatus));
            } catch (IOException e) {
                // If writing fails (e.g., client closed the browser without notifying),
                // remove the broken connection from our list.
                emitter.completeWithError(e);
                emitters.remove(emitter);
                log.debug("Removed dead SSE connection.");
            }
        }
    }

    /**
     * Listens for any state changes broadcast by other services.
     */
    @EventListener
    public void handleStateChangedEvent(StateChangedEvent event) {
        log.info("State change detected from {}. Broadcasting live status.", event.getSource().getClass().getSimpleName());
        LiveStatusDTO liveStatus = billingService.getLiveBillingStatus();
        broadcastLiveStatus(liveStatus);
    }
}
