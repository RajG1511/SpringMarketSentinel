package com.raj.springmarketanalysis.api;

public class ApiExceptions {

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException(String message) { super(message); }
    }

    public static class UpstreamException extends RuntimeException {
        public UpstreamException(String message) { super(message); }
    }
}
