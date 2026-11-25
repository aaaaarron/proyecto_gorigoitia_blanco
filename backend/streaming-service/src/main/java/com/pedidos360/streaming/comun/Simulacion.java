package com.pedidos360.streaming.comun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interruptor para la demo: con fallo de analítica activado, la aplicación de analítica responde con un
 * error transitorio (como si su BD no estuviera disponible) y se ven los reintentos con espera creciente
 * y el envío al stream de fallidos. Se cambia en vivo desde mensajeria-admin, sin reiniciar.
 */
@Component
public class Simulacion {

    private final AtomicBoolean falloAnalitica;

    public Simulacion(@Value("${simulacion.fallo-transitorio-analitica:false}") boolean inicial) {
        this.falloAnalitica = new AtomicBoolean(inicial);
    }

    public boolean falloAnalitica() {
        return falloAnalitica.get();
    }

    public void falloAnalitica(boolean activo) {
        falloAnalitica.set(activo);
    }
}
