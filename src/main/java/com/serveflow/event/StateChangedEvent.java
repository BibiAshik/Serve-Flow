package com.serveflow.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event fired whenever the billing system state changes (new bill, payment matched, etc.)
 * Listened to by BillingService to trigger an SSE broadcast.
 */
public class StateChangedEvent extends ApplicationEvent {
    public StateChangedEvent(Object source) {
        super(source);
    }
}
