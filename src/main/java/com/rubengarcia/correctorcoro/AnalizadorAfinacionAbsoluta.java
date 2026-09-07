package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Analiza una curva de pitch contra la afinacion estandar (A4 = 440Hz,
 * temperamento igual), SIN necesidad de un audio de referencia.
 *
 * Util para responder "esta este audio afinado o no", en vez de
 * "esta este audio afinado respecto a este otro audio" (que es lo que
 * hace DtwAligner/ComparadorDeCoro).
 */
@Component
public class AnalizadorAfinacionAbsoluta {

    private static final String[] NOMBRES_NOTAS = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    // IMPORTANTE: una nota sostenida nunca puede estar a mas de 50 cents
    // de la nota mas cercana (las notas estan separadas 100 cents, el
    // punto medio es 50) - por eso el umbral tiene que ser MENOR a 50,
    // o nunca se dispara. 30 cents es un desvio bien perceptible para
    // un oido entrenado, sin ser inalcanzable como el 50.
    private static final double UMBRAL_CENTS = 30.0;
    private static final double DURACION_MINIMA_SEC = 0.15;

    public record PuntoAfinacion(double timestampSec, float pitchHz, String notaMasCercana, double diferenciaCents) {}

    public record TramoDesafinado(double inicioSec, double finSec, String notaMasCercana, double diferenciaCentsPromedio) {}

    public List<PuntoAfinacion> analizarPuntos(List<PitchPoint> pitchCurva) {
        List<PuntoAfinacion> puntos = new ArrayList<>();
        for (PitchPoint p : pitchCurva) {
            if (!p.esValido()) continue;
            double notaMidi = 69 + 12 * (Math.log(p.pitchHz() / 440.0) / Math.log(2));
            long notaMasCercana = Math.round(notaMidi);
            double diferenciaCents = (notaMidi - notaMasCercana) * 100;
            String nombreNota = NOMBRES_NOTAS[(int) (((notaMasCercana % 12) + 12) % 12)];
            int octava = (int) (notaMasCercana / 12) - 1;
            puntos.add(new PuntoAfinacion(p.timestampSec(), p.pitchHz(), nombreNota + octava, diferenciaCents));
        }
        return puntos;
    }

    /** Agrupa en tramos sostenidos donde la desviacion supera el umbral (evita marcar glitches puntuales). */
    public List<TramoDesafinado> detectarTramosDesafinados(List<PuntoAfinacion> puntos) {
        List<TramoDesafinado> tramos = new ArrayList<>();

        Double inicioTramo = null;
        List<PuntoAfinacion> puntosTramo = new ArrayList<>();

        for (PuntoAfinacion p : puntos) {
            boolean esError = Math.abs(p.diferenciaCents()) > UMBRAL_CENTS;

            if (esError) {
                if (inicioTramo == null) inicioTramo = p.timestampSec();
                puntosTramo.add(p);
            } else if (inicioTramo != null) {
                cerrarTramoSiAplica(tramos, inicioTramo, puntosTramo);
                inicioTramo = null;
                puntosTramo = new ArrayList<>();
            }
        }
        if (inicioTramo != null) {
            cerrarTramoSiAplica(tramos, inicioTramo, puntosTramo);
        }

        return tramos;
    }

    private void cerrarTramoSiAplica(List<TramoDesafinado> tramos, double inicio, List<PuntoAfinacion> puntosTramo) {
        if (puntosTramo.isEmpty()) return;
        double fin = puntosTramo.get(puntosTramo.size() - 1).timestampSec();
        if (fin - inicio < DURACION_MINIMA_SEC) return;

        double promedioCents = puntosTramo.stream().mapToDouble(PuntoAfinacion::diferenciaCents).average().orElse(0);
        String notaMasFrecuente = puntosTramo.get(puntosTramo.size() / 2).notaMasCercana();

        tramos.add(new TramoDesafinado(inicio, fin, notaMasFrecuente, promedioCents));
    }
}
