# Programación Concurrente y Paralela 2026

# Trabajo Práctico 1

## Condiciones

- El trabajo es grupal.
- La defensa y la presentación se realizan con el grupo completo.
- La evaluación puede incluir una calificación individual.
- Solo se corrigen las entregas subidas al aula virtual y asociadas con un repositorio Git accesible para la cátedra.
- Los problemas de concurrencia deben estar correctamente resueltos y explicados.
- El trabajo debe implementarse con Java 21 y Maven.
- Se evaluará la utilización de objetos y colecciones, la corrección de la sincronización y la explicación de los conceptos relacionados con la programación concurrente.

## Objetivo

Desarrollar un sistema concurrente que simule una granja industrial de impresión 3D. El sistema recibe órdenes de fabricación, les asigna una impresora, valida los modelos, ejecuta las impresiones y controla la calidad de las piezas obtenidas.

Todas las etapas deben funcionar concurrentemente y compartir recursos de manera segura. El sistema debe procesar todas las órdenes, conservar la consistencia de sus estados y finalizar sin dejar hilos activos.

## Modelo del sistema

La granja contiene una matriz de impresoras. Cada impresora posee:

- Un identificador único.
- Una posición dentro de la matriz.
- Un estado.
- Un contador de usos.
- La orden que tiene asignada, si corresponde.

Los estados posibles de una impresora son:

- `AVAILABLE`: disponible para recibir una orden.
- `RESERVED`: reservada por una orden que está siendo validada o impresa.
- `OUT_OF_SERVICE`: fuera de servicio y no reutilizable durante la ejecución actual.

A su vez, existen las órdenes de impresión. Cada orden de impresión debe comenzar en estado `CREATED` y finalizar exactamente en uno de los siguientes estados:

- `APPROVED`.
- `REJECTED`.
- `PRINT_FAILED`.
- `DEFECTIVE`.

Las órdenes deben identificarse consecutivamente desde `1` hasta `totalOrders`.

## Etapas concurrentes

Todas las etapas deben iniciarse al comienzo del programa. No se permite ejecutar una etapa completa antes de iniciar la siguiente.

### Etapa 1 Asignación de impresora

Esta etapa es ejecutada por tres hilos en la configuración oficial.

Cada hilo debe:

1. Tomar una orden en estado `CREATED`.
2. Obtener una impresora disponible.
3. Reservarla de manera exclusiva.
4. Incrementar su contador de usos.
5. Asociarla con la orden.
6. Cambiar la orden a `WAITING_VALIDATION`.

Transiciones:

```text
Orden:     CREATED -> WAITING_VALIDATION
Impresora: AVAILABLE -> RESERVED
```

### Etapa 2 Validación del modelo

Esta etapa es ejecutada por dos hilos en la configuración oficial.

Cada hilo toma una orden en `WAITING_VALIDATION` y valida su modelo 3D mediante `OutcomeDecider.isModelValid`.

Si el modelo es válido:

```text
Orden: WAITING_VALIDATION -> READY_TO_PRINT
```

La impresora permanece reservada.

Si el modelo es inválido:

```text
Orden:     WAITING_VALIDATION -> REJECTED
Impresora: RESERVED -> AVAILABLE
```

La orden queda terminada y la impresora puede recibir otro trabajo.

### Etapa 3 Impresión

Esta etapa es ejecutada por tres hilos en la configuración oficial.

Cada hilo toma una orden en `READY_TO_PRINT` y determina el resultado mediante `OutcomeDecider.isPrintSuccessful`.

Si la impresión es exitosa:

```text
Orden:     READY_TO_PRINT -> PRINTED
Impresora: RESERVED -> AVAILABLE
```

Si la impresión falla:

```text
Orden:     READY_TO_PRINT -> PRINT_FAILED
Impresora: RESERVED -> OUT_OF_SERVICE
```

Una impresora fuera de servicio no puede volver a utilizarse durante esa ejecución.

### Etapa 4 Control de calidad

Esta etapa es ejecutada por dos hilos en la configuración oficial.

Cada hilo toma una orden en `PRINTED` y determina el resultado mediante `OutcomeDecider.isQualityApproved`.

Resultados posibles:

```text
PRINTED -> APPROVED
PRINTED -> DEFECTIVE
```

En esta etapa no se utilizan impresoras, dado que la impresora ya fue liberada en la etapa de impresión.

## Determinación de resultados

Las decisiones de validación, impresión y calidad deben realizarse exclusivamente con la clase `OutcomeDecider` provista por la cátedra.

La combinación de la semilla, el identificador de la orden y la etapa produce un resultado determinista. Por lo tanto, una misma configuración debe producir los mismos estados finales independientemente del orden en que se ejecuten los hilos. No se exige que sean iguales la impresora asignada a cada orden, los contadores individuales de las impresoras, los tiempos ni el orden global de los eventos.

No se permite modificar las clases contenidas en el paquete:

```text
ar.edu.unc.fcefyn.pcp.tp1.api
```

## Invariantes obligatorios

