package com.studyroom.ai;

import java.util.List;

public record RAGResult(String answer, List<String> sources) {
}
