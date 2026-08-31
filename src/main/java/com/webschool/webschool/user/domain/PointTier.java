package com.webschool.webschool.user.domain;

// 포인트/티어 시스템(todo.md 요구사항) - 누적 포인트 구간으로 티어를 정하는 순수 함수 스타일 enum.
// User에 tier를 별도 컬럼으로 저장/캐싱하지 않고 매번 points에서 계산하는 이유: 포인트 소비(상점)와
// 제재로 인한 차감(UserPointService.deductForPenalty())으로 points가 줄어들 수도 있다 - tier를
// 저장해두면 그 변경마다 재계산 로직을 별도로 챙겨야 하지만, 계산식으로 두면 points만 바뀌면 tier는
// 항상 자동으로 맞다.
// 등급 이름은 게임 랭크 시스템(아이언~마스터) 테마로 확정(사용자 지시, 2026-08-27).
public enum PointTier {
    IRON(0, "아이언"),
    BRONZE(150, "브론즈"),
    SILVER(450, "실버"),
    GOLD(1200, "골드"),
    PLATINUM(2400, "플래티넘"),
    DIAMOND(4500, "다이아"),
    MASTER(9000, "마스터");

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
        PointTier result = IRON;
        for (PointTier tier : values()) {
            if (points >= tier.minPoints) {
                result = tier;
            }
        }
        return result;
    }
}
