package com.company.recycle.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文件哈希工具类
 */
public class FileHashUtil {
    
    /**
     * 计算文件的 SHA-256 哈希值
     */
    public static String sha256(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] hashBytes = digest.digest();
            
            // 转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
