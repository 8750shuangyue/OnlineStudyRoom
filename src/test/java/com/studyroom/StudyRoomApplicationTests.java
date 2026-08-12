package com.studyroom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:contextdb;DB_CLOSE_DELAY=-1")
class StudyRoomApplicationTests {

    @Test
    void contextLoads() {
    }
}
