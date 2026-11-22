package com.ourtoday.api.push;

/**
 * 푸시 한 건의 내용. path 는 앱에서 탭했을 때 열 화면 경로, type 은 알림 종류
 */
public record PushMessage(String body, String path, String type) {
}
