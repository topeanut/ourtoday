package com.ourtoday.api.push;

import java.util.Map;

/**
 * 알림 없이 앱만 깨우는 무음(data-only) 푸시. type 은 data 의 "type" 키로 함께 실린다
 */
public record DataPush(String type, Map<String, String> data) {
}
