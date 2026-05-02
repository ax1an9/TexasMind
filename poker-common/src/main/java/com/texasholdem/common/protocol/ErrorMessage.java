package com.texasholdem.common.protocol;

public class ErrorMessage extends ServerMessage {
    private String message;
    private String errorCode;

    public ErrorMessage() {
        super("ERROR");
    }

    public ErrorMessage(String message, String errorCode) {
        super("ERROR");
        this.message = message;
        this.errorCode = errorCode;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
