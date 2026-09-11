package com.visitscotland.ccg.controller;

import com.visitscotland.ccg.exception.TraceApiException;
import com.visitscotland.ccg.exception.VsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**  The final version of the application would react to error and this method is unlikely to be required */
    @ExceptionHandler(TraceApiException.class)
    public ResponseEntity<String> handleException(TraceApiException exception){
        if (exception.isApiError()) {
            logger.error("{}\n\tCode: {}, message: {}", exception.getMessage(), exception.getStatusCode(),
                    exception.getApiMessage(), exception);
        } else {
            logger.error(exception.getMessage(), exception);
        }

        return ResponseEntity.status(exception.getStatusCode()).body(exception.getMessage());
    }

    /** If the application where to Respond with JSON Payloads This could be a good way to send non-200 responses */
    @ExceptionHandler(VsException.class)
    public ResponseEntity<String> handleException(VsException exception){
        logger.error("Error processing request: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.valueOf(500)).body("The service encountered an error. Please try again later. ");
    }
}
