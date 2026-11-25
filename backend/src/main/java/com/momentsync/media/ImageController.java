package com.momentsync.media;

import java.io.IOException;
import com.momentsync.auth.CurrentUserProvider;
import com.momentsync.web.ApiException;
import com.momentsync.web.ApiResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/files/images")
public class ImageController {
    private final ImageStorage storage;
    private final CurrentUserProvider users;

    public ImageController(ImageStorage storage, CurrentUserProvider users) {
        this.storage = storage;
        this.users = users;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadedImage> upload(MultipartHttpServletRequest request) throws IOException {
        users.requireUserId();
        var files = request.getMultiFileMap();
        if (files.size() != 1 || !files.containsKey("file") || files.get("file").size() != 1) {
            throw ApiException.invalid("请通过 file 字段上传一张图片");
        }
        return ApiResponse.success(new UploadedImage(storage.save(files.getFirst("file"))));
    }

    // 微信 image 组件无法附带 Bearer 头；只有生成的图片地址允许匿名读取。
    @GetMapping("/{name}")
    public ResponseEntity<Resource> image(@PathVariable String name) {
        var file = storage.find(name);
        return ResponseEntity.ok()
                .contentType(name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    public record UploadedImage(String imageUrl) {}
}
