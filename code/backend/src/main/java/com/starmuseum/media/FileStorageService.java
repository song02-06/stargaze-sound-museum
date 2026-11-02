package com.starmuseum.media;

import com.starmuseum.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 音频落盘。
 *
 * 两条原则：
 * 1）**不信用户文件名** —— 统一用日期 + UUID 命名，只在展示时用中文标题；
 * 2）**防目录穿越** —— 目标路径必须仍在根目录内。
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path root;

    public FileStorageService(@Value("${museum.audio-root}") String audioRoot) {
        this.root = Paths.get(audioRoot).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** @return 相对路径，如 exhibits/20260918_ab12cd34.wav */
    public String save(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BizException("音频是空的");
        }
        String ext = extensionFor(file.getContentType(), file.getOriginalFilename());

        Path dir = root.resolve(subDir).normalize();
        if (!dir.startsWith(root)) {
            throw new BizException("非法的存储路径");
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException("创建音频目录失败");
        }

        String name = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new BizException("非法的存储路径");
        }

        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new BizException("保存音频失败");
        }
        return subDir + "/" + name;
    }

    public Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) {
            throw new BizException("非法的音频路径");
        }
        return p;
    }

    /**
     * 删除一条已经落盘的音频。
     *
     * 找不到就当删过了 —— 删两次不该报错。
     * 删不掉也不算失败：数据库里的那条已经没了，磁盘上剩个文件不影响任何人。
     */
    public void delete(String relative) {
        if (relative == null || relative.isBlank()) {
            return;
        }
        Path target = resolve(relative);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("音频没能从磁盘上删掉：{}（{}）", relative, e.getMessage());
        }
    }

    private static String extensionFor(String contentType, String originalName) {
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("wav") || ct.contains("x-wav") || ct.contains("wave")) {
                return ".wav";
            }
            if (ct.contains("webm")) {
                return ".webm";
            }
            if (ct.contains("ogg")) {
                return ".ogg";
            }
            if (ct.contains("mpeg") || ct.contains("mp3")) {
                return ".mp3";
            }
        }
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot > 0 && originalName.length() - dot <= 6) {
                return originalName.substring(dot).toLowerCase();
            }
        }
        throw new BizException("不支持的音频格式");
    }
}
