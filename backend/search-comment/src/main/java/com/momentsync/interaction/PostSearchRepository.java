package com.momentsync.interaction;

public interface PostSearchRepository {
    Models.PageResult<Models.Post> search(SearchQuery query);
}
