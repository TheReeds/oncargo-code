package pe.logyx.routing.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopOrderOptimizerTest {

    @Test
    void heldKarpIgualaAFuerzaBrutaConPartidaLlegadaYPrecedencias() {
        Random rnd = new Random(42);
        for (int trial = 0; trial < 60; trial++) {
            int n = 2 + rnd.nextInt(6);
            double[][] c = randomMatrix(rnd, n);
            int start = rnd.nextBoolean() ? 0 : -1;
            int end = n > 2 && rnd.nextBoolean() ? n - 1 : -1;
            int[] pred = new int[n];
            Arrays.fill(pred, -1);
            // Pares recojo → entrega entre los nodos intermedios.
            for (int i = 1; i + 1 < n - 1; i += 2) if (rnd.nextBoolean()) pred[i + 1] = i;

            StopOrderOptimizer.Result r = StopOrderOptimizer.solve(c, start, end, pred);
            double brute = bruteForce(c, start, end, pred);
            assertTrue(r.exact());
            assertTrue(StopOrderOptimizer.feasible(r.order(), start, end, pred), "orden no factible");
            assertEquals(brute, r.cost(), 1e-6, "trial " + trial);
            assertEquals(StopOrderOptimizer.cost(c, r.order()), r.cost(), 1e-6);
        }
    }

    @Test
    void respetaLaPrecedenciaAunqueSeaMasLarga() {
        // 0 → 1 → 2 es barato, pero 2 debe ir antes que 1.
        double[][] c = {
                {0, 1, 10},
                {1, 0, 1},
                {10, 1, 0}};
        StopOrderOptimizer.Result r = StopOrderOptimizer.solve(c, 0, -1, new int[]{-1, 2, -1});
        assertArrayEquals(new int[]{0, 2, 1}, r.order());
        assertEquals(11, r.cost(), 1e-9);
    }

    @Test
    void heuristicaParaMuchasParadasDevuelveUnOrdenFactible() {
        Random rnd = new Random(7);
        int n = 22;
        double[][] c = randomMatrix(rnd, n);
        int[] pred = new int[n];
        Arrays.fill(pred, -1);
        for (int i = 1; i + 1 < n - 1; i += 2) pred[i + 1] = i;
        StopOrderOptimizer.Result r = StopOrderOptimizer.solve(c, 0, n - 1, pred);
        assertFalse(r.exact());
        assertEquals(n, Arrays.stream(r.order()).distinct().count());
        assertTrue(StopOrderOptimizer.feasible(r.order(), 0, n - 1, pred));
        // No peor que el orden trivial 0, 1, 2, … (factible por construcción).
        int[] identity = new int[n];
        for (int i = 0; i < n; i++) identity[i] = i;
        assertTrue(r.cost() <= StopOrderOptimizer.cost(c, identity) + 1e-9);
    }

    @Test
    void heuristicaSeAcercaAlOptimoEnInstanciasMedianas() {
        // Puntos en una recta: el óptimo es recorrerlos en orden (costo = 100). La heurística debe encontrarlo.
        int n = 16;
        double[] x = new double[n];
        Random rnd = new Random(3);
        for (int i = 1; i < n; i++) x[i] = rnd.nextDouble() * 100;
        x[0] = 0;
        double[][] c = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) c[i][j] = Math.abs(x[i] - x[j]);
        double max = Arrays.stream(x).max().orElseThrow();
        assertEquals(max, StopOrderOptimizer.solve(c, 0, -1, null).cost(), 1e-6);
    }

    @Test
    void restriccionesImposiblesFallan() {
        double[][] c = {{0, 1, 1}, {1, 0, 1}, {1, 1, 0}};
        // 1 después de 2 y 2 después de 1: ciclo.
        assertThrows(IllegalArgumentException.class, () -> StopOrderOptimizer.solve(c, 0, -1, new int[]{-1, 2, 1}));
        assertThrows(IllegalArgumentException.class, () -> StopOrderOptimizer.solve(c, 0, 0, null));
    }

    @Test
    void casosTriviales() {
        assertEquals(0, StopOrderOptimizer.solve(new double[0][0], -1, -1, null).order().length);
        assertArrayEquals(new int[]{0}, StopOrderOptimizer.solve(new double[][]{{0}}, 0, -1, null).order());
    }

    private static double[][] randomMatrix(Random rnd, int n) {
        double[][] c = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (i != j) c[i][j] = 1 + rnd.nextInt(500);
        return c;
    }

    private static double bruteForce(double[][] c, int start, int end, int[] pred) {
        int n = c.length;
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        double[] best = {Double.POSITIVE_INFINITY};
        permute(perm, 0, p -> {
            if (StopOrderOptimizer.feasible(p, start, end, pred)) best[0] = Math.min(best[0], StopOrderOptimizer.cost(c, p));
        });
        return best[0];
    }

    private static void permute(int[] a, int k, java.util.function.Consumer<int[]> visit) {
        if (k == a.length) {
            visit.accept(a);
            return;
        }
        for (int i = k; i < a.length; i++) {
            int t = a[k]; a[k] = a[i]; a[i] = t;
            permute(a, k + 1, visit);
            t = a[k]; a[k] = a[i]; a[i] = t;
        }
    }
}