La implementación debe preservar como mínimo los siguientes invariantes:

1. Cada orden existe en un solo estado a la vez.
2. Cada orden alcanza exactamente un estado terminal.
3. Ninguna orden puede validarse, imprimirse o auditarse más veces de lo establecido por su recorrido.
4. Una impresora no puede estar asignada a dos órdenes simultáneamente.
5. Toda orden en `WAITING_VALIDATION` o `READY_TO_PRINT` debe tener una impresora reservada.
6. Una orden terminal no puede mantener una impresora reservada.
7. Una impresora `OUT_OF_SERVICE` no puede reutilizarse.
8. El contador de usos de las impresoras debe coincidir con la cantidad total de asignaciones.
9. Al finalizar no pueden quedar órdenes en estados intermedios.
10. Al finalizar no pueden quedar impresoras en estado `RESERVED`.

## Configuración

La aplicación debe leer siempre:

```text
config/tp1.properties
```

No debe requerir argumentos de línea de comandos ni configuración del IDE.

La suite de la cátedra utilizará diferentes archivos de configuración. No deben codificarse en el programa cantidades de órdenes, impresoras, hilos, probabilidades, tiempos o rutas.

`Main` carga `config/tp1.properties`. En cambio, `Simulation.execute(config)` debe utilizar exclusivamente la configuración recibida, sin volver a leer ese archivo.

La configuración oficial contiene:

- 500 órdenes.
- Una matriz de 200 impresoras.
- Tres hilos de asignación.
- Dos hilos de validación.
- Tres hilos de impresión.
- Dos hilos de control de calidad.
- 85 por ciento de modelos válidos.
- 90 por ciento de impresiones exitosas.
- 95 por ciento de piezas aprobadas.

Las configuraciones oficiales y de evaluación garantizan que no se agotarán las impresoras operativas. En particular, habrá más impresoras que órdenes que, según `OutcomeDecider`, puedan terminar en `PRINT_FAILED`. No es necesario resolver el caso de agotamiento de impresoras.

## Demoras

Cada hilo debe aplicar la demora configurada una vez por cada orden efectivamente procesada en su etapa.

Las demoras representan el tiempo propio de las operaciones y no pueden utilizarse como mecanismo de sincronización.

Con la configuración oficial, el programa debe demorar entre 30 y 45 segundos en el entorno de referencia de la cátedra. La suite pública y la privada utilizarán también configuraciones con demoras nulas o reducidas.

## Terminación

La simulación debe terminar cuando todas las órdenes hayan alcanzado un estado terminal.

Antes de retornar desde `Simulation.execute` se debe garantizar que:

- Todas las órdenes fueron procesadas.
- No quedan elementos en registros intermedios.
- Todos los hilos creados por la simulación finalizaron.
- No quedan impresoras reservadas.
- Los archivos de resultados fueron escritos y cerrados.

No se permite finalizar los hilos mediante `Thread.stop`, `System.exit` ni mecanismos equivalentes.

## Sincronización permitida

En `src/main/java` se permite utilizar:

- `Thread` y `Runnable`.
- `synchronized` (método y bloque).
- `java.util.concurrent.locks.Lock`.
- `java.util.concurrent.locks.ReentrantLock`.
- `java.util.concurrent.Semaphore`.
- `java.util.concurrent.Executor`.
- `java.util.concurrent.ExecutorService`.
- `java.util.concurrent.Executors`.
- `java.util.concurrent.ThreadPoolExecutor`.
- `Object.wait`, `Object.notify` y `Object.notifyAll`.
- Colecciones comunes no thread-safe de Java.

El uso de ejecutores es opcional. Si se utilizan, deben limitarse a ejecutar los workers de la simulación y a esperar su finalización; no sustituyen los mecanismos de sincronización que deben implementar los grupos. `Thread` es el camino recomendado para esta primera entrega.

No se permite utilizar:

- Ningún otro tipo de `java.util.concurrent` ni de sus subpaquetes. Las únicas
  excepciones son las explicitadas anteriormente.
- Imports con wildcard de `java.util.concurrent` o de sus subpaquetes.
- Colecciones concurrentes.
- `Condition`.
- Ejecutores programados, `ForkJoinPool` y cualquier otro ejecutor o pool no
  incluido expresamente en la lista anterior.
- `BlockingQueue`.
- Barreras y latches provistos por la biblioteca estándar.
- Clases atómicas.
- `Collections.synchronizedList`, `synchronizedMap` o equivalentes.
- `Vector` o `Hashtable` como mecanismos de sincronización.
- Streams paralelos.
- Espera activa.
- Librerías externas de producción.

JUnit está permitido únicamente en `src/test/java`.

## API obligatoria

La clase:

```text
ar.edu.unc.fcefyn.pcp.tp1.solution.ConcurrentSimulation
```

debe implementar `Simulation` y conservar un constructor público sin parámetros.

El método principal de evaluación es:

```java
SimulationResult execute(SimulationConfig config) throws InterruptedException;
```

