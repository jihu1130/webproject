package com.webschool.webschool.global.logging;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// 서버 에러(ERROR 레벨) 로그를 메모리에만 보관하는 원형 버퍼 - DB 테이블/마이그레이션 없이
// "방금 무슨 에러가 났었는지"를 관리자 화면에서 바로 확인하는 용도라, 서버가 재시작되면
// 비워지는 걸 감수하고 최근 MAX_ENTRIES개만 유지한다(사용자 요청 - "간단한 에러 로그").
@Component
public class ErrorLogBuffer {

    private static final int MAX_ENTRIES = 300;

    private final Deque<ErrorLogEntry> entries = new ArrayDeque<>();

    public synchronized void add(ErrorLogEntry entry) {
        entries.addFirst(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    public synchronized List<ErrorLogEntry> getAll() {
        return new ArrayList<>(entries);
    }

    public synchronized void clear() {
        entries.clear();
    }
}
