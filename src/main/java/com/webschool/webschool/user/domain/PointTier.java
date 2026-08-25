package com.webschool.webschool.user.domain;

// 포인트/티어 시스템(todo.md 요구사항) - 누적 포인트 구간으로 티어를 정하는 순수 함수 스타일 enum.
// User에 tier를 별도 컬럼으로 저장/캐싱하지 않고 매번 points에서 계산하는 이유: 지금은 포인트가
// 오르기만 하지만(소비 기능 미구현, 사용자 확정), 나중에 소비 기능이나 비활동 감점(하락)이
// 추가되면 points가 줄어들 수 있다 - tier를 저장해두면 그 변경마다 재계산 로직을 별도로 챙겨야
// 하지만, 계산식으로 두면 points만 바뀌면 tier는 항상 자동으로 맞다.
// 등급 이름은 임시값 - 사용자가 나중에 직접 정한 이름으로 교체할 예정(라벨 문자열만 바꾸면 됨).
public enum PointTier {
    NEWBIE(0, "새내기"),
    CLASS_REP_CANDIDATE(50, "반장 후보"),
    CLASS_REP(150, "반장"),
    STUDENT_COUNCIL(400, "학생회 임원"),
    SCHOOL_PRESIDENT(1000, "전교 회장");

    private final int minPoints;
    private final String label;

    PointTier(int minPoints, String label) {
        this.minPoints = minPoints;
        this.label = label;
    }

    public int getMinPoints() {
        return minPoints;
    }

    public String getLabel() {
        return label;
    }

    // values()는 선언 순서(임계값 오름차순)를 그대로 유지하므로, 포인트 이상인 마지막 등급을
    // 찾으면 된다.
    public static PointTier forPoints(int points) {
        PointTier result = NEWBIE;
        for (PointTier tier : values()) {
            if (points >= tier.minPoints) {
                result = tier;
            }
        }
        return result;
    }
}
