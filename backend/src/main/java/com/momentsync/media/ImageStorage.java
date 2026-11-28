package com.momentsync.media;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import com.momentsync.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageStorage {
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000;
    private final Path directory;
    private final String publicBaseUrl;

    public ImageStorage(@Value("${moment.media.directory:./uploads}") String directory,
            @Value("${moment.media.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        URI uri = URI.create(publicBaseUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("图片公共地址必须是无凭据、查询参数或片段的 HTTP(S) 地址");
        }
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        if (this.publicBaseUrl.length() > 400) throw new IllegalArgumentException("图片公共地址过长");
    }

    public String save(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw ApiException.invalid("请选择一张图片");
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        BufferedImage image;
        String format;
        // 检查解码器识别的真实格式，不信任文件扩展名或客户端 Content-Type。
        try (var input = new MemoryCacheImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw ApiException.invalid("仅支持有效的 JPEG 或 PNG 图片");
            var reader = readers.next();
            try {
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw ApiException.invalid("仅支持 JPEG 或 PNG 图片");
                }
                reader.setInput(input, true, true);
                long width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width * height > MAX_PIXELS) {
                    throw ApiException.invalid("图片尺寸不能超过 2000 万像素");
                }
                image = reader.read(0);
                if (image == null) throw ApiException.invalid("图片已损坏，请重新选择");
            } finally {
                reader.dispose();
            }
        } catch (IOException error) {
            throw ApiException.invalid("图片已损坏或无法读取，请重新选择");
        }
        Files.createDirectories(directory);
        String name = UUID.randomUUID() + (format.equals("jpeg") ? ".jpg" : ".png");
        Path target = directory.resolve(name);
        // 重新编码移除原始元数据、尾随内容，原文件名不参与路径生成。
        try {
            if (!ImageIO.write(image, format, target.toFile())) throw new IOException("没有可用的图片编码器");
            if (Files.size(target) > MAX_BYTES) throw tooLarge();
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(target);
            throw error;
        }
        return publicBaseUrl + "/api/files/images/" + name;
    }

    public Path find(String name) {
        if (!name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|png)")) {
            throw missing();
        }
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw missing();
        return file;
    }

    private static ApiException missing() { return new ApiException(404, 40401, "图片不存在"); }
    private static ApiException tooLarge() { return new ApiException(413, 41301, "单张图片不能超过 10MB"); }
}
