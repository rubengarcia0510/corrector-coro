package com.rubengarcia.correctorcoro;

import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquesta el spike completo (CDM-3): extrae pitch de referencia y ensayo,
 * los alinea con DTW y marca como error los tramos donde la diferencia
 * en cents supera el umbral durante un tiempo minimo sostenido (para
 * evitar falsos positivos por vibrato natural o glitches de deteccion).
 */
@Service
public class ComparadorDeCoro {

    // 50 cents = medio semitono. Ajustar segun resultados del spike.
    private static final double UMBRAL_CENTS = 50.0;

    // Duracion minima sostenida para contar como error real (evita ruido puntual)
    private static final double DURACION_MINIMA_SEC = 0.15;

    private final PitchExtractor pitchExtractor;
    private final DtwAligner dtwAligner;

    public ComparadorDeCoro(PitchExtractor pitchExtractor, DtwAligner dtwAligner) {
        this.pitchExtractor = pitchExtractor;
        this.dtwAligner = dtwAligner;
    }

    public List<ErrorDesafinacion> compararArchivos(File referenciaWav, File ensayoWav) throws Exception {
        List<PitchPoint> pitchReferencia = pitchExtractor.extraerPitch(referenciaWav);
        List<PitchPoint> pitchEnsayo = pitchExtractor.extraerPitch(ensayoWav);

        List<DtwAligner.ParAlineado> alineados = dtwAligner.alinear(pitchReferencia, pitchEnsayo);

        return detectarErroresSostenidos(alineados);
    }

    private List<ErrorDesafinacion> detectarErroresSostenidos(List<DtwAligner.ParAlineado> alineados) {
        List<ErrorDesafinacion> errores = new ArrayList<>();

        Double inicioTramoError = null;
        DtwAligner.ParAlineado primerPuntoTramo = null;

        for (DtwAligner.ParAlineado par : alineados) {
            boolean esError = Math.abs(par.diferenciaCents()) > UMBRAL_CENTS;

            if (esError && inicioTramoError == null) {
                inicioTramoError = par.ensayo().timestampSec();
                primerPuntoTramo = par;
            } else if (!esError && inicioTramoError != null) {
                double duracionTramo = par.ensayo().timestampSec() - inicioTramoError;
                if (duracionTramo >= DURACION_MINIMA_SEC) {
                    errores.add(aErrorDesafinacion(primerPuntoTramo));
                }
                inicioTramoError = null;
            }
        }

        // Cerrar un tramo de error que llegue hasta el final del audio
        if (inicioTramoError != null && primerPuntoTramo != null) {
            errores.add(aErrorDesafinacion(primerPuntoTramo));
        }

        return errores;
    }

    private ErrorDesafinacion aErrorDesafinacion(DtwAligner.ParAlineado par) {
        return new ErrorDesafinacion(
                par.ensayo().timestampSec(),
                par.referencia().pitchHz(),
                par.ensayo().pitchHz(),
                par.diferenciaCents()
        );
    }
}
