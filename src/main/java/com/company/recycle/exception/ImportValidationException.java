package com.company.recycle.exception;

/**
 * 导入行级校验失败（整文件将回滚，不向 t_anomaly 写入）。
 */
public class ImportValidationException extends Exception {
    public ImportValidationException(String message) {
        super(message);
    }
}
