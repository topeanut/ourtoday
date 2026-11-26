package com.ourtoday.api.location;

import com.ourtoday.api.location.dto.LatLng;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolylineSimplifierTest {

    @Test
    void 일직선_중간점은_제거하고_양끝은_유지한다() {
        List<LatLng> in = List.of(new LatLng(0, 0), new LatLng(1, 1), new LatLng(2, 2), new LatLng(3, 3));
        assertThat(PolylineSimplifier.simplify(in, 0.0001)).containsExactly(new LatLng(0, 0), new LatLng(3, 3));
    }

    @Test
    void 꺾이는_점은_유지한다() {
        List<LatLng> in = List.of(new LatLng(0, 0), new LatLng(1, 0), new LatLng(1, 1));
        assertThat(PolylineSimplifier.simplify(in, 0.0001)).isEqualTo(in);
    }

    @Test
    void 점이_2개_이하면_그대로() {
        List<LatLng> one = List.of(new LatLng(0, 0));
        assertThat(PolylineSimplifier.simplify(one, 0.0001)).isEqualTo(one);
    }

    @Test
    void 인코딩_디코딩_왕복() {
        List<LatLng> in = List.of(new LatLng(37.1, 127.1), new LatLng(37.2, 127.2));
        assertThat(PolylineSimplifier.decode(PolylineSimplifier.encode(in))).isEqualTo(in);
        assertThat(PolylineSimplifier.decode("")).isEmpty();
    }
}
