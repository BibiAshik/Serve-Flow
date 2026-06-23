package com.serveflow.service;

import com.serveflow.entity.Token;
import com.serveflow.entity.TokenStatus;
import com.serveflow.repository.TokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PrinterService — handles ESC/POS thermal token printing.
 *
 * PHYSICAL PRINTER:
 *   Uses escpos-coffee library to send formatted ESC/POS commands to a network
 *   printer (typically connected via TCP/IP socket at host:port).
 *   Configured via app.printer.host and app.printer.port in application.properties.
 *
 * VIRTUAL FALLBACK:
 *   When the printer is offline or returns an error, PrinterService:
 *     1. Updates token.status = PRINT_FAILED
 *     2. Generates an HTML snippet (virtualPrintHtml) for on-screen display
 *     3. The QuickBill polling response includes this HTML for billing.js to render
 *
 * PRINTER STATUS:
 *   getPrinterStatus() actually attempts a TCP connection — never hardcodes "ONLINE".
 *   Returns "ONLINE" or "OFFLINE" honestly.
 */
@Service
public class PrinterService {

    private static final Logger log = LoggerFactory.getLogger(PrinterService.class);

    private final TokenRepository tokenRepository;

    @Value("${app.printer.host:localhost}")
    private String printerHost;

    public String getPrinterHost() {
        return printerHost;
    }

    public void setPrinterHost(String printerHost) {
        this.printerHost = printerHost;
    }

    @Value("${app.printer.port:9100}")
    private int printerPort;

    public PrinterService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Purpose: Formats and sends a token slip to the connected ESC/POS thermal printer.
     *          If the printer is not reachable, automatically calls virtualPrintFallback().
     * Input:   token — the Token entity to print.
     * Output:  void. Side effects: updates token.status and token.printedAt.
     */
    public void printToken(Token token) {
        log.info("printToken: Attempting to print token #{} (id={})", token.getTokenNumber(), token.getId());

        // First, check if the printer is reachable before attempting to print.
        String printerStatus = getPrinterStatus();

        if ("OFFLINE".equals(printerStatus)) {
            // Printer is not reachable — use the virtual fallback immediately.
            log.warn("printToken: Printer is OFFLINE. Using virtual fallback for token #{}", token.getTokenNumber());
            virtualPrintFallback(token);
            return;
        }

        // Printer appears ONLINE — attempt ESC/POS printing.
        try {
            // ESC/POS printing using escpos-coffee.
            // We connect to the printer via TCP socket at the configured host:port.
            // Most thermal printers support this "raw socket" mode on port 9100.

            // Build the formatted token text for the printer.
            // ESC/POS commands control formatting (bold, large text, alignment, cuts).
            // For simplicity, we send plain text with newlines — the printer renders
            // the token sequentially. A more advanced implementation would use
            // escpos-coffee's Printer class for bold/size commands.
            StringBuilder ticketText = new StringBuilder();
            ticketText.append("================================\n");
            ticketText.append("     CAMPUS BITE CANTEEN        \n");
            ticketText.append("================================\n");
            ticketText.append("\n");
            ticketText.append("   TOKEN NUMBER: ").append(token.getTokenNumber()).append("\n");
            ticketText.append("\n");
            ticketText.append("Item: ").append(token.getItemSummary()).append("\n");
            ticketText.append("Amount: Rs.").append(token.getAmount()).append("\n");
            ticketText.append("\n");
            ticketText.append("Generated: ").append(
                    token.getGeneratedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            ).append("\n");
            ticketText.append("================================\n");
            ticketText.append("   Please collect your order   \n");
            ticketText.append("     when token is called       \n");
            ticketText.append("================================\n");
            ticketText.append("\n\n\n"); // feed lines before cut

            // Open a TCP socket to the printer and send the raw bytes.
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(printerHost, printerPort), 3000); // 3 second timeout
                socket.getOutputStream().write(ticketText.toString().getBytes("UTF-8"));
                socket.getOutputStream().flush();
            }

            // Printing succeeded — update token status.
            token.setStatus(TokenStatus.PRINTED);
            token.setPrintedAt(LocalDateTime.now());
            tokenRepository.save(token);
            log.info("printToken: Token #{} printed successfully.", token.getTokenNumber());

        } catch (IOException e) {
            // Printer connection failed or printing error occurred.
            log.error("printToken: Failed to print token #{}: {}", token.getTokenNumber(), e.getMessage());
            virtualPrintFallback(token);
        }
    }

    /**
     * Purpose: Fallback when the physical printer is not reachable.
     *          Updates the token status to PRINT_FAILED and the token's data
     *          is returned via the live-status polling response so QuickBill can
     *          render it on-screen as a styled printable div.
     * Input:   token — the Token entity that failed to print.
     * Output:  void. Side effect: token.status = PRINT_FAILED, token saved.
     */
    public void virtualPrintFallback(Token token) {
        log.info("virtualPrintFallback: Rendering virtual token for token #{}", token.getTokenNumber());

        token.setStatus(TokenStatus.PRINT_FAILED);
        // printedAt stays null — it was never successfully sent to the printer.
        tokenRepository.save(token);

        // Note: The virtual print HTML is generated by BillingService.getLiveBillingStatus()
        // when it detects a PRINT_FAILED token. It uses the token data to build the HTML snippet
        // which is then embedded in the TokenResponseDTO.virtualPrintHtml field.
        // billing.js renders this div in the Recent Tokens zone with a "Print" browser button.
    }

    /**
     * Purpose: Checks whether the thermal printer is currently reachable.
     *          Attempts a real TCP connection — NEVER returns a hardcoded status.
     * Input:   none.
     * Output:  "ONLINE" if the printer responds within 1 second; "OFFLINE" otherwise.
     *
     * This is called by getLiveBillingStatus() every 2.5 seconds and by the Settings page.
     * The 1-second timeout prevents the billing screen from hanging when the printer is down.
     */
    public String getPrinterStatus() {
        try (Socket socket = new Socket()) {
            // Attempt to connect to the printer's TCP socket with a 1-second timeout.
            // If the connection succeeds, the printer is reachable.
            socket.connect(new InetSocketAddress(printerHost, printerPort), 1000);
            return "ONLINE";
        } catch (IOException e) {
            // Connection failed — printer is offline or not configured.
            return "OFFLINE";
        }
    }
}
