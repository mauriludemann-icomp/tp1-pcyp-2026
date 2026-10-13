# Informe Trabajo Practico Nº1
## Grupo: Programacion Con Currente
### Integrantes
* Chiavazza Labeguerie, Fiamma Itziar -  46851396
* Scagliotti, Marcos - 46070301
* Arias, Valentin - 45734482
* Tosolini, Franco Alberto - 45702152
* Boye, Facundo - 46070306

## Recursos Compartidos

#### Printer
En la clase Printer encontramos atributos mutables que son consultados e interactuados por varios hilos simultaneamente, por lo que encontramos recursos criticos entre ellos **state : PrinterState**, **asssignedOrderId : Integer** y **count : int**.

#### Printer Manager
Printer manager es un recursor compartido ya que actua a modo de monitor, donde los hilos compiten por la reserva de impresoras disponibles y este tambien gestiona la liberacion de las mismas, todo esto con los metodos **reserveAvailablePrinter** y **releasePrinter**.

#### Work Queue
Este recurso critico surge del diseño proporcionado por nosotros ya que no esta explicito en el texto, Es un recurso critico ya que posee campos como **queue : Queue< Order >** que gestiona las tareas que deben hacerse por cada etapa y si mas de un hilo interactuara con ellas a la vez la estructura de datos podria corromperse, tambien el campo **closed: Boolean** que marca si una etapa fue finalizada o no.

#### Order
Todos los metodos que encontrarmos en order salvo el id, son de caracteristicas mutables y todos son interactuados por  mas de un hilo a la vez, haciendo que si no se gestionan los problemas de concurrencia en estos mismos, podrian generarse conflictos y problemas.

#### Event Logger
Aqui los atributos compartidos son **sequence : long** y **writer : BufferedWriter**, el event logger es el unico recurso al que absolutamente todos los hilos del programa de cada etapa acceden concurrentemente y al mismo tiempo, lo que hace que sea especialmente critico en esta ocasion.

#### Order Manager
En este caso el recurso compartido sera **orderMap : Map< Integer, Order>** ya que varios hilos pueden consultar y registrar ordenes a la vez.


## Race Conditions y Explicacion

#### Disponibilidad de impresoras
Cuando en la primer etapa de asignacion de impresoras los hilos intentan conseguir una impresora que este libre, estos compiten por conseguirla, generando asi una race condition.

#### Tareas en Work Queue
Nuevamente surgen desde nuestra vision de como encarar el problema, los hilos al poder modificar simultaneamente las tareas que estan en la **queue**, ejecutando push() o pop() modifican la misma y generan problemas de concurrencia.

#### Contador de Secuencia en Event Logger
Al ser el contador **sequence** accedido por todos los hilos de cada etapa simultaneamente, estos pueden modificar el contador y/o leer el mismo valor al mismo tiempo generando duplicacion de tareas o perder incrementos.

#### Estados internos de Orden y contadores
Si un hilo modifica el estado y simultaneamente otro hilo lee el estado, se crearian estados intermedios errones lo cual violaria uno de los invariables del enunciado: "Cada orden existe en un solo estado a la vez".

#### Notificaciones de los hilos en PrinterManager
Si un hilo se duerme al mismo tiempo que se libera una impresora, este podria quedarse dormido por tiempo indefinido, aun habiendo una impresora libre

#### Insercion de ordenes y close en Pipeline Coordinator
Supongamos que un hilo intentara modificar el boolean *closed* en WorkQueue, si intena cerrar la cola justo mientras otro hilo sigue modificando o intentando modificar la misma luego el hilo al intertar insertarla en una cola cerrada, lanzaria una excepcion y se perderia una orden.

## Estrategia de sincronización utilizada.

#### En PrinterManager 
Monitor centralizado. Tanto la búsqueda/reserva como la liberación están protegidas por el lock intrínseco de PrinterManager. Esto hace que el chequeo de disponibilidad y la asignación sean atómicos (solución a Check-Then-Act y Lost Wakeup).
#### En WorkQueue 
Implementación del patrón Productor-Consumidor Bounded/Unbounded con monitores. Las operaciones push(), pop() y close() están sincronizadas sobre la cola, garantizando que el acceso al ArrayDeque sea mutuamente excluyente.
#### En EventLogger 
Sección crítica con cerrojo intrínseco. Se sincroniza el método logEvent(...) para serializar las escrituras en el archivo de texto y garantizar que la operación sequence++ sea estrictamente secuencial y atómica.
#### En Order y OrderManager
 Métodos sincronizados para asegurar la visibilidad en memoria (happens-before) entre el hilo que muta el estado y cualquier hilo que consulte o tome un snapshot.

