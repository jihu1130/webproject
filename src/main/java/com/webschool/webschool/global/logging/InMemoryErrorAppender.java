package com.webschool.webschool.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// ERROR 레벨 로그를 기존 콘솔/파일 로깅(logs/webschool.log)과 별개로 ErrorLogBuffer에도 남겨서
// 관리자 화면(/admin/error-log)에서 로그 파일을 직접 열어보지 않고도 최근 서버 에러를 바로 확인할
// 수 있게 한다. 이 프로젝트는 logback-spring.xml 없이 application.yml의 기본 로깅 설정만 쓰므로,
// XML로 어펜더를 추가하는 대신 스프링 빈이 뜰 때 root logger에 프로그래밍 방식으로 붙인다 - 기존
// 어펜더는 건드리지 않고 하나 추가만 하는 것이라 기존 로깅 동작에는 영향이 없다.
@Component
@RequiredArgsConstructor
public class InMemoryErrorAppender extends AppenderBase<ILoggingEvent> {

    private final ErrorLogBuffer errorLogBuffer;

    @PostConstruct
    public void register() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        setContext(rootLogger.getLoggerContext());
        start();
        rootLogger.addAppender(this);
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(Level.ERROR)) {
            return;
        }
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        errorLogBuffer.add(ErrorLogEntry.builder()
                .timestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault()))
                .level(event.getLevel().toString())
                .loggerName(event.getLoggerName())
                .message(event.getFormattedMessage())
                .stackTrace(throwableProxy != null ? ThrowableProxyUtil.asString(throwableProxy) : null)
                .build());
    }
}
