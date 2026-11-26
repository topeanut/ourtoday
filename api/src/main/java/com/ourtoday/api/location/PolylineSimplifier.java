package com.ourtoday.api.location;

import com.ourtoday.api.location.dto.LatLng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Douglas-Peucker 경로 단순화 + "lat,lng;lat,lng" 문자열 인코딩. 거리는 도(degree) 평면 근사.
 */
final class PolylineSimplifier {

    private PolylineSimplifier() {
    }

    static List<LatLng> simplify(List<LatLng> points, double epsilon) {
        int n = points.size();
        if (n <= 2) {
            return List.copyOf(points);
        }
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        mark(points, 0, n - 1, epsilon, keep);
        List<LatLng> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                out.add(points.get(i));
            }
        }
        return out;
    }

    private static void mark(List<LatLng> pts, int first, int last, double epsilon, boolean[] keep) {
        double max = 0;
        int index = -1;
        for (int i = first + 1; i < last; i++) {
            double d = distance(pts.get(i), pts.get(first), pts.get(last));
            if (d > max) {
                max = d;
                index = i;
            }
        }
        if (index != -1 && max > epsilon) {
            keep[index] = true;
            mark(pts, first, index, epsilon, keep);
            mark(pts, index, last, epsilon, keep);
        }
    }

    private static double distance(LatLng p, LatLng a, LatLng b) {
        double dx = b.lng() - a.lng();
        double dy = b.lat() - a.lat();
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) {
            return Math.hypot(p.lng() - a.lng(), p.lat() - a.lat());
        }
        return Math.abs(dy * (p.lng() - a.lng()) - dx * (p.lat() - a.lat())) / Math.sqrt(len2);
    }

    static String encode(List<LatLng> points) {
        return points.stream().map(p -> p.lat() + "," + p.lng()).collect(Collectors.joining(";"));
    }

    static List<LatLng> decode(String polyline) {
        if (polyline == null || polyline.isBlank()) {
            return List.of();
        }
        return Arrays.stream(polyline.split(";"))
            .map(s -> s.split(","))
            .map(c -> new LatLng(Double.parseDouble(c[0]), Double.parseDouble(c[1])))
            .toList();
    }
}
