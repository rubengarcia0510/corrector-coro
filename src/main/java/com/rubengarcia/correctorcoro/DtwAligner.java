package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic Time Warping simple sobre dos secuencias de pitch (en Hz).
 * Alinea la secuencia "ensayo" contra la secuencia "referencia" aunque
 * tengan duraciones o pequenas variaciones de tempo distintas.
 *
 * La distancia entre puntos se calcula en semitonos (escala logaritmica),
 * no en Hz lineal, porque la percepcion de desafinacion es logaritmica:
 * el mismo error en Hz es mas grave en una nota grave que en una aguda.
 */
@Component
public class DtwAligner {

    public record ParAlineado(PitchPoint referencia, PitchPoint ensayo, double diferenciaCents) {}

    public List<ParAlineado> alinear(List<PitchPoint> referencia, List<PitchPoint> ensayo) {
        List<PitchPoint> ref = filtrarValidos(referencia);
        List<PitchPoint> ens = filtrarValidos(ensayo);

        int n = ref.size();
        int m = ens.size();

        if (n == 0 || m == 0) {
            return List.of();
        }

        double[][] costoAcumulado = new double[n + 1][m + 1];
        for (double[] fila : costoAcumulado) {
            java.util.Arrays.fill(fila, Double.POSITIVE_INFINITY);
        }
        costoAcumulado[0][0] = 0;

        // Matriz de costos: distancia en semitonos entre cada par de puntos
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                double costo = distanciaEnSemitonos(ref.get(i - 1).pitchHz(), ens.get(j - 1).pitchHz());
                double minAnterior = Math.min(
                        costoAcumulado[i - 1][j],
                        Math.min(costoAcumulado[i][j - 1], costoAcumulado[i - 1][j - 1])
                );
                costoAcumulado[i][j] = costo + minAnterior;
            }
        }

        // Backtracking para reconstruir el camino optimo de alineacion
        List<ParAlineado> alineados = new ArrayList<>();
        int i = n, j = m;
        while (i > 0 && j > 0) {
            PitchPoint pRef = ref.get(i - 1);
            PitchPoint pEns = ens.get(j - 1);
            double diffCents = distanciaEnSemitonos(pRef.pitchHz(), pEns.pitchHz()) * 100;
            alineados.add(new ParAlineado(pRef, pEns, diffCents));

            double diag = costoAcumulado[i - 1][j - 1];
            double arriba = costoAcumulado[i - 1][j];
            double izq = costoAcumulado[i][j - 1];

            if (diag <= arriba && diag <= izq) {
                i--; j--;
            } else if (arriba < izq) {
                i--;
            } else {
                j--;
            }
        }

        java.util.Collections.reverse(alineados);
        return alineados;
    }

    private List<PitchPoint> filtrarValidos(List<PitchPoint> puntos) {
        return puntos.stream().filter(PitchPoint::esValido).toList();
    }

    /** Distancia en semitonos (escala logaritmica) entre dos frecuencias en Hz. */
    private double distanciaEnSemitonos(float hzA, float hzB) {
        if (hzA <= 0 || hzB <= 0) {
            return 12; // penalizacion grande si algun punto no tiene pitch valido
        }
        return Math.abs(12 * (Math.log(hzA / hzB) / Math.log(2)));
    }
}