## Condiciones de Espera y Despertar en los Hilos
Estas ocurren principalmente en 2 casos puntuales:

#### Asignacion de impresoras
En la primer etapa un hilo de asignacion se bloquea al no haber impresoras disponibles *while ((availablePrinter == null) { wait(); })* y luego es despertado cuando un hilo de validacion o de impresion libera una impresora y llama a *releasePrinter()*. Alli se ejecuta un *notifyAll()* despertando a los hilos asignados que estaban bloqueados previamente.

#### Work-Queue
Aqui se generan dos condiciones distintas de espera, una por cola vacia dada por: *while (queue.isEmpty() && !closed) { wait(); }*, bloqueando a aquellos hilos que quisieran buscar una tarea, y otra por cola llena dada por: *while (queue.size() == capacity && !closed) { wait(); }*, mandando a dormir a todos los hilos que intenten ingresar una orden. Para cada una de esas condiciones hay dos formas de despertar a los hilos, el metodo *pop()* ejecuta *notifyAll()* y despierta a los hilos que pudieran estar esperando para ingresar ordenes y otra con *push()* despertando a los hilos de forma equivalente pero en este caso a aquellos que estan esperando para tomar una tarea.


## Mecanismo de Terminacion
El programa propuesto por nuestro grupo tiene dos mecanismos de terminacion: una terminacion normal y ordenada, y una terminacion ante cancelaciones o excepciones.

#### Terminacion Normal 
La terminacion del sistema se realiza de forma progresiva y en cascada a traves de las etapas del pipeline, garantizando que ninguna orden se pierda y que todos los hilos finalicen sin quedar activos:

**Uso de valor centinela (*null*) en *WorkQueue*:** Al invocar el metodo *close()* sobre una cola, esta no descarta los elementos pendientes. Los hilos consumidores continuan procesando las ordenes restantes normalmente. Una vez que la cola queda completamente vacia y su estado es *closed == true*, el metodo *pop()* deja de suspenderse en *wait()* y retorna *null*. Al recibir este valor centinela, el bucle principal de cada *Worker* finaliza su ejecucion de forma limpia y natural (*break*).
**Propagacion en cascada mediante *PipelineCoordinator*:** El cierre de colas se realiza de izquierda a derecha en el pipeline. El coordinador utiliza el metodo *awaitStageAndCloseOutput(stageIndex, outputQueue)*, el cual ejecuta un *join()* sobre todos los hilos de la etapa actual. Recien cuando se tiene la certeza de que todos los hilos productores de esa etapa terminaron su ejecucion, se procede a cerrar la cola de salida hacia la etapa siguiente (*outputQueue.close()*). Esto evita condiciones de carrera donde un hilo intente insertar una orden en una cola cerrada prematuramente.
**Conclusion de la simulacion:** Una vez que los hilos de la ultima etapa (Control de Calidad) finalizan tras vaciar la ultima cola, el hilo principal verifica mediante *printerManager.allPrintersReleased()* y la comprobacion de estados de los hilos que no quede ninguna impresora reservada ni ningun hilo en ejecucion, procediendo a la escritura final de resultados.

#### Terminacion Anormal o por Interrupcion 
En caso de que ocurra una excepcion no controlada o una señal de interrupcion externa (*InterruptedException*) antes de completar el ciclo:
Se activa el bloque *finally* en *ConcurrentSimulation*, invocando a *pipelineCoordinator.cancelAndAwaitTermination()*.
Dicho metodo cierra inmediatamente todas las colas del sistema (*queue.close()*) para destrabar a cualquier hilo suspendido en *wait()*.
Envia una señal de interrupcion a cada hilo activo mediante *worker.interrupt()*, logrando que salgan de los bloqueos de simulacion de tiempo (*Thread.sleep*).
Finalmente, espera a que cada uno de los hilos finalice mediante un ciclo de *join()*, garantizando el cumplimiento del invariante de no dejar hilos huerfanos o activos en memoria.