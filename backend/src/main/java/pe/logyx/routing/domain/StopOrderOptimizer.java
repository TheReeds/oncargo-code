package pe.logyx.routing.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Orden de visita de menor costo (TSP de camino, dirigido) sobre una matriz de costos, con partida y llegada fijas
 * opcionales y precedencias (una entrega va después de su recojo).
 * <ul>
 *   <li>Hasta {@link #EXACT_LIMIT} nodos: Held-Karp exacto, O(2ⁿ·n²) — 13 nodos ≈ 1.4 M pasos, &lt; 10 ms.</li>
 *   <li>Más nodos: vecino más cercano factible + mejora local (reubicar un nodo y 2-opt) mientras mejore.</li>
 * </ul>
 * Java puro, sin estado.
 */
public final class StopOrderOptimizer {

    public static final int EXACT_LIMIT = 13;
    private static final int MAX_IMPROVEMENT_ROUNDS = 200;

    private StopOrderOptimizer() {}

    /** {@code order}: índices de nodo en orden de visita (todos, una vez); {@code cost}: suma de tramos. */
    public record Result(int[] order, double cost, boolean exact) {}

    /**
     * @param cost        matriz n×n (no negativa; puede ser asimétrica)
     * @param start       nodo de partida fijo o -1
     * @param end         nodo de llegada fijo o -1
     * @param predecessor {@code predecessor[j]} = nodo que debe visitarse antes que j, o -1 (null = sin precedencias)
     */
    public static Result solve(double[][] cost, int start, int end, int[] predecessor) {
        int n = cost.length;
        if (n == 0) return new Result(new int[0], 0, true);
        int[] pred = predecessor != null ? predecessor : filled(n, -1);
        validate(cost, start, end, pred);
        if (n == 1) return new Result(new int[]{0}, 0, true);
        return n <= EXACT_LIMIT ? heldKarp(cost, start, end, pred) : heuristic(cost, start, end, pred);
    }

    // ------------------------------------------------------------------ exacto

    private static Result heldKarp(double[][] c, int start, int end, int[] pred) {
        int n = c.length;
        int full = (1 << n) - 1;
        double[][] dp = new double[1 << n][n];
        int[][] parent = new int[1 << n][n];
        for (double[] row : dp) Arrays.fill(row, Double.POSITIVE_INFINITY);
        for (int j = 0; j < n; j++) {
            boolean canStart = start >= 0 ? j == start : pred[j] < 0 && j != end;
            if (canStart) {
                dp[1 << j][j] = 0;
                parent[1 << j][j] = -1;
            }
        }
        for (int mask = 1; mask <= full; mask++) {
            for (int j = 0; j < n; j++) {
                double base = dp[mask][j];
                if (base == Double.POSITIVE_INFINITY || (mask & (1 << j)) == 0) continue;
                for (int k = 0; k < n; k++) {
                    if ((mask & (1 << k)) != 0) continue;
                    if (pred[k] >= 0 && (mask & (1 << pred[k])) == 0) continue;
                    int next = mask | (1 << k);
                    if (k == end && next != full) continue;
                    double v = base + c[j][k];
                    if (v < dp[next][k]) {
                        dp[next][k] = v;
                        parent[next][k] = j;
                    }
                }
            }
        }
        int last = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int j = 0; j < n; j++) {
            if (end >= 0 && j != end) continue;
            if (dp[full][j] < best) {
                best = dp[full][j];
                last = j;
            }
        }
        if (last < 0) throw new IllegalArgumentException("Restricciones de orden imposibles de cumplir");
        int[] order = new int[n];
        int mask = full;
        for (int pos = n - 1, j = last; pos >= 0; pos--) {
            order[pos] = j;
            int p = parent[mask][j];
            mask &= ~(1 << j);
            j = p;
        }
        return new Result(order, best, true);
    }

    // ------------------------------------------------------------------ heurística

    private static Result heuristic(double[][] c, int start, int end, int[] pred) {
        int n = c.length;
        boolean[] seen = new boolean[n];
        List<Integer> order = new ArrayList<>(n);
        int current = start;
        if (current < 0) {
            for (int j = 0; j < n && current < 0; j++) if (pred[j] < 0 && j != end) current = j;
        }
        order.add(current);
        seen[current] = true;
        while (order.size() < n) {
            int bestK = -1;
            for (int k = 0; k < n; k++) {
                if (seen[k] || (pred[k] >= 0 && !seen[pred[k]])) continue;
                if (k == end && order.size() < n - 1) continue;
                if (bestK < 0 || c[current][k] < c[current][bestK]) bestK = k;
            }
            if (bestK < 0) throw new IllegalArgumentException("Restricciones de orden imposibles de cumplir");
            order.add(bestK);
            seen[bestK] = true;
            current = bestK;
        }
        int[] best = order.stream().mapToInt(Integer::intValue).toArray();
        double bestCost = cost(c, best);
        int lo = 1;                         // con o sin partida fija, se deja quieto el primero solo si es fijo
        if (start < 0) lo = 0;
        int hi = end >= 0 ? n - 2 : n - 1;  // último índice movible
        boolean improved = true;
        for (int round = 0; improved && round < MAX_IMPROVEMENT_ROUNDS; round++) {
            improved = false;
            // Reubicar un nodo.
            for (int i = lo; i <= hi && !improved; i++) {
                for (int j = lo; j <= hi && !improved; j++) {
                    if (i == j) continue;
                    int[] cand = relocate(best, i, j);
                    double v = cost(c, cand);
                    if (v + 1e-9 < bestCost && feasible(cand, start, end, pred)) {
                        best = cand;
                        bestCost = v;
                        improved = true;
                    }
                }
            }
            // 2-opt: invertir un tramo.
            for (int i = lo; i < hi && !improved; i++) {
                for (int j = i + 1; j <= hi && !improved; j++) {
                    int[] cand = reverse(best, i, j);
                    double v = cost(c, cand);
                    if (v + 1e-9 < bestCost && feasible(cand, start, end, pred)) {
                        best = cand;
                        bestCost = v;
                        improved = true;
                    }
                }
            }
        }
        return new Result(best, bestCost, false);
    }

    static double cost(double[][] c, int[] order) {
        double sum = 0;
        for (int i = 1; i < order.length; i++) sum += c[order[i - 1]][order[i]];
        return sum;
    }

    static boolean feasible(int[] order, int start, int end, int[] pred) {
        if (start >= 0 && order[0] != start) return false;
        if (end >= 0 && order[order.length - 1] != end) return false;
        int[] pos = new int[order.length];
        for (int i = 0; i < order.length; i++) pos[order[i]] = i;
        for (int j = 0; j < pred.length; j++) if (pred[j] >= 0 && pos[pred[j]] > pos[j]) return false;
        return true;
    }

    private static int[] relocate(int[] order, int from, int to) {
        List<Integer> list = new ArrayList<>(order.length);
        for (int v : order) list.add(v);
        int node = list.remove(from);
        list.add(to, node);
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] reverse(int[] order, int i, int j) {
        int[] out = order.clone();
        for (int a = i, b = j; a < b; a++, b--) {
            int t = out[a];
            out[a] = out[b];
            out[b] = t;
        }
        return out;
    }

    private static void validate(double[][] cost, int start, int end, int[] pred) {
        int n = cost.length;
        for (double[] row : cost) {
            if (row.length != n) throw new IllegalArgumentException("La matriz de costos debe ser cuadrada");
        }
        if (pred.length != n) throw new IllegalArgumentException("predecessor debe tener un valor por nodo");
        if (start >= n || end >= n || (start >= 0 && start == end && n > 1)) {
            throw new IllegalArgumentException("Partida/llegada fuera de rango");
        }
        if (start >= 0 && pred[start] >= 0) throw new IllegalArgumentException("La partida no puede tener predecesor");
    }

    private static int[] filled(int n, int v) {
        int[] a = new int[n];
        Arrays.fill(a, v);
        return a;
    }
}
