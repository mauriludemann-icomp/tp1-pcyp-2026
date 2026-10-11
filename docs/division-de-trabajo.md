# División del trabajo e implementación

Este documento reparte el trabajo restante entre los 6 integrantes y fija, clase por clase, qué hay que
implementar: atributos, constructor, métodos, qué hilos los usan y qué reglas de sincronización deben
respetar. La idea es que cada parte se pueda programar de forma independiente y que al integrar todo
encaje sin sorpresas.

Documentos relacionados: [diagrama-de-clases.md](diagrama-de-clases.md),
[diagrama-de-estados.md](diagrama-de-estados.md) y [diagrama-de-secuencia.md](diagrama-de-secuencia.md).

---

## 1. Resumen de la división

| # | Parte | Clases | Tests propios | Sección del informe | Otras tareas |
|---|---|---|---|---|---|
| 1 | Arquitectura, dominio e integración | `ConcurrentSimulation`, `Order`, `Printer`, `Stage`, `Worker` | `OrderTest`, `PrinterTest` | Mecanismo de terminación; justificación de decisiones de diseño | Integración final, armado del informe y entrega |
| 2 | Colas entre etapas | `StageQueue` | `StageQueueTest` (concurrente) | Recursos compartidos; condiciones de carrera | Presentación (con la parte 3) |
| 3 | Gestión de impresoras | `PrinterManager` | `PrinterManagerTest` (concurrente) | Estrategia de sincronización; condiciones de espera y despertar | Presentación (con la parte 2) |
| 4 | Asignación y validación | `AssignmentWorker`, `ValidationWorker` (y el ciclo común de los workers) | `AssignmentWorkerTest`, `ValidationWorkerTest` | Análisis teórico del tiempo de ejecución | `README.md` |
| 5 | Impresión y control de calidad | `PrintingWorker`, `QualityControlWorker` | `PrintingWorkerTest`, `QualityControlWorkerTest`, `InvariantsTest` | Resultados de múltiples ejecuciones; comparación teórico vs. observado | Ejecución oficial y resultados; clase de experimentos (ver [sección 5](#5-análisis-teórico-del-tiempo-punto-de-partida-para-la-parte-4)) |
| 6 | Registro y resultados | `SimulationLogger` | `SimulationLoggerTest` | Efecto de cambiar la cantidad de hilos y las demoras; diagramas incluidos y explicados | Exportación de los diagramas |

Además del código, quedan estos entregables y tareas del enunciado:

| Entregable o tarea | Responsable |
|---|---|
| `docs/diagrama-clases.png` (diagrama completo en un solo PNG; ya está generado, se vuelve a exportar si cambia el diagrama) y `docs/diagrama-secuencia.png` | 6 |
| Diagramas incluidos y explicados dentro del informe (lo pide `docs/README.md` de la plantilla) | 6 |
| `README.md` con instrucciones de compilación y ejecución | 4 |
| Resultados de una ejecución oficial en `resultados/` | 5 |
| `integrantes.json` completo | 1 (cada uno completa sus datos) |
| `docs/informe.pdf` | Cada uno escribe su sección; 1 arma el documento final |
| Presentación de hasta 3 minutos | 2 y 3 (ver [sección 8](#8-preparación-para-la-defensa)) |
| Reemplazar `docs/README.md` de la plantilla por los entregables, como indica ese archivo | 1 |
| Tag `{nombre-del-grupo}-entrega-tp1`, ZIP generado con `git archive` desde el tag, subida al aula virtual y acceso de la cátedra al repositorio | 1 |
| Revisión cruzada de concurrencia | Ver [sección 7](#7-revisión-cruzada) |
| Sesión de comprensión del diseño | Todos (ver [sección 8](#8-preparación-para-la-defensa)) |

Los PNG se generan con mermaid-cli (necesita Node.js 18.19 o más nuevo), copiando el bloque Mermaid de
cada diagrama a un archivo `.mmd`:

```bash
npx -y @mermaid-js/mermaid-cli@11 -b '#0d1117' -w 3800 -s 1.5 -i clases.mmd -o docs/diagrama-clases.png
npx -y @mermaid-js/mermaid-cli@11 -b '#0d1117' -w 2400 -s 1.5 -i secuencia.mmd -o docs/diagrama-secuencia.png
```

Si en Linux Chrome no arranca por el *sandbox*, agregar `-p puppeteer.json`, con un archivo
`puppeteer.json` que contenga `{"args": ["--no-sandbox"]}`.

---

## 2. Decisiones comunes

### 2.1 Paquete

Todas las clases nuevas van en el paquete que ya existe:

```text
ar.edu.unc.fcefyn.pcp.tp1.solution
```

Al estar todas en el mismo paquete, las clases internas pueden ser **package-private** (sin
modificador): solo `ConcurrentSimulation` necesita ser `public`. Los atributos y métodos se declaran con
la visibilidad del diagrama de clases: `-` es `private` y `+` es `public` (dentro de una clase
package-private, un método `public` igual se ve solo desde el paquete). No se modifica nada del paquete
`api`.

### 2.2 Cambios respecto del diagrama de clases

El diagrama acordado se mantiene. Para que la implementación no quede librada al azar, este documento
**agrega o ajusta** los siguientes detalles, que ya están reflejados en [diagrama-de-clases.md](diagrama-de-clases.md):

| Cambio | Motivo |
|---|---|
| Nuevo enum `Stage` (`INITIALIZATION`, `ASSIGNMENT`, `VALIDATION`, `PRINTING`, `QUALITY_CONTROL`) | La columna `stage` de `eventos.csv` lo necesita y no existe en el paquete `api`. |
| `Worker` extiende `Runnable` | Para poder crear `new Thread(worker, nombre)`. |
| Los workers reciben `SimulationConfig` | Necesitan la demora de su etapa y `OutcomeDecider` necesita la configuración. |
| `PrinterManager` recibe la `Order` y registra el evento dentro de su lock | Para que el cambio de la impresora, el cambio de la orden y la fila del CSV ocurran juntos (ver [regla R3](#23-reglas-comunes-obligatorias)). |
| `PrinterManager` tiene una cola de impresoras disponibles además de la lista | Para reservar en O(1) y saber si hay que esperar. |
| `StageQueue` tiene `size()` | Para verificar al final que no quedan órdenes en registros intermedios. |
| `SimulationLogger.log(...)` se concreta en `logCreated` y `logTransition` | Son los dos tipos de evento que pide el enunciado. |
| `Order` y `Printer` tienen métodos (el diagrama solo tenía atributos) | Validan las transiciones y generan los snapshots. |
| Todas las clases tienen constructor con sus dependencias | Así queda definido qué recibe cada objeto al crearse y nadie depende de *setters*. |
| Los workers tienen `process(Order order)` | Es el paso propio de cada etapa dentro del ciclo común (ver [3.8](#38-ciclo-común-de-los-workers--parte-4)). |
| `ConcurrentSimulation` ya no guarda la lista de impresoras | `createPrinters()` se la entrega directamente al `PrinterManager`, que es su único dueño. |
| Nueva relación `StageQueue o-- Order` | La cola contiene órdenes (atributo `Queue<Order>`); faltaba dibujarla. |
| Se dibuja la interfaz `Simulation`, que `ConcurrentSimulation` implementa | La exige el enunciado (sección "API obligatoria"); faltaba en el diagrama. |
| `AssignmentWorker` y `PrintingWorker` no reciben el logger | Todas sus transiciones pasan por `PrinterManager`, que las registra (R3); el logger no tendría uso. |
| Las relaciones que no son atributos se dibujan como dependencias (línea punteada): `ConcurrentSimulation` con `Worker` y con `SimulationResult`, los workers con `OutcomeDecider` y `SimulationLogger` con `Stage` | Una línea sólida indica que la clase guarda un atributo de ese tipo. `ConcurrentSimulation` guarda los hilos (`List<Thread>`), no los workers, y crea y devuelve el resultado sin guardarlo; `OutcomeDecider` se usa con métodos estáticos y `Stage` llega como parámetro. |

### 2.3 Reglas comunes obligatorias

Estas reglas valen para todas las partes. Si alguien necesita romper una, se discute en grupo.

| # | Regla | Por qué |
|---|---|---|
| R1 | **Una orden la modifica un solo hilo a la vez**: el que la sacó de una cola. Después de hacer `put` en la cola siguiente, ese hilo no vuelve a tocarla. | Así `Order` no necesita lock. El `put`/`take` sincronizado garantiza que el siguiente hilo ve los cambios. |
| R2 | **`put` en la cola siguiente es lo último** que hace un worker con una orden, después de cambiar el estado y registrar el evento. | Si no, el worker siguiente podría registrar su evento antes y el CSV quedaría desordenado. |
| R3 | **Toda transición que toca una impresora se hace dentro de `PrinterManager`**, que en la misma sección crítica cambia la impresora, cambia el estado de la orden y registra el evento. | El enunciado exige que las filas se escriban en el orden en que se confirman las transiciones. Si la impresora se liberara antes de registrar el evento, otro hilo podría reservarla y registrar su evento antes, y el CSV mostraría la impresora asignada a dos órdenes a la vez. |
| R4 | **Orden de locks: `PrinterManager` → `SimulationLogger`.** `SimulationLogger` nunca llama a otro objeto sincronizado. Ningún worker llama a `PrinterManager` o al logger mientras tiene el lock de una `StageQueue`. | Evita deadlocks: no hay ciclos posibles en la espera de locks. |
| R5 | **Toda espera es `while (condición) wait();`**, nunca `if`. | Protege contra despertares espurios y contra que otro hilo se adelante. |
| R6 | **Nunca `sleep` dentro de un `synchronized`.** La demora de cada etapa se aplica fuera de cualquier lock. | Un `sleep` con el lock tomado frena a todos los hilos que comparten ese objeto. |
| R7 | **`InterruptedException` en un worker:** restaurar el flag con `Thread.currentThread().interrupt()` y salir de `run()`. No se ignora ni se usa para terminar. | Es la práctica correcta; la terminación normal es por `take()` → `null`. |
| R8 | **Nombres de hilos:** `assignment-N`, `validation-N`, `printing-N`, `quality-control-N`, con N desde 1. | Aparecen en la columna `thread` de `eventos.csv`, como en el ejemplo del enunciado. |
| R9 | **Solo herramientas permitidas:** `Thread`, `Runnable`, `synchronized`, `wait`/`notifyAll`, colecciones comunes (`ArrayList`, `ArrayDeque`, `EnumMap`). Nada de `java.util.concurrent` salvo lo listado en el enunciado, nada de imports con `*`. | Lo verifica `SourceRestrictionsTest`. |
| R10 | **Nada de valores fijos en el código:** cantidades, hilos, demoras, probabilidades y rutas salen siempre de `SimulationConfig`. | La cátedra prueba con otras configuraciones. |

### 2.4 Mapa de recursos compartidos

| Objeto | Lo usan | Protección |
|---|---|---|
| Cada `StageQueue` | El hilo que la alimenta y los workers que la consumen | `synchronized` sobre la propia cola + `wait`/`notifyAll` |
| `PrinterManager` (y todas las `Printer`) | Workers de asignación, validación e impresión | `synchronized` sobre el `PrinterManager` + `wait`/`notifyAll` |
| `SimulationLogger` | Todos los hilos (los de asignación e impresión, a través de `PrinterManager`) | `synchronized` sobre el logger |
| `Order` | Un solo hilo a la vez (R1) | Sin lock: confinamiento + traspaso por cola sincronizada |
| `Printer` | Solo a través de `PrinterManager` | El lock del `PrinterManager` |

### 2.5 Tests propios

Reglas para todos los tests del grupo. Si no se cumplen, `mvn clean test` o `mvn clean package` fallan
o se cuelgan.

| Regla | Por qué |
|---|---|
| Van en `src/test/java/ar/edu/unc/fcefyn/pcp/tp1/solution/`, el mismo paquete que las clases. | Las clases internas son package-private ([2.1](#21-paquete)): desde otro paquete los tests no compilan. El paquete `publictests` es de la cátedra y no se toca. |
| Toda simulación completa usa una configuración con **más impresoras que órdenes que terminan en `PRINT_FAILED`**. Con una sola impresora, `printSuccessPercentage = 100`, como el test público `reusesOnePrinterWhenNoPrintingFails`. Se puede comprobar en el propio test contando con `OutcomeDecider`. | Es la garantía del enunciado. Si no se cumple, las impresoras se agotan y los asignadores esperan para siempre. Ejemplo verificado: con 1 impresora, 20 órdenes, probabilidades 85/90/95 y la semilla oficial, dos órdenes terminan en `PRINT_FAILED`; la primera deja la granja sin impresoras y el test se cuelga. |
| Demoras en 0 (o de pocos milisegundos). | `mvn clean package` también corre los tests; con las demoras oficiales, cada simulación completa tardaría 30 s. |
| La simulación completa va dentro de `assertTimeoutPreemptively`, como en los tests públicos. En los tests con hilos propios, `join` con timeout y verificar `!isAlive()`. | Un deadlock tiene que hacer fallar el test en segundos, no colgar el build. |
| Los tests concurrentes se repiten (por ejemplo con `@RepeatedTest`). | Un error de concurrencia puede no aparecer en una sola ejecución. |

---

## 3. Especificación por clase

Las firmas están escritas en Java para que no haya ambigüedad, pero el cuerpo de cada método se
describe en pasos: lo implementa quien tenga la parte.

### 3.1 `Stage` — Parte 1

Enum con las etapas que aparecen en `eventos.csv`.

```java
enum Stage { INITIALIZATION, ASSIGNMENT, VALIDATION, PRINTING, QUALITY_CONTROL }
```

### 3.2 `Order` — Parte 1

Orden de impresión. **No es thread-safe a propósito**: la usa un hilo a la vez (R1).

**Atributos**

| Atributo | Tipo | Valor inicial | Notas |
|---|---|---|---|
| `id` | `int` (final) | el recibido | De 1 a `totalOrders`. |
| `state` | `OrderState` | `CREATED` | |
| `assignedPrinter` | `Printer` | `null` | **No se borra al liberar la impresora**: es el dato histórico que piden `eventos.csv` y `elementos.csv`. |
| `assignmentCount`, `validationCount`, `printingCount`, `qualityControlCount` | `int` | 0 | Cada uno debe terminar en 0 o 1. |

**Constructor**

```java
Order(int id)
```

- Lanza `IllegalArgumentException` si `id <= 0`.

**Métodos**

| Firma | Qué hace |
|---|---|
| `int getId()` | Devuelve el id. |
| `OrderState getState()` | Devuelve el estado actual. |
| `Printer getAssignedPrinter()` | Devuelve la impresora asignada (o `null` si nunca tuvo). |
| `String getAssignedPrinterId()` | `null` si no tiene impresora; si no, el id de la impresora. |
| `void transition(OrderState expectedFrom, OrderState to)` | Si `state != expectedFrom`, lanza `IllegalStateException` con un mensaje que incluya id, estado actual y esperado. Si no, pone `state = to`. |
| `void assignPrinter(Printer printer)` | Lanza `IllegalStateException` si ya tenía una impresora. Si no, la guarda. |
| `void incrementAssignmentCount()` y equivalentes para las otras 3 etapas | Incrementan el contador. Si el contador ya era 1, lanzan `IllegalStateException` (invariante 3). |
| `OrderSnapshot toSnapshot()` | `new OrderSnapshot(id, state, getAssignedPrinterId(), los 4 contadores)`. |

`transition` con estado esperado es la red de seguridad del grupo: si alguien procesa una orden en la
etapa equivocada, explota en el momento en vez de producir resultados inconsistentes.

**Tests sugeridos (`OrderTest`)**

1. Una orden nueva está en `CREATED`, sin impresora y con los 4 contadores en 0; `new Order(0)` lanza `IllegalArgumentException`.
2. `transition` con el estado esperado correcto cambia el estado; con otro, lanza `IllegalStateException` y el estado no cambia.
3. Cada `increment…` funciona una vez y la segunda llamada lanza `IllegalStateException`.
4. `assignPrinter` sobre una orden que ya tiene impresora lanza `IllegalStateException`.
5. `toSnapshot()` conserva el id de la impresora después de que la impresora se libera (dato histórico).

### 3.3 `Printer` — Parte 1

Impresora de la matriz. **No es thread-safe por sí misma**: solo la modifica `PrinterManager` con su
lock tomado.

**Atributos**

| Atributo | Tipo | Valor inicial | Notas |
|---|---|---|---|
| `id` | `String` (final) | `"P-" + row + "-" + column` | Formato del ejemplo del enunciado: `P-0-0`. |
| `row`, `column` | `int` (final) | los recibidos | Desde 0. |
| `state` | `PrinterState` | `AVAILABLE` | |
| `usageCount` | `int` | 0 | |
| `assignedOrderId` | `Integer` | `null` | `null` cuando no está `RESERVED`. |

**Constructor**

```java
Printer(int row, int column)
```

**Métodos**

| Firma | Precondición | Qué hace |
|---|---|---|
| `String getId()`, `PrinterState getState()`, `int getUsageCount()`, `Integer getAssignedOrderId()` | — | Getters. No hay getters de fila y columna: nadie los usa y la posición ya está en el id. |
| `void reserve(int orderId)` | `state == AVAILABLE` | `state = RESERVED`, `usageCount++`, `assignedOrderId = orderId`. |
| `void release()` | `state == RESERVED` | `state = AVAILABLE`, `assignedOrderId = null`. |
| `void markOutOfService()` | `state == RESERVED` | `state = OUT_OF_SERVICE`, `assignedOrderId = null`. |
| `PrinterSnapshot toSnapshot()` | — | `new PrinterSnapshot(id, state, usageCount, assignedOrderId)`. |

Si no se cumple la precondición: `IllegalStateException`.

**Tests sugeridos (`PrinterTest`)**

1. `new Printer(2, 3)` tiene id `P-2-3`, está `AVAILABLE`, con `usageCount` 0 y sin orden asignada.
2. `reserve(7)` la deja `RESERVED`, con `usageCount` 1 y `assignedOrderId` 7; `release()` la vuelve a `AVAILABLE` con `assignedOrderId` en `null`, y `usageCount` no baja.
3. `markOutOfService()` la deja `OUT_OF_SERVICE` con `assignedOrderId` en `null`, y después no se puede reservar.
4. `reserve` sobre una impresora que no está `AVAILABLE`, y `release` o `markOutOfService` sobre una que no está `RESERVED`, lanzan `IllegalStateException`.
5. `toSnapshot()` refleja los 4 campos.

### 3.4 `Worker` — Parte 1

```java
interface Worker extends Runnable { }
```

No agrega métodos: `run()` viene de `Runnable`. Sirve para que `ConcurrentSimulation` trate a los cuatro
workers de la misma forma.

### 3.5 `StageQueue` — Parte 2

Cola entre dos etapas. Monitor: todos los métodos son `synchronized` sobre la propia instancia.

**Atributos**

| Atributo | Tipo | Valor inicial | Notas |
|---|---|---|---|
| `name` | `String` (final) | el recibido | Para mensajes de error y depuración. |
| `orders` | `Queue<Order>` (final) | `new ArrayDeque<>()` | FIFO, no thread-safe: la protege el monitor. |
| `closed` | `boolean` | `false` | `true` cuando ya no van a llegar más órdenes. |

**Constructor**

```java
StageQueue(String name)
```

**Métodos**

| Firma | Comportamiento |
|---|---|
| `synchronized void put(Order order)` | Si `closed`, lanza `IllegalStateException` (sería un error de integración). Agrega la orden al final y hace `notifyAll()`. |
| `synchronized Order take() throws InterruptedException` | Mientras la cola esté vacía **y** abierta: `wait()`. Si está vacía (y por lo tanto cerrada), devuelve `null`. Si no, saca y devuelve la primera orden. |
| `synchronized void close()` | `closed = true` y `notifyAll()`. Llamarlo dos veces no es un error. |
| `synchronized int size()` | Cantidad de órdenes en la cola. Lo usa `ConcurrentSimulation` al final. |

**Puntos a cuidar**

- `take()` devuelve órdenes mientras haya, **aunque la cola esté cerrada**: cerrar no descarta nada.
- `close()` usa `notifyAll()` porque pueden estar esperando varios consumidores y todos deben despertar
  para terminar.
- Nunca se llama a otro objeto sincronizado desde dentro de estos métodos (R4).

**Tests sugeridos (`StageQueueTest`)**

1. FIFO con un solo hilo.
2. `take()` sobre una cola cerrada y vacía devuelve `null` sin bloquear.
3. Cola cerrada con órdenes: `take()` devuelve todas y después `null`.
4. Varios consumidores bloqueados en `take()`: `close()` los despierta a todos y terminan (usar `join` con timeout y verificar `!isAlive()`).
5. N productores y M consumidores con miles de órdenes: no se pierde ni se duplica ninguna.
6. `put()` después de `close()` lanza excepción.

### 3.6 `PrinterManager` — Parte 3

Administra la matriz de impresoras. Monitor: todos los métodos públicos son `synchronized` sobre la
instancia. Es el único que modifica `Printer` (y, en las transiciones con impresora, también `Order`).

**Atributos**

| Atributo | Tipo | Notas |
|---|---|---|
| `printers` | `List<Printer>` (final) | Todas las impresoras, en orden fila-columna. Nunca cambia de tamaño. |
| `availablePrinters` | `Deque<Printer>` (final, `ArrayDeque`) | Solo las `AVAILABLE`. Inicialmente todas. |
| `logger` | `SimulationLogger` (final) | Para registrar el evento dentro de la sección crítica (R3). |

**Constructor**

```java
PrinterManager(List<Printer> printers, SimulationLogger logger)
```

- Copia la lista (`new ArrayList<>(printers)`) y carga todas en `availablePrinters`.
- Lanza `IllegalArgumentException` si la lista está vacía.

**Métodos**

| Firma | Lo llama | Pasos (todo dentro del `synchronized`) |
|---|---|---|
| `synchronized Printer reservePrinter(Order order) throws InterruptedException` | `AssignmentWorker` (O1) | 1. Validar que la orden esté en `CREATED` y sin impresora; si no, `IllegalStateException`. 2. `while (availablePrinters.isEmpty()) wait();` 3. Sacar una impresora de `availablePrinters`. 4. `printer.reserve(order.getId())`. 5. `order.assignPrinter(printer)`. 6. `order.transition(CREATED, WAITING_VALIDATION)`. 7. `logger.logTransition(order, Stage.ASSIGNMENT, CREATED, WAITING_VALIDATION)`. 8. Devolver la impresora. |
| `synchronized void releasePrinter(Order order, Stage stage, OrderState to)` | `ValidationWorker` (O3) y `PrintingWorker` (O4) | 1. Deducir el estado esperado de la combinación: `VALIDATION` + `REJECTED` → `WAITING_VALIDATION`; `PRINTING` + `PRINTED` → `READY_TO_PRINT`. Cualquier otra combinación: `IllegalArgumentException`. 2. Validar que la orden esté en el estado esperado y que su impresora esté reservada para ella (`assignedOrderId` igual al id de la orden); si no, `IllegalStateException`. 3. `order.transition(esperado, to)`. 4. `printer.release()` y `availablePrinters.addLast(printer)`. 5. `logger.logTransition(order, stage, esperado, to)`. 6. `notifyAll()`. |
| `synchronized void markOutOfService(Order order)` | `PrintingWorker` (O5) | 1. Validar que la orden esté en `READY_TO_PRINT` y que su impresora esté reservada para ella; si no, `IllegalStateException`. 2. `order.transition(READY_TO_PRINT, PRINT_FAILED)`. 3. `printer.markOutOfService()` (no vuelve a `availablePrinters`). 4. `logger.logTransition(order, Stage.PRINTING, READY_TO_PRINT, PRINT_FAILED)`. **Sin** `notifyAll()`: no hay una impresora nueva disponible. |
| `synchronized List<PrinterSnapshot> snapshots()` | `ConcurrentSimulation` al final | Lista de `toSnapshot()` de todas las impresoras, en orden. |

**Puntos a cuidar**

- En los tres métodos se validan **primero** las precondiciones y recién después se modifica algo. Así, un
  error de integración lanza una excepción sin dejar la orden y la impresora a medio cambiar.
- La validación de `releasePrinter` usa el estado **esperado** según la etapa, no el estado actual de la
  orden: comparar el estado actual consigo mismo nunca detectaría un error.
- El enunciado garantiza que no se agotan las impresoras operativas, así que no hace falta resolver el
  caso en que todas quedan fuera de servicio.
- Las impresoras se pueden reutilizar en cualquier orden: el enunciado no exige que coincida qué
  impresora usa cada orden.
- `assignedOrderId` es `Integer`: compararlo con `Objects.equals(printer.getAssignedOrderId(), order.getId())`.
  Con `==` entre dos `Integer`, la comparación funciona hasta 127 (Java reutiliza esos objetos) y falla
  desde la orden 128: los tests públicos (40 y 12 órdenes) no lo detectan y la corrida oficial sí.

**Tests sugeridos (`PrinterManagerTest`)**

1. Con 1 impresora: dos hilos que reservan → el segundo queda bloqueado hasta que el primero libera.
2. Muchos hilos reservando y liberando: nunca hay dos órdenes con la misma impresora al mismo tiempo, y la suma de `usageCount` es igual a la cantidad de reservas.
3. Después de `markOutOfService`, esa impresora no vuelve a entregarse nunca.
4. Al final, ninguna impresora queda `RESERVED` y todas tienen `assignedOrderId == null`.

### 3.7 `SimulationLogger` — Parte 6

Registra los eventos en memoria durante la simulación y escribe los tres archivos al final. Monitor:
los métodos son `synchronized`.

Se guarda en memoria (y no se escribe el archivo en cada evento) para no hacer E/S dentro de secciones
críticas. Como `sequence` se asigna y la línea se agrega a la lista en el mismo bloque sincronizado, el
orden de la lista es el orden de la secuencia, que es lo que exige el enunciado.

**Atributos**

| Atributo | Tipo | Notas |
|---|---|---|
| `startNanos` | `long` (final) | `System.nanoTime()` del inicio de la simulación. |
| `sequence` | `long` | Último número usado. Empieza en 0; el primer evento es 1. |
| `eventLines` | `List<String>` (final, `ArrayList`) | Las filas de `eventos.csv` sin la cabecera. |

**Constructor**

```java
SimulationLogger(long startNanos)
```

**Métodos de registro (los usan los hilos durante la simulación)**

| Firma | Fila generada |
|---|---|
| `synchronized void logCreated(Order order)` | `seq;elapsed;thread;id;INITIALIZATION;ORDER_CREATED;;CREATED;` |
| `synchronized void logTransition(Order order, Stage stage, OrderState from, OrderState to)` | `seq;elapsed;thread;id;stage;ORDER_STATE_CHANGED;from;to;printer` |

- `seq`: `++sequence`.
- `elapsed`: `(System.nanoTime() - startNanos) / 1_000_000`. Nunca negativo.
- `thread`: `Thread.currentThread().getName()`.
- `printer`: `order.getAssignedPrinterId()`, o vacío si es `null`.
- Separador `;`, sin espacios. Siempre 9 campos (los vacíos quedan como `;;`).

**Métodos de escritura (los llama `ConcurrentSimulation` al final, después de los `join`)**

| Firma | Archivo | Contenido |
|---|---|---|
| `synchronized void writeEvents(Path outputDirectory) throws IOException` | `eventos.csv` | Cabecera exacta `sequence;elapsedMs;thread;orderId;stage;event;fromState;toState;printer` y luego `eventLines` en orden. |
| `void writeElements(Path outputDirectory, List<OrderSnapshot> orders) throws IOException` | `elementos.csv` | Cabecera exacta `orderId;finalState;printer;assignmentCount;validationCount;printingCount;qualityControlCount` y una fila por orden, **ordenadas por `orderId`**. `printer` vacío si es `null`. |
| `void writeSummary(Path outputDirectory, SimulationResult result, int remainingIntermediateOrders) throws IOException` | `resumen.properties` | Una línea `clave=valor` por cada clave: `totalOrders`, `processedOrders`, `durationMillis` y `allThreadsTerminated` salen de `result`; `approvedOrders`, `rejectedOrders`, `printFailedOrders` y `defectiveOrders`, de `result.totalsByState()` con `getOrDefault(estado, 0)`; `remainingIntermediateOrders` es el parámetro. |

- Los tres métodos crean el directorio si no existe (`Files.createDirectories`).
- Escribir en UTF-8 con `Files.newBufferedWriter` dentro de un try-with-resources, así el archivo queda
  cerrado al terminar (lo exige el enunciado).

**Tests sugeridos (`SimulationLoggerTest`)**

1. Formato exacto de cada tipo de fila y de las cabeceras (9 campos, separador `;`).
2. Muchos hilos registrando a la vez: las secuencias son 1..N sin huecos ni repetidos y en orden en el archivo.
3. `elementos.csv` ordenado por `orderId` aunque la lista llegue desordenada.

### 3.8 Ciclo común de los workers — Parte 4

Los cuatro workers siguen **el mismo esquema** en `run()`. La parte 4 lo implementa primero en
`AssignmentWorker` y los demás lo copian tal cual, cambiando solo `process`.

```text
run():
  try:
    repetir:
      1. order = <cola de entrada>.take()
      2. si order == null → salir (la cola se cerró y está vacía)
      3. si delay > 0 → Thread.sleep(delay)          // fuera de todo lock (R6)
      4. process(order)                              // propio de cada etapa
  catch InterruptedException:
      Thread.currentThread().interrupt()             // R7
```

El paso propio de cada etapa tiene la misma firma en los cuatro workers:

```java
private void process(Order order) throws InterruptedException
```

Solo `AssignmentWorker` puede esperar dentro de `process` (en `reservePrinter`), pero se declara igual en
todos para que el ciclo sea idéntico.

- La demora se aplica **una vez por orden procesada**, como pide el enunciado.
- El incremento del contador de la etapa lo hace el worker **antes** de la transición; la orden es suya (R1).
- Si `process` incluye `put`, va al final (R2).

**Atributos** (todos `final`, recibidos por constructor; los nombres son los del diagrama de clases):

| Worker | Cola de entrada | Cola de salida | Otros atributos |
|---|---|---|---|
| `AssignmentWorker` | `assignmentQueue` | `validationQueue` | `printerManager`, `config` |
| `ValidationWorker` | `validationQueue` | `printingQueue` | `printerManager`, `logger`, `config` |
| `PrintingWorker` | `printingQueue` | `qualityQueue` | `printerManager`, `config` |
| `QualityControlWorker` | `qualityQueue` | — | `logger`, `config` |

`AssignmentWorker` y `PrintingWorker` no tienen logger: todas sus transiciones pasan por
`PrinterManager`, que las registra (R3). El evento igual sale con el nombre del hilo del worker, porque
`PrinterManager` se ejecuta en el hilo que lo llama.

**Tests de los workers (partes 4 y 5)**

Todos siguen el mismo esquema, con las clases reales y respetando las reglas de [2.5](#25-tests-propios):

1. Crear las colas, el `PrinterManager` y el logger.
2. Llevar cada orden al estado de entrada de la etapa con los mismos métodos que usa la simulación. Por
   ejemplo, para validación, `printerManager.reservePrinter(order)`; para impresión, además
   `order.transition(WAITING_VALIDATION, READY_TO_PRINT)`.
3. `put` de las órdenes en la cola de entrada y `close()`.
4. Correr el worker en 2 hilos, hacer `join` con timeout y verificar que terminaron.
5. Verificar el estado y el contador de cada orden, en qué cola quedó y el estado de las impresoras.

Con probabilidades de 0 % y 100 % el resultado no depende de la semilla, así que cada rama se prueba por
separado:

| Test | Casos |
|---|---|
| `AssignmentWorkerTest` | Con tantas impresoras como órdenes: todas pasan a `WAITING_VALIDATION`, quedan en `validationQueue` con impresoras distintas y `assignmentCount` en 1. Con 1 impresora y 3 órdenes: el test toma cada orden de `validationQueue` y la libera con `releasePrinter(orden, VALIDATION, REJECTED)`; al final las 3 fueron asignadas y la impresora tiene `usageCount` 3. Si `releasePrinter` no despertara al worker, el test se colgaría y fallaría por timeout. |
| `ValidationWorkerTest` | Con 100 %: todas pasan a `READY_TO_PRINT`, quedan en `printingQueue` y sus impresoras siguen `RESERVED`. Con 0 %: todas terminan en `REJECTED`, `printingQueue` queda vacía y las impresoras vuelven a `AVAILABLE`. En ambos casos, `validationCount` en 1. |
| `PrintingWorkerTest` | Con 100 %: todas pasan a `PRINTED`, quedan en `qualityQueue` y las impresoras vuelven a `AVAILABLE`. Con 0 %: todas terminan en `PRINT_FAILED` y sus impresoras quedan `OUT_OF_SERVICE`. En ambos casos, `printingCount` en 1. |
| `QualityControlWorkerTest` | Con 100 %: todas terminan en `APPROVED`; con 0 %, en `DEFECTIVE`. `qualityControlCount` en 1 y las impresoras no cambian. |

### 3.9 `AssignmentWorker` — Parte 4

```java
AssignmentWorker(StageQueue assignmentQueue, StageQueue validationQueue,
                 PrinterManager printerManager, SimulationConfig config)
```

- Demora: `config.assignmentDelayMillis()`.
- `process(order)`:
  1. `order.incrementAssignmentCount()`.
  2. `printerManager.reservePrinter(order)` (puede esperar; hace la transición O1 y registra el evento).
  3. `validationQueue.put(order)`.

### 3.10 `ValidationWorker` — Parte 4

```java
ValidationWorker(StageQueue validationQueue, StageQueue printingQueue,
                 PrinterManager printerManager, SimulationLogger logger, SimulationConfig config)
```

- Demora: `config.validationDelayMillis()`.
- `process(order)`:
  1. `order.incrementValidationCount()`.
  2. Si `OutcomeDecider.isModelValid(order.getId(), config)`:
     - `order.transition(WAITING_VALIDATION, READY_TO_PRINT)`.
     - `logger.logTransition(order, Stage.VALIDATION, WAITING_VALIDATION, READY_TO_PRINT)`.
     - `printingQueue.put(order)`.
  3. Si no: `printerManager.releasePrinter(order, Stage.VALIDATION, REJECTED)`. La orden termina acá.

### 3.11 `PrintingWorker` — Parte 5

```java
PrintingWorker(StageQueue printingQueue, StageQueue qualityQueue,
               PrinterManager printerManager, SimulationConfig config)
```

- Demora: `config.printingDelayMillis()`.
- `process(order)`:
  1. `order.incrementPrintingCount()`.
  2. Si `OutcomeDecider.isPrintSuccessful(order.getId(), config)`:
     - `printerManager.releasePrinter(order, Stage.PRINTING, PRINTED)`.
     - `qualityQueue.put(order)`.
  3. Si no: `printerManager.markOutOfService(order)`. La orden termina acá.

### 3.12 `QualityControlWorker` — Parte 5

```java
QualityControlWorker(StageQueue qualityQueue, SimulationLogger logger, SimulationConfig config)
```

- Demora: `config.qualityControlDelayMillis()`.
- `process(order)`:
  1. `order.incrementQualityControlCount()`.
  2. `to = OutcomeDecider.isQualityApproved(order.getId(), config) ? APPROVED : DEFECTIVE`.
  3. `order.transition(PRINTED, to)`.
  4. `logger.logTransition(order, Stage.QUALITY_CONTROL, PRINTED, to)`. La orden termina acá.

### 3.13 `ConcurrentSimulation` — Parte 1

Coordinadora. Es `public final`, implementa `Simulation` y conserva el constructor público sin
parámetros. Cada llamada a `execute` crea todos sus objetos desde cero: no se reutiliza estado entre
ejecuciones.

**Atributos** (se inicializan al comienzo de `execute`)

| Atributo | Tipo |
|---|---|
| `config` | `SimulationConfig` |
| `startNanos` | `long` |
| `orders` | `List<Order>` (en orden de id) |
| `logger` | `SimulationLogger` |
| `printerManager` | `PrinterManager` |
| `assignmentQueue`, `validationQueue`, `printingQueue`, `qualityQueue` | `StageQueue` |
| `assignmentThreads`, `validationThreads`, `printingThreads`, `qualityThreads` | `List<Thread>` |

**Métodos**

| Firma | Pasos |
|---|---|
| `public SimulationResult execute(SimulationConfig config) throws InterruptedException` | 1. Guardar `config` y `startNanos = System.nanoTime()`. 2. Crear el logger, el `PrinterManager` con `new PrinterManager(createPrinters(), logger)`, las 4 colas y las órdenes. 3. `createWorkers()` y `startWorkers()`. 4. Para cada orden, en orden de id: `logger.logCreated(order)` y **después** `assignmentQueue.put(order)` (si se invirtiera, un asignador podría registrar su transición antes que la creación). Al terminar, `assignmentQueue.close()`. 5. `waitForWorkers()`. 6. `buildResult()`. Con ese resultado, `remaining = result.totalOrders() - result.processedOrders()` y se escriben los archivos: `writeEvents(dir)`, `writeElements(dir, result.orders())` y `writeSummary(dir, result, remaining)` (el resumen necesita el resultado). Si escribir falla, envolver la `IOException` en `UncheckedIOException`. 7. Devolver el resultado. |
| `private void createOrders()` | Órdenes con id de 1 a `config.totalOrders()`. |
| `private List<Printer> createPrinters()` | Devuelve una `Printer(fila, columna)` por cada posición de `printerRows × printerColumns`, recorriendo fila por fila. La lista no se guarda en `ConcurrentSimulation`: se entrega directamente al `PrinterManager`, que es el único dueño de las impresoras. |
| `private void createWorkers()` | Para cada etapa, `config.xxxThreads()` hilos `new Thread(worker, "etapa-N")` (R8), guardados en su lista. |
| `private void startWorkers()` | `start()` de **todos** los hilos de las 4 etapas, antes de cargar las órdenes. |
| `private void waitForWorkers() throws InterruptedException` | Cierre en cascada: `join` de todos los de asignación → `validationQueue.close()` → `join` de validación → `printingQueue.close()` → `join` de impresión → `qualityQueue.close()` → `join` de control de calidad. |
| `private SimulationResult buildResult()` | Ver abajo. |

**`buildResult()`**

Primero verifica que las 4 colas estén vacías (`size() == 0`). Si alguna no lo está, lanza
`IllegalStateException` con el `name` de la cola: sería un error de implementación. Después arma el
`SimulationResult`:

- `totalOrders`: `config.totalOrders()`.
- `processedOrders`: cantidad de órdenes en estado terminal (`state.isTerminal()`).
- `totalsByState`: un `EnumMap<OrderState, Integer>` con **los 8 estados**: se cargan todos en 0 y después
  se cuenta cada orden. `SimulationResult` copia el mapa con `Map.copyOf`, así que una clave que falte
  daría `null` en `get`; por ejemplo, con todo al 100 % no hay ninguna orden `REJECTED`.
- `orders`: `toSnapshot()` de cada orden, en orden de id.
- `printers`: `printerManager.snapshots()`.
- `durationMillis = (System.nanoTime() - startNanos) / 1_000_000`.
- `allThreadsTerminated`: `true` si ningún hilo de las 4 listas `isAlive()`.

`remainingIntermediateOrders` no forma parte de `SimulationResult`: lo calcula `execute` (paso 6) y se lo
pasa a `writeSummary`.

**Por qué los objetos de dominio no necesitan lock al final:** después de los `join`, el hilo principal
ve todo lo que escribieron los workers (el `join` establece *happens-before*).

---

## 4. Invariantes y quién los garantiza

| # | Invariante del enunciado | Lo garantiza | Lo prueba |
|---|---|---|---|
| 1 | Cada orden en un solo estado a la vez | `Order` (un solo campo `state`) + R1 | `OrderTest` |
| 2 | Cada orden alcanza exactamente un estado terminal | Recorrido de los workers + `transition` con estado esperado | `InvariantsTest` |
| 3 | Ninguna orden se procesa más veces de lo debido | Contadores que lanzan excepción si pasan de 1 + cada orden está en una sola cola | `InvariantsTest` |
| 4 | Una impresora no se asigna a dos órdenes a la vez | `PrinterManager.reservePrinter` sincronizado + `Printer.reserve` exige `AVAILABLE` | `PrinterManagerTest` |
| 5 | `WAITING_VALIDATION` y `READY_TO_PRINT` tienen impresora | La transición O1 ocurre dentro de `reservePrinter` | `InvariantsTest` (sobre `eventos.csv`) |
| 6 | Una orden terminal no mantiene impresora reservada | O3 y O5 liberan o retiran la impresora en la misma operación | `InvariantsTest` |
| 7 | Una impresora `OUT_OF_SERVICE` no se reutiliza | `markOutOfService` no la devuelve a `availablePrinters` | `PrinterManagerTest` |
| 8 | Suma de `usageCount` = total de asignaciones | `Printer.reserve` incrementa una vez por O1 | Test público + `InvariantsTest` |
| 9 | No quedan órdenes en estados intermedios | Cierre en cascada + `join` | Test público |
| 10 | No quedan impresoras `RESERVED` | Toda orden con impresora pasa por O3, O4 u O5 | Test público |

`InvariantsTest` (parte 5) corre la simulación completa con varias configuraciones (demoras en 0, una sola
impresora con 100 % de impresiones exitosas, muchos hilos, pocas órdenes), siempre con las reglas de
[2.5](#25-tests-propios), y además recorre `eventos.csv` reconstruyendo el estado de cada orden e
impresora para verificar 4, 5 y 6 en todo momento.

---

## 5. Análisis teórico del tiempo (punto de partida para la parte 4)

Con la configuración oficial (500 órdenes, semilla `20261001`), `OutcomeDecider` da exactamente estos
estados finales: 359 `APPROVED`, 71 `REJECTED`, 45 `PRINT_FAILED` y 25 `DEFECTIVE`. Por lo tanto:

| Etapa | Órdenes que la atraviesan | Demora | Hilos | Tiempo de la etapa |
|---|---|---|---|---|
| Asignación | 500 | 50 ms | 3 | 500 × 50 / 3 ≈ 8,3 s |
| Validación | 500 | 120 ms | 2 | 500 × 120 / 2 = **30 s** |
| Impresión | 429 (500 − 71 rechazadas) | 180 ms | 3 | 429 × 180 / 3 ≈ 25,7 s |
| Control de calidad | 384 (429 − 45 fallidas) | 80 ms | 2 | 384 × 80 / 2 ≈ 15,4 s |

Como las etapas trabajan en paralelo, el tiempo total se aproxima al de la etapa más lenta
(**validación, 30 s**) más lo que tarda en llenarse y vaciarse la línea: unos 50 ms hasta que llega la
primera orden y unos 260 ms de impresión y control de calidad de la última. En total, **≈ 30,3 s**, cerca
del límite inferior del rango de 30 a 45 segundos que da el enunciado. No puede bajar de 30 s: aunque
todo lo demás fuera instantáneo, validación tiene que procesar 500 órdenes con 2 hilos de 120 ms.

**Saturación de impresoras.** Asignación procesa unas 60 órdenes/s (3 hilos, 50 ms) y validación unas
17/s (2 hilos, 120 ms). Cada orden asignada retiene su impresora hasta que se rechaza o se imprime, así
que las reservas crecen mucho más rápido de lo que se liberan. A los 4 o 5 segundos no queda ninguna
impresora disponible y los asignadores pasan a esperar en `reservePrinter` hasta que se libera alguna.
Esto no cambia el tiempo total, porque validación sigue siendo el cuello de botella y siempre tiene
órdenes en su cola (llega a acumular casi 200), mientras que las colas de impresión y de control de
calidad casi siempre están vacías. Conviene mostrarlo en el informe. Además, quedan **45 impresoras**
fuera de servicio, una por cada orden `PRINT_FAILED`: muchas menos que 200, coherente con la garantía del
enunciado de que no se agotan.

Como los estados finales son deterministas, el `resumen.properties` de la corrida oficial tiene que dar
exactamente `approvedOrders=359`, `rejectedOrders=71`, `printFailedOrders=45` y `defectiveOrders=25`; la
parte 5 lo usa para verificar la ejecución oficial. La parte 4 desarrolla este análisis en el informe y
la parte 5 lo compara con lo medido.

**Experimentos.** Las partes 5 y 6 necesitan correr varias veces la configuración oficial y también otras
(más o menos hilos, otras demoras). `config/tp1.properties` no se modifica: es la configuración oficial
que pide el enunciado y `ConfigurationLoaderTest` verifica sus valores. Los experimentos se corren desde
una clase con `main` en `src/test/java`, en el paquete `solution`, con un nombre que no empiece ni termine
en `Test` (por ejemplo `Experimentos`), para que `mvn clean test` no la ejecute. Esa clase arma cada
`SimulationConfig`, llama a `new ConcurrentSimulation().execute(config)` y escribe la salida en un
directorio aparte, por ejemplo `target/experimentos/`, que no se sube al repositorio. La arma la parte 5
y la usan las partes 5 y 6. Los resultados oficiales de `resultados/` salen de `java -jar target/tp1.jar`
con la configuración sin tocar.

---

## 6. Plan de trabajo e hitos

| Hito | Qué se entrega | Quién | Desbloquea |
|---|---|---|---|
| 0 | Este documento revisado y aceptado por todos | Todos | Todo |
| 1 | `Stage`, `Order`, `Printer`, `Worker` completos y versiones mínimas que compilen de `StageQueue`, `PrinterManager` y `SimulationLogger` (firmas de este documento) | 1, 2, 3, 6 | Que los workers compilen |
| 2 | Cada clase implementada con sus tests propios pasando | Cada parte | Integración |
| 3 | `ConcurrentSimulation` integrada; `mvn clean test` pasa completo | 1 (con ayuda de todos) | Mediciones |
| 4 | Revisión cruzada (sección 7), correcciones y sesión de comprensión (sección 8) | Todos | Entrega |
| 5 | Mediciones y ejecución oficial (5), diagramas PNG (6), README (4), secciones del informe (todos) y presentación (2 y 3) | Según la tabla de la sección 1 | Entrega |
| 6 | Entrega: informe armado, `docs/README.md` reemplazado, verificación desde un clon limpio (`mvn clean test`, `mvn clean package`, `java -jar target/tp1.jar`), tag, ZIP con `git archive`, subida al aula virtual y acceso de la cátedra | 1 | — |

Mientras una clase de la que dependés no esté terminada, programá contra su firma; para probar tu parte
podés usar la versión mínima del hito 1.

---

## 7. Revisión cruzada

Cada parte la revisa alguien que no la escribió, con foco en concurrencia. Cada integrante revisa
exactamente una parte: se revisan de a pares (1 ↔ 6, 2 ↔ 3 y 4 ↔ 5).

| Parte | La revisa | Qué mirar especialmente |
|---|---|---|
| 1 · `ConcurrentSimulation` | 6 | Orden del cierre en cascada; que se inicien todos los hilos antes de cargar órdenes; que `buildResult` entregue lo que necesita `writeSummary` (los 8 estados en `totalsByState`). |
| 2 · `StageQueue` | 3 | `while` + `wait`; `notifyAll` en `put` y `close`; `take` devuelve `null` solo con la cola vacía y cerrada. |
| 3 · `PrinterManager` | 2 | Atomicidad (R3); que `markOutOfService` no devuelva la impresora; orden de locks (R4). |
| 4 · Asignación y validación | 5 | R1, R2 y R6; contadores; uso correcto de `OutcomeDecider`. |
| 5 · Impresión y calidad | 4 | Lo mismo que la parte 4. |
| 6 · `SimulationLogger` | 1 | Formato exacto de los 3 archivos; que no haga E/S dentro de la sección crítica de registro. |

**Checklist para todos antes de dar una parte por terminada**

- [ ] No hay `sleep` dentro de un `synchronized`.
- [ ] Toda espera es `while (...) wait();`.
- [ ] No hay espera activa (ciclos que consultan una condición sin bloquearse).
- [ ] Solo se usan las herramientas permitidas y no hay imports con `*` de `java.util.concurrent`.
- [ ] No hay valores de configuración escritos en el código.
- [ ] `InterruptedException` no se ignora.
- [ ] Los tests de la parte pasan varias veces seguidas (los errores de concurrencia no siempre aparecen en la primera ejecución).
- [ ] Los tests cumplen las reglas de [2.5](#25-tests-propios).

---

## 8. Preparación para la defensa

El enunciado aclara que la evaluación puede incluir una calificación individual y que la cátedra puede
preguntar sobre el código o los resultados. Por eso **todos** tienen que poder explicar la arquitectura,
los recursos protegidos, la estrategia de sincronización y el cierre en cascada, no solo quien los
programó.

**Sesión de comprensión** (después de la integración, hito 4). Cada uno explica su parte al resto, con
foco en lo que se puede preguntar:

| Quién | Qué explica |
|---|---|
| 2 | `StageQueue`: por qué `while` + `wait`, por qué `notifyAll` en `put` y `close`, cuándo `take` devuelve `null`. |
| 3 | `PrinterManager`: espera y despertar, por qué `markOutOfService` no despierta a nadie, regla R3 y orden de locks. |
| 1 | Cierre en cascada: por qué se hace `join` de una etapa antes de cerrar la cola siguiente y por qué eso no hace secuencial el proceso. |
| 4 y 5 | Ciclo común de los workers, reglas R1 y R2, y el análisis teórico frente a lo medido. |
| 6 | Formato de los archivos y por qué la secuencia de eventos respeta el orden de confirmación. |

**Presentación** (partes 2 y 3, hasta 3 minutos). Debe cubrir lo que pide el enunciado:

1. La arquitectura general (línea de producción con 4 etapas y 4 colas).
2. Los recursos protegidos (las colas, el `PrinterManager` y el logger).
3. La estrategia de sincronización (monitores con `synchronized` + `wait`/`notifyAll`, regla R3, orden de locks).
4. Un resultado relevante del análisis experimental, que aporta la parte 5.

Antes de la entrega, el grupo completo la ensaya al menos una vez.
