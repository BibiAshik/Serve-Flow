package com.serveflow.exception;

/**
 * InvalidDomainException — thrown when a student authenticates with a Google account
 * that does not belong to the allowed college email domain (@sairamtap.edu.in).
 *
 * Thrown by OAuth2SuccessHandler after extracting and checking the email domain.
 * The handler redirects to /student/login?error=invalid-domain rather than
 * propagating this as an HTTP error, since it is a redirect flow.
 *
 * This exception class is kept for use in any future programmatic domain checks.
 */
public class InvalidDomainException extends RuntimeException {

    public InvalidDomainException(String message) {
        super(message);
    }
}
