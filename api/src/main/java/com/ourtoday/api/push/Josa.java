package com.ourtoday.api.push;

/** 한글 조사 선택 */
public final class Josa {

    private static final char HANGUL_FIRST = '가';
    private static final char HANGUL_LAST = '힣';
    private static final int JONGSEONG_COUNT = 28;

    private Josa() {
    }

    /** 마지막 글자가 한글이면 받침 있음 "이" / 없음 "가", 한글이 아니면 "이(가)" */
    public static String iGa(String name) {
        if (name == null || name.isEmpty()) {
            return "이(가)";
        }
        char last = name.charAt(name.length() - 1);
        if (last < HANGUL_FIRST || last > HANGUL_LAST) {
            return "이(가)";
        }
        return (last - HANGUL_FIRST) % JONGSEONG_COUNT == 0 ? "가" : "이";
    }
}
