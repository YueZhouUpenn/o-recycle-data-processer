package com.company.recycle.exception;

/**
 * 重复导入异常
 */
public class DuplicateImportException extends Exception {
    public DuplicateImportException(String message) {
        super(message);
    }
}
