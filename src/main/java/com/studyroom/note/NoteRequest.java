package com.studyroom.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NoteRequest(
        @Size(max = 200, message = "标题不能超过 200 字")
        String title,

        @Size(max = 50, message = "分类不能超过 50 字")
        String category,

        @Size(max = 5, message = "标签最多 5 个")
        List<@Size(max = 30, message = "单个标签不超过 30 字") String> tags,

        @NotBlank(message = "笔记内容不能为空")
        @Size(max = 5000, message = "笔记不能超过 5000 字")
        String content) {
}
