package com.webschool.webschool.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// InMemoryErrorAppender/ErrorLogBuffer 단위 테스트 - 스프링 컨텍스트 없이 순수 logback 객체로
// "ERROR만 버퍼에 쌓이는지"/"버퍼가 MAX_ENTRIES를 넘지 않는지"만 확인한다(관리자 화면(/admin/error-log)
// 자체는 이미 브라우저로 확인했으므로, 여기서는 버퍼 로직만 회귀 테스트로 남겨둔다).
class ErrorLogBufferTest {

    private final Logger testLogger = (Logger) LoggerFactory.getLogger("ErrorLogBufferTest");

    @Test
    void errorLevelIsCaptured() {
        ErrorLogBuffer buffer = new ErrorLogBuffer();
        InMemoryErrorAppender appender = new InMemoryErrorAppender(buffer);
        appender.setContext((LoggerContext) testLogger.getLoggerContext());
        appender.start();

        appender.doAppend(new LoggingEvent(Logger.class.getName(), testLogger, Level.ERROR, "테스트 에러", null, null));

        assertEquals(1, buffer.getAll().size());
        assertEquals("테스트 에러", buffer.getAll().get(0).getMessage());
    }

    @Test
    void belowErrorLevelIsIgnored() {
        ErrorLogBuffer buffer = new ErrorLogBuffer();
        InMemoryErrorAppender appender = new InMemoryErrorAppender(buffer);
        appender.setContext((LoggerContext) testLogger.getLoggerContext());
        appender.start();

        appender.doAppend(new LoggingEvent(Logger.class.getName(), testLogger, Level.WARN, "경고는 무시", null, null));

        assertTrue(buffer.getAll().isEmpty());
    }

    @Test
    void bufferKeepsOnlyMostRecentEntries() {
        ErrorLogBuffer buffer = new ErrorLogBuffer();
        for (int i = 0; i < 305; i++) {
            buffer.add(ErrorLogEntry.builder().message("에러 " + i).level("ERROR").build());
        }

        assertEquals(300, buffer.getAll().size());
        assertEquals("에러 304", buffer.getAll().get(0).getMessage()); // 최신이 맨 앞
    }
}
