package pe.logyx.catalog;

import java.util.Arrays;
import java.util.Optional;

/**
 * Ciudades del corredor Lima–Sierra Sur (alcance del MVP) con coordenadas
 * aproximadas del centro urbano. El motor de costos usa estas coordenadas
 * cuando no hay geocodificación de la dirección exacta.
 */
public enum City {
    LIMA("Lima", -12.0464, -77.0428),
    ICA("Ica", -14.0678, -75.7286),
    NAZCA("Nazca", -14.8309, -74.9389),
    AREQUIPA("Arequipa", -16.4090, -71.5375),
    MOQUEGUA("Moquegua", -17.1956, -70.9353),
    TACNA("Tacna", -18.0146, -70.2536),
    JULIACA("Juliaca", -15.5000, -70.1333),
    PUNO("Puno", -15.8402, -70.0219),
    CUSCO("Cusco", -13.5320, -71.9675),
    ABANCAY("Abancay", -13.6339, -72.8814),
    AYACUCHO("Ayacucho", -13.1588, -74.2239);

    public final String label;
    public final double lat;
    public final double lng;

    City(String label, double lat, double lng) {
        this.label = label;
        this.lat = lat;
        this.lng = lng;
    }

    public static Optional<City> parse(String value) {
        return Arrays.stream(values()).filter(c -> c.name().equalsIgnoreCase(value)).findFirst();
    }
}
