package com.momentsync.interaction;

/** 印记发布负责人可注入此接口，对标题、正文分别调用 check，无需依赖评论内部实现。 */
@FunctionalInterface
public interface ContentPolicy {
    void check(String text);
}
