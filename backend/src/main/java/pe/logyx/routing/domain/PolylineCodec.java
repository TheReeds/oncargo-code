package pe.logyx.routing.domain;

import pe.logyx.routing.domain.model.LatLng;

import java.util.ArrayList;
import java.util.List;

/** Polyline codificada (algoritmo de Google; precisión 5 en el dataset, como espera la web). */
public final class PolylineCodec {

    private PolylineCodec() {}

    public static List<LatLng> decode(String encoded, int precision) {
        double factor = Math.pow(10, precision);
        List<LatLng> out = new ArrayList<>();
        int index = 0;
        long lat = 0;
        long lng = 0;
        while (index < encoded.length()) {
            long[] delta = new long[2];
            for (int k = 0; k < 2; k++) {
                long result = 0;
                int shift = 0;
                int b;
                do {
                    if (index >= encoded.length()) throw new IllegalArgumentException("Polyline truncada");
                    b = encoded.charAt(index++) - 63;
                    result |= (long) (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                delta[k] = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
            }
            lat += delta[0];
            lng += delta[1];
            out.add(new LatLng(lat / factor, lng / factor));
        }
        return out;
    }

    public static String encode(List<LatLng> points, int precision) {
        double factor = Math.pow(10, precision);
        StringBuilder sb = new StringBuilder();
        long prevLat = 0;
        long prevLng = 0;
        for (LatLng p : points) {
            long lat = Math.round(p.lat() * factor);
            long lng = Math.round(p.lng() * factor);
            write(sb, lat - prevLat);
            write(sb, lng - prevLng);
            prevLat = lat;
            prevLng = lng;
        }
        return sb.toString();
    }

    private static void write(StringBuilder sb, long delta) {
        long v = delta < 0 ? ~(delta << 1) : delta << 1;
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }
}
