package com.ourtoday.api.couple;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 6자리 대문자+숫자. 헷갈리는 0/O/1/I 제외
 */
@Component
public class InviteCodeGenerator {

    static final int LENGTH = 6;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
