package com.company.recycle.exception;

/**
 * 列缺失异常
 */
public class ColumnMissingException extends Exception {
    public ColumnMissingException(String message) {
        super(message);
    }
}
