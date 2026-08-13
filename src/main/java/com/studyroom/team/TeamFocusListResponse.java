package com.studyroom.team;

import java.util.List;

public record TeamFocusListResponse(
        TeamFocusResponse active,
        List<TeamFocusResponse> recent) {
}
