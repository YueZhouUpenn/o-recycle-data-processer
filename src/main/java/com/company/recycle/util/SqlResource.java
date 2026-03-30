package com.company.recycle.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从 classpath 加载 SQL 资源文件。
 */
public final class SqlResource {

    private SqlResource() {
    }

    public static String loadUtf8(String classpathRelative) throws IOException {
        try (InputStream is = SqlResource.class.getClassLoader().getResourceAsStream(classpathRelative)) {
            if (is == null) {
                throw new IOException("Resource not found: " + classpathRelative);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
