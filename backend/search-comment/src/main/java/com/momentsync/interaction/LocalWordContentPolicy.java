package com.momentsync.interaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.Resource;

public final class LocalWordContentPolicy implements ContentPolicy {
    private final List<String> words;
    public LocalWordContentPolicy(Resource resource) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            words = reader.lines().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .map(LocalWordContentPolicy::normalize).filter(s -> !s.isEmpty()).distinct().toList();
        }
        if (words.isEmpty()) throw new IllegalArgumentException("敏感词表不能为空");
    }
    private static String normalize(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Z}\\s\\p{Cf}]+", "");
    }
    @Override public void check(String text) {
        String normalized = normalize(text == null ? "" : text);
        if (words.stream().anyMatch(normalized::contains))
            throw new InteractionException(422, 42201, "内容含有不适宜词语，请修改后重试");
    }
}
