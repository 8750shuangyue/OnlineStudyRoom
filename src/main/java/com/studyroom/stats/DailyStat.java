package com.studyroom.stats;

import java.time.LocalDate;

public record DailyStat(LocalDate date, long seconds) {
}