Los grupos pueden crear libremente sus clases y paquetes internos. No deben modificar las firmas ni el comportamiento de las clases del paquete `api`.

## Archivos de resultados

Todos los archivos se escriben dentro de `output.directory`.

### eventos.csv

Debe utilizar exactamente esta cabecera:

```text
sequence;elapsedMs;thread;orderId;stage;event;fromState;toState;printer
```

Debe registrarse un evento por cambio de estado. La secuencia debe ser creciente, única y respetar el orden de escritura del archivo.

Se registran únicamente transiciones de órdenes, no cambios de estado internos de las impresoras. Los valores admitidos son:

- `stage`: `INITIALIZATION`, `ASSIGNMENT`, `VALIDATION`, `PRINTING` o `QUALITY_CONTROL`.
- `event`: `ORDER_CREATED` para la creación inicial u `ORDER_STATE_CHANGED` para cualquier otra transición.

Cada orden genera inicialmente una fila `ORDER_CREATED`, con `fromState` y `printer` vacíos, y `toState=CREATED`. En las transiciones posteriores, `fromState` y `toState` contienen estados de `OrderState`; `printer` contiene el identificador histórico de la impresora usada por la orden, o queda vacío si no corresponde. `thread` identifica el hilo que efectuó la transición y `elapsedMs` es no negativo. Las filas deben escribirse en el mismo orden en que las transiciones que describen quedan confirmadas.

Ejemplo:

```text
1;0;main;1;INITIALIZATION;ORDER_CREATED;;CREATED;
2;3;assignment-1;1;ASSIGNMENT;ORDER_STATE_CHANGED;CREATED;WAITING_VALIDATION;P-0-0
3;10;validation-1;1;VALIDATION;ORDER_STATE_CHANGED;WAITING_VALIDATION;READY_TO_PRINT;P-0-0
```

### elementos.csv

Debe utilizar exactamente esta cabecera:

```text
orderId;finalState;printer;assignmentCount;validationCount;printingCount;qualityControlCount
```

Debe contener una fila por orden, ordenada por `orderId`.

La columna `printer` conserva el identificador de la impresora utilizada por la orden como dato histórico, incluso después de liberarla. En cambio, una impresora liberada debe informar `assignedOrderId` vacío en su `PrinterSnapshot`.

### resumen.properties

Debe contener al menos:

```properties
totalOrders=500
processedOrders=500
approvedOrders=0
rejectedOrders=0
printFailedOrders=0
defectiveOrders=0
durationMillis=0
allThreadsTerminated=true
remainingIntermediateOrders=0
```

Los valores del ejemplo no representan un resultado esperado.

## Análisis solicitado

El informe debe incluir:

- Identificación de todos los recursos compartidos.
- Explicación de las condiciones de carrera posibles.
- Estrategia de sincronización utilizada.
- Condiciones de espera y despertar de los hilos.
- Mecanismo de terminación.
- Análisis teórico del tiempo de ejecución.
- Resultados de múltiples ejecuciones.
- Comparación entre tiempos teóricos y observados.
- Análisis del efecto de cambiar la cantidad de hilos y las demoras.
- Justificación de las decisiones de diseño.

## Diagramas

Se deben entregar:

- `docs/diagrama-clases.png`.
- `docs/diagrama-secuencia.png`.

Se pueden entregar más de 1 diagrama de secuencia si el grupo lo considera necesario.

## Entregables

El repositorio debe contener:

- Código fuente Java.
- `pom.xml`.
- Configuración oficial.
- Tests desarrollados por el grupo.
- Informe en `docs/informe.pdf`.
- Diagramas solicitados.
- Resultados de una ejecución oficial.
- `integrantes.json` completo.
- `README.md` con instrucciones de compilación y ejecución.

## Repositorio Git y entrega en el aula virtual

El uso de un repositorio Git es obligatorio.

La entrega se identifica mediante el tag:

```text
{nombre-del-grupo}-entrega-tp1
```

El ZIP que se sube al aula virtual debe generarse desde ese tag:

```bash
git archive --format=zip --prefix=TP1-GRUPO-XX/ --output=TP1-GRUPO-XX.zip {nombre-del-grupo}-entrega-tp1
```

El contenido del ZIP y el tag deben coincidir. La cátedra debe tener acceso al repositorio.

## Comandos de evaluación

La suite pública se ejecuta mediante:

```bash
mvn clean test
```

Aprobar la suite pública es necesario pero no suficiente: no acredita por sí solo concurrencia efectiva ni el cumplimiento de todos los invariantes que evaluará la cátedra.

El proyecto se construye y ejecuta mediante:

```bash
mvn clean package
java -jar target/tp1.jar
```

El proyecto debe funcionar desde un clon limpio sin configuración adicional.

## Presentación

Cada grupo realizará una presentación de hasta tres minutos. Deberá explicar:

- La arquitectura general.
- Los recursos protegidos.
- La estrategia de sincronización.
- Un resultado relevante del análisis experimental.

La cátedra podrá realizar preguntas sobre el código o los resultados del grupo.
