##### Posibles casos para testear
- Etapa 1 - Argumento invalido
	Posible caso en que un hilo reciba un estado distinto a ``CREATED``
- Etapa 2 - Argumento invalido
	Posible caso de que un hilo no reciba un argumento tipo ``WAITING_VALIDATION``
- Etapa 2 - Llamada a la api de verificación
	Verificar que la llamada a la api de verificación sea correcta (Si la api resuelve verdadero ponga la orden en estado `READY_TO_PRINT` , caso contrario estado de  ``REJECTED`` y el estado de la impresora en `AVALIABLE`)
- Etapa 3 - Argumento invalido
	Posible caso de que un hilo no reciba un argumento tipo ``READY_TO_PRINT``
- Etapa 3 - Llamada a la api de verificación
	Verificar que la llamada a la api de verificación sea correcta (Si la api resuelve verdadero ponga la orden en estado `PRINTED` y la impresora en estado `AVALIABLE` , caso contrario estado de  ``PRINT_FAILED`` y el estado de la impresora en `OUT_OF_SERVICE`) 
- Etapa 4 - Argumento invalido
	Posible caso de que un hilo no reciba un argumento tipo ``PRINTED``
- Etapa 3 - Llamada a la api de verificación
	Verificar que la llamada a la api de verificación sea correcta (Si la api resuelve verdadero ponga la orden en estado `APROBED`, caso contrario estado de  ``DEFECTIVE``) 
##### Posibles secciones criticas
- Cond de carreras en el contador de ordenes
- Cond de carrera en la reserva de una impresora y el contador de uso (etapa 1)
- Posible caso de Inconsistencia al momento de asignar una orden a una impresora (Etapa 1) (¿Qué pasa si dos hilos toman la misma orden y la asignan a dos impresoras distintas?)
- Cond de carrera en la toma de las ordenes (Etapa 2) (¿Qué pasa si dos hilos toman la misma orden?)
