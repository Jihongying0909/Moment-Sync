package com.momentsync.admin;

import com.momentsync.web.ApiException;
import com.momentsync.web.ApiResponse;
import com.momentsync.web.PageRequest;
import com.momentsync.web.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
class AdminController {
    private final AdminRepository admin;

    AdminController(AdminRepository admin) {
        this.admin = admin;
    }

    @GetMapping("/users")
    ApiResponse<PageResult<AdminRepository.UserRow>> users(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String query = keyword.strip();
        if (query.codePointCount(0, query.length()) > 100) {
            throw ApiException.invalid("关键词最多 100 个字符");
        }
        if (status != null && !status.equals("ACTIVE") && !status.equals("DISABLED")) {
            throw ApiException.invalid("账号状态须为 ACTIVE 或 DISABLED");
        }
        return ApiResponse.success(admin.users(query, status, new PageRequest(page, pageSize)));
    }

    @GetMapping("/posts")
    ApiResponse<PageResult<AdminRepository.PostRow>> posts(@RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (visibility != null && !visibility.equals("PUBLIC") && !visibility.equals("PRIVATE")) {
            throw ApiException.invalid("可见性须为 PUBLIC 或 PRIVATE");
        }
        if (status != null && !status.equals("PUBLISHED") && !status.equals("OFFLINE")) {
            throw ApiException.invalid("印记状态须为 PUBLISHED 或 OFFLINE");
        }
        return ApiResponse.success(admin.posts(visibility, status, new PageRequest(page, pageSize)));
    }
}
