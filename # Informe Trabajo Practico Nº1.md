# Informe Trabajo Práctico Nº 1
## Grupo: Programación Con Currente
### Integrantes
* Chiavazza Labeguerie, Fiamma Itziar - 46851396
* Scagliotti, Marcos - 46070301
* Arias, Valentín - 45734482
* Tosolini, Franco Alberto - 45702152
## Recursos Compartidos
#### Printer
En la clase Printer encontramos atributos mutables que son consultados e interactuados por varios hilos simultáneamente, por lo que encontramos recursos críticos entre ellos **state : PrinterState**, **assignedOrderId : Integer** y **count : int**.
#### Printer Manager
Printer Manager es un recurso compartido ya que actúa a modo de monitor, donde los hilos compiten por la reserva de impresoras disponibles y este también gestiona la liberación de las mismas, todo esto con los métodos **reserveAvailablePrinter** y **releasePrinter**.
#### Work Queue
Este recurso crítico surge del diseño proporcionado por nosotros ya que no está explícito en el texto. Es un recurso crítico ya que posee campos como **queue : Queue< Order >** que gestiona las tareas que deben hacerse por cada etapa y si más de un hilo interactuara con ellas a la vez la estructura de datos podría corromperse, también el campo **closed: Boolean** que marca si una etapa fue finalizada o no.
#### Order
Todos los métodos que encontramos en Order salvo el id, son de características mutables y todos son interactuados por más de un hilo a la vez, haciendo que si no se gestionan los problemas de concurrencia en estos mismos, podrían generarse conflictos y problemas.
#### Event Logger
Aquí los atributos compartidos son **sequence : long** y **writer : BufferedWriter**. El Event Logger es el único recurso al que absolutamente todos los hilos del programa de cada etapa acceden concurrentemente y al mismo tiempo, lo que hace que sea especialmente crítico en esta ocasión.
#### Order Manager
En este caso el recurso compartido será **orderMap : Map< Integer, Order>** ya que varios hilos pueden consultar y registrar órdenes a la vez.
## Race Conditions y Explicación
#### Disponibilidad de impresoras
Cuando en la primera etapa de asignación de impresoras los hilos intentan conseguir una impresora que esté libre, estos compiten por conseguirla, generando así una race condition.
#### Tareas en Work Queue
Nuevamente surgen desde nuestra visión de cómo encarar el problema: los hilos, al poder modificar simultáneamente las tareas que están en la **queue**, ejecutando push() o pop(), modifican la misma y generan problemas de concurrencia.
#### Contador de Secuencia en Event Logger
Al ser el contador **sequence** accedido por todos los hilos de cada etapa simultáneamente, estos pueden modificar el contador y/o leer el mismo valor al mismo tiempo generando duplicación de tareas o perder incrementos.
#### Estados internos de Orden y contadores
Si un hilo modifica el estado y simultáneamente otro hilo lee el estado, se crearían estados intermedios erróneos, lo cual violaría uno de los invariables del enunciado: "Cada orden existe en un solo estado a la vez".
#### Notificaciones de los hilos en PrinterManager
Si un hilo se duerme al mismo tiempo que se libera una impresora, este podría quedarse dormido por tiempo indefinido, aun habiendo una impresora libre.
#### Inserción de órdenes y close en Pipeline Coordinator
Supongamos que un hilo intentara modificar el boolean *closed* en WorkQueue: si intenta cerrar la cola justo mientras otro hilo sigue modificando o intentando modificar la misma, luego el hilo, al intentar insertarla en una cola cerrada, lanzaría una excepción y se perdería una orden.
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
#### Asignación de impresoras
En la primera etapa un hilo de asignación se bloquea al no haber impresoras disponibles *while ((availablePrinter == null) { wait(); })* y luego es despertado cuando un hilo de validación o de impresión libera una impresora y llama a *releasePrinter()*. Allí se ejecuta un *notifyAll()* despertando a los hilos asignados que estaban bloqueados previamente.
#### Work-Queue
Aquí se generan dos condiciones distintas de espera: una por cola vacía dada por *while (queue.isEmpty() && !closed) { wait(); }*, bloqueando a aquellos hilos que quisieran buscar una tarea, y otra por cola llena dada por *while (queue.size() == capacity && !closed) { wait(); }*, mandando a dormir a todos los hilos que intenten ingresar una orden. Para cada una de esas condiciones hay dos formas de despertar a los hilos: el método *pop()* ejecuta *notifyAll()* y despierta a los hilos que pudieran estar esperando para ingresar órdenes, y otra con *push()* despertando a los hilos de forma equivalente, pero en este caso a aquellos que están esperando para tomar una tarea.
## Mecanismo de Terminación
El programa propuesto por nuestro grupo tiene dos mecanismos de terminación: una terminación normal y ordenada, y una terminación ante cancelaciones o excepciones.
#### Terminación Normal 
La terminación del sistema se realiza de forma progresiva y en cascada a través de las etapas del pipeline, garantizando que ninguna orden se pierda y que todos los hilos finalicen sin quedar activos:
**Uso de valor centinela (*null*) en *WorkQueue*:** Al invocar el método *close()* sobre una cola, esta no descarta los elementos pendientes. Los hilos consumidores continúan procesando las órdenes restantes normalmente. Una vez que la cola queda completamente vacía y su estado es *closed == true*, el método *pop()* deja de suspenderse en *wait()* y retorna *null*. Al recibir este valor centinela, el bucle principal de cada *Worker* finaliza su ejecución de forma limpia y natural (*break*).  
**Propagación en cascada mediante *PipelineCoordinator*:** El cierre de colas se realiza de izquierda a derecha en el pipeline. El coordinador utiliza el método *awaitStageAndCloseOutput(stageIndex, outputQueue)*, el cual ejecuta un *join()* sobre todos los hilos de la etapa actual. Recién cuando se tiene la certeza de que todos los hilos productores de esa etapa terminaron su ejecución, se procede a cerrar la cola de salida hacia la etapa siguiente (*outputQueue.close()*). Esto evita condiciones de carrera donde un hilo intente insertar una orden en una cola cerrada prematuramente.  
**Conclusión de la simulación:** Una vez que los hilos de la última etapa (Control de Calidad) finalizan tras vaciar la última cola, el hilo principal verifica mediante *printerManager.allPrintersReleased()* y la comprobación de estados de los hilos que no quede ninguna impresora reservada ni ningún hilo en ejecución, procediendo a la escritura final de resultados.
#### Terminación Anormal o por Interrupción 
En caso de que ocurra una excepción no controlada o una señal de interrupción externa (*InterruptedException*) antes de completar el ciclo:  
Se activa el bloque *finally* en *ConcurrentSimulation*, invocando a *pipelineCoordinator.cancelAndAwaitTermination()*.  
Dicho método cierra inmediatamente todas las colas del sistema (*queue.close()*) para destrabar a cualquier hilo suspendido en *wait()*.  
Envía una señal de interrupción a cada hilo activo mediante *worker.interrupt()*, logrando que salgan de los bloqueos de simulación de tiempo (*Thread.sleep*).  
Finalmente, espera a que cada uno de los hilos finalice mediante un ciclo de *join()*, garantizando el cumplimiento del invariante de no dejar hilos huérfanos o activos en memoria.
## Análisis Teórico del Tiempo de Ejecución
Para estimar cuánto debería tardar la simulación de forma teórica bajo condiciones ideales, analizamos el comportamiento del pipeline en base a los valores establecidos en el archivo de configuración *tp1.properties*:
* **Total de órdenes:** 500 órdenes.
* **Matriz de impresoras:** 10 filas x 20 columnas = 200 impresoras disponibles.
* **Etapa 1 (Asignación):** 3 hilos con una demora de 50 ms.
* **Etapa 2 (Validación):** 2 hilos con una demora de 120 ms.
* **Etapa 3 (Impresión):** 3 hilos con una demora de 180 ms.
* **Etapa 4 (Control de Calidad):** 2 hilos con una demora de 80 ms.
#### 1. Identificación del Cuello de Botella
En una arquitectura por etapas concurrentes (*pipeline*), el ritmo general de avance del sistema queda determinado por la etapa más lenta (el *cuello de botella*).  
Primero verificamos las impresoras: al disponer de 200 impresoras y tener como máximo 3 hilos trabajando en impresión a la vez, las impresoras nunca escasean ni frenan el avance, por lo que no representan una limitación.  
Luego calculamos el tiempo promedio de despacho de cada etapa dividiendo su demora por la cantidad de hilos que trabajan en paralelo:
* **Asignación:** 50 ms / 3 hilos ≈ 16.67 ms por orden.
* **Validación:** 120 ms / 2 hilos = 60 ms por orden.
* **Impresión:** 180 ms / 3 hilos = 60 ms por orden.
* **Control de Calidad:** 80 ms / 2 hilos = 40 ms por orden.  
Como podemos observar, el cuello de botella se encuentra en la etapa de **Validación** (y de Impresión), que despachan a un ritmo de 1 orden cada **60 ms**. Dado que la etapa de Validación debe procesar obligatoriamente las 500 órdenes iniciales (a diferencia de las siguientes etapas donde algunas órdenes se van descartando), es esta etapa la que marca el tiempo total de procesamiento.
#### 2. Estimación del Tiempo Total
El tiempo total se compone de dos partes:
1. **Llenado inicial del pipeline:** El tiempo que tarda la primera orden en recorrer todas las etapas desde el inicio hasta el final:
   * 50 ms + 120 ms + 180 ms + 80 ms = 430 ms.
