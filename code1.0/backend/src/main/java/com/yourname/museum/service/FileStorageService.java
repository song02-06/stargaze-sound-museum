package com.yourname.museum.service;

import com.yourname.museum.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** 音频落盘。要点：不信任用户文件名，统一「日期_UUID.扩展名」；防目录穿越。 */
@Service
public class FileStorageService {

    @Value("${museum.audio-root}")
    private String audioRoot;

    public String save(MultipartFile file, String subDir) {
        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        String ext = resolveExtension(file.getContentType(), originalName);
        Path dir = Paths.get(audioRoot, subDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            String name = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path target = dir.resolve(name).normalize();
            if (!target.startsWith(dir)) {
                throw new BizException("非法路径");
            }
            file.transferTo(target.toFile());
            return subDir + "/" + name;
        } catch (IOException e) {
            throw new BizException(5001, "音频保存失败：" + e.getMessage());
        }
    }

    public Path resolve(String relativePath) {
        return Paths.get(audioRoot).toAbsolutePath().normalize().resolve(relativePath).normalize();
    }

    private String resolveExtension(String contentType, String originalName) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.equals("audio/wav") || ct.equals("audio/x-wav") || ct.equals("audio/wave")) {
            return ".wav";
        }
        if (ct.equals("audio/webm")) {
            return ".webm";
        }
        if (ct.equals("audio/ogg")) {
            return ".ogg";
        }
        if (ct.equals("audio/mpeg") || ct.equals("audio/mp3")) {
            return ".mp3";
        }
        // 浏览器给的 contentType 有时不可靠，退回到看扩展名
        String lower = originalName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".wav")) {
            return ".wav";
        }
        if (lower.endsWith(".webm")) {
            return ".webm";
        }
        if (lower.endsWith(".ogg")) {
            return ".ogg";
        }
        if (lower.endsWith(".mp3")) {
            return ".mp3";
        }
        throw new BizException("不支持的音频格式：" + contentType);
    }
}
