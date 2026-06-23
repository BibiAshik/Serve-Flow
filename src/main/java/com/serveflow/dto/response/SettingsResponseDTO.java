package com.serveflow.dto.response;

import lombok.Data;

/**
 * SettingsResponseDTO — carries current system settings to the QuickBill settings page.
 *
 * Returned by GET /api/biller/settings.
 * The college email domain is shown but not editable from the UI (changing it requires
 * a restart with updated application.properties, which is an intentional restriction).
 */
@Data
public class SettingsResponseDTO {

    // Lunch start time in HH:mm format (e.g. "12:30").
    private String lunchStartTime;

    // Time window in minutes for the payment matching engine.
    private Integer matchingWindowMinutes;

    // College email domain — display only. Not editable from the UI.
    private String collegeEmailDomain;

    // Current printer status: "ONLINE" or "OFFLINE".
    private String printerStatus;

    // The IP address of the thermal printer.
    private String printerHost;
}
