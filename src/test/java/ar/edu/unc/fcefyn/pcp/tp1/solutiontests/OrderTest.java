
package ar.edu.unc.fcefyn.pcp.tp1.solutiontests;

import ar.edu.unc.fcefyn.pcp.tp1.solution.Order;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OrderTest {

    @Test
    void noPermiteAsignarDosVeces() {

        Order orden = new Order(1);

        orden.onAssigned("P-0-1");

        assertEquals(
            OrderState.WAITING_VALIDATION,
            orden.getState()
        );

        assertThrows(IllegalStateException.class, () -> {
            orden.onAssigned("P-0-2");
        });

        assertEquals(1, orden.getAssignmentCount());
    }


@Test
    void noPermiteValidarSinAsignacion() {

        Order orden = new Order(2);

        assertThrows(IllegalStateException.class, () -> {
            orden.onValidationResult(true);
        });

        assertEquals(OrderState.CREATED, orden.getState());
        assertEquals(0, orden.getValidationCount());
    }

@Test
    void ordenCompletaRecorridoExitoso() {

        Order orden = new Order(3);

        orden.onAssigned("P-0-1");
        orden.onValidationResult(true);
        orden.onPrintResult(true);
        orden.onQualityResult(true);

        assertEquals(OrderState.APPROVED, orden.getState());
        assertEquals(1, orden.getAssignmentCount());
        assertEquals(1, orden.getValidationCount());
        assertEquals(1, orden.getPrintingCount());
        assertEquals(1, orden.getQualityControlCount());
    }
@Test
    void ordenRechazadaEnValidacion() {

        Order orden = new Order(4);

        orden.onAssigned("P-0-2");
        orden.onValidationResult(false);

        assertEquals(OrderState.REJECTED, orden.getState());
        assertEquals(1, orden.getAssignmentCount());
        assertEquals(1, orden.getValidationCount());
        assertEquals(0, orden.getPrintingCount());
        assertEquals(0, orden.getQualityControlCount()); 
        
        assertThrows(IllegalStateException.class, () -> {
        orden.onPrintResult(true);
});
    }
}