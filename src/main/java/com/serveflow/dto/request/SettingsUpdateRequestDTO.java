package com.serveflow.dto.request;

import lombok.Data;

/**
 * SettingsUpdateRequestDTO — carries updated settings from the QuickBill settings page.
 *
 * Used as the request body for PUT /api/biller/settings.
 *
 * Note: The college email domain is NOT updatable via this endpoint — it requires
 * a restart with updated application.properties. This is by design; changing the
 * allowed domain is a deliberate admin action, not a routine biller operation.
 */
@Data
public class SettingsUpdateRequestDTO {

    // Lunch start time in HH:mm format (e.g. "12:30").
    // Online ordering closes 15 minutes before this time.
    private String lunchStartTime;

    // How many minutes back to search for matching payments when a bill is created.
    // For example, 10 means: find payments received in the last 10 minutes.
    private Integer matchingWindowMinutes;

    // The IP address of the thermal printer.
    private String printerHost;
}
