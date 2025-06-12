package com.cesco.scheduly.exception;

public class MandatoryCourseConflictException extends IllegalArgumentException {
    public MandatoryCourseConflictException(String message) {
        super(message);
    }
}