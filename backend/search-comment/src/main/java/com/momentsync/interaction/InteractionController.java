package com.momentsync.interaction;

import org.springframework.web.bind.annotation.*;
import static com.momentsync.interaction.Models.*;

@RestController
@RequestMapping("/api/posts")
public class InteractionController {
    private final InteractionService service;
    private final CurrentUserProvider users;
    public InteractionController(InteractionService service, CurrentUserProvider users) {
        this.service = service; this.users = users;
    }
    @GetMapping("/search")
    public Envelope<PageResult<Post>> search(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long tagId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        users.requireUserId();
        return Envelope.ok(service.search(new SearchQuery(keyword, tagId, page, pageSize)));
    }
    @GetMapping("/{id}/comments")
    public Envelope<PageResult<Comment>> comments(@PathVariable long id, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        users.requireUserId();
        return Envelope.ok(service.comments(id, page, pageSize));
    }
    @PostMapping("/{id}/comments")
    public Envelope<Comment> publish(@PathVariable long id, @RequestBody CreateComment request) {
        return Envelope.ok(service.publish(id, users.requireUserId(), request.content()));
    }
}
