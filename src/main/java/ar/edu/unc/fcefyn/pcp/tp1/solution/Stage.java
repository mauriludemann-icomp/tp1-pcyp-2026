package ar.edu.unc.fcefyn.pcp.tp1.solution;


//Etapas

public enum Stage{
    INITIALIZATION, // Creación de la orden. Pasa de nada a created
    ASSIGNMENT,  // Asignar impresora (3 hilos). Create -> Validation
    VALIDATION,  // validamos el modelo 3D. WAITING_VALIDATION -> PRINTED o a PRINT_FAILED
    PRINTING,  //  Imprimimos READY_TO_PRINT -> PRINTED o PRINT_FAILED
    QUALITY_CONTROL  // control de calidad. PRINTED -> APROVED o DEFECTIVE (Si salio bien o no la impresion)
}



