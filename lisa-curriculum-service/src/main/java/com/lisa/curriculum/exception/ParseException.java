package com.lisa.curriculum.exception;
public class ParseException extends RuntimeException {
    public ParseException(String msg, Throwable cause) { super(msg, cause); }
    public ParseException(String msg) { super(msg); }
}
