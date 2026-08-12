package com.studyroom.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomRequest(
        @NotBlank(message = "房间名不能为空")
        @Size(max = 50, message = "房间名不能超过 50 个字符")
        String name,

        @Size(max = 30, message = "标签不能超过 30 个字符")
        String category,

        @Size(max = 100, message = "密码不能超过 100 个字符")
        String password,

        @Size(max = 1000, message = "公告不能超过 1000 个字符")
        String announcement,

        Integer focusMinutes,

        Integer breakMinutes,

        Boolean aiTutorEnabled,

        @Size(max = 200, message = "助教人设不能超过 200 字")
        String tutorPersona,

        Integer weeklyGoalMinutes) {
}