2. **Procesamiento de las órdenes restantes en el cuello de botella:** Las 499 órdenes restantes van saliendo al ritmo que impone la etapa de validación (60 ms por orden):
   * 499 órdenes x 60 ms = 29.940 ms.  
Sumando ambos términos obtenemos el tiempo teórico esperado:
* **Tiempo Teórico Total:** 430 ms + 29.940 ms = **30.370 ms (aproximadamente 30.4 segundos)**.
## Resultados de Múltiples Ejecuciones
#### Run 1:
* allThreadsTerminated=true
* approvedOrders=359
* defectiveOrders=25
* durationMillis=30607
* printFailedOrders=45
* processedOrders=500
* rejectedOrders=71
* remainingIntermediateOrders=0
* totalOrders=500
#### Run 2:
* allThreadsTerminated=true
* approvedOrders=359
* defectiveOrders=25
* durationMillis=30712
* printFailedOrders=45
* processedOrders=500
* rejectedOrders=71
* remainingIntermediateOrders=0
* totalOrders=500
#### Run 3:
* allThreadsTerminated=true
* approvedOrders=359
* defectiveOrders=25
* durationMillis=30738
* printFailedOrders=45
* processedOrders=500
* rejectedOrders=71
* remainingIntermediateOrders=0
* totalOrders=500
#### Run 4:
* allThreadsTerminated=true
* approvedOrders=359
* defectiveOrders=25
* durationMillis=30647
* printFailedOrders=45
* processedOrders=500
* rejectedOrders=71
* remainingIntermediateOrders=0
* totalOrders=500
#### Run 5:
* allThreadsTerminated=true
* approvedOrders=359
* defectiveOrders=25
* durationMillis=30624
* printFailedOrders=45
* processedOrders=500
* rejectedOrders=71
* remainingIntermediateOrders=0
* totalOrders=500
## Comparación Entre Tiempos Teóricos y Observados
Hicimos 5 simulaciones de Run y terminaron respectivamente en los siguientes tiempos: 30.607, 30.712, 30.738, 30.647, 30.624 (segundos), lo que nos da un promedio de ejecución de 30.665 segundos ≈ 30.7 segundos, y en el cálculo teórico nos da aproximadamente un promedio de 30.4 segundos, lo que nos da una diferencia promedio de 0.3 segundos. Estas diferencias se deben principalmente a las imprecisiones de los sleep y wait que el sistema ejecuta, y también de la competencia natural de los hilos por los recursos, que consumen ciclos de CPU adicionales; sin embargo, la diferencia es marginal y bastante cercana a lo deseado.
## Análisis del Efecto de Cambiar la Cantidad de Hilos y las Demoras
#### Modificación de Cantidad de Hilos
Este punto depende directamente de dónde modifiquemos la cantidad de hilos existente. Principalmente la parte más interesante de la modificación de los hilos podría verse en la etapa donde presentamos el cuello de botella del pipeline, que corresponde en nuestro caso a la etapa de validación e impresión: si aumentamos drásticamente la cantidad de hilos ahí, la etapa pasaría de tardar aproximadamente unos 60 ms por orden a apenas unas milésimas de segundo dependiendo de cuántos hilos aumentemos, y caso contrario si los disminuimos, el cuello de botella solo se agrandaría.  
Si en cambio aumentamos la cantidad de hilos en otra etapa que no sea el cuello de botella, esta etapa tardará menos pero seguirá siendo detenida en las etapas de impresión y validación ya que la velocidad máxima de producción estará siempre limitada por el cuello de botella; y si por el contrario disminuimos hasta tener más tiempo de retardo que el cuello de botella, esa área pasará a ser el cuello de botella dominante.  
También hay que tener en cuenta la escalabilidad si solo aumentamos la cantidad de hilos indefinidamente: por encima de los núcleos físicos de la máquina, el rendimiento del programa se verá carcomido por el overhead y los context switching.  
#### Modificación de Esperas
Si se configuran todas las demoras en 0 ms, la simulación deja de estar limitada por las pausas simuladas de tiempo (*Thread.sleep*) y pasa a estar limitada pura y exclusivamente por la velocidad de la CPU y la sincronización de hilos en memoria. En este escenario, las 500 órdenes se procesan de manera prácticamente instantánea (en cuestión de milisegundos), permitiendo comprobar que los mecanismos de sincronización, las colas y la exclusión mutua funcionan correctamente sin depender de las pausas temporales.  
Si se incrementa significativamente la demora de una sola etapa (por ejemplo, aumentando la demora de Control de Calidad a 300 ms), esta absorbe de inmediato el rol de cuello de botella excluyente. Esto provoca que el tiempo total de la simulación crezca proporcionalmente a dicha demora, obligando a los hilos de esa etapa a trabajar al 100% mientras las etapas anteriores quedan bloqueadas por colas llenas o finalizan mucho antes.  
## Justificación de las decisiones de diseño.