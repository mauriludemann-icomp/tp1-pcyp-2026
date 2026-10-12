# TP1 Programacion Concurrente y Paralela

Repositorio plantilla del Trabajo Practico 1 sobre una granja concurrente de impresion 3D.

## Requisitos

- Java 21.
- Maven 3.9 o compatible.
- No se admiten dependencias de produccion externas a la biblioteca estandar de Java.
- De `java.util.concurrent` solo se permiten `Semaphore`, `Lock`, `ReentrantLock`,
  `Executor`, `ExecutorService`, `Executors` y `ThreadPoolExecutor`.
- El uso de ejecutores es opcional; `Thread` es el camino recomendado. Si se usan,
  deben limitarse a ejecutar los workers y esperar su finalizacion.

## Comandos

Ejecutar la suite publica:

```bash
mvn clean test
```

Construir el proyecto:

```bash
mvn clean package
```

Ejecutar la configuracion ubicada en `config/tp1.properties`:

```bash
java -jar target/tp1.jar
```

La plantilla contiene una implementacion incompleta de `ConcurrentSimulation`. La suite funcional publica fallara hasta que el grupo implemente la simulacion.

## Documentacion

El enunciado completo se encuentra en [ENUNCIADO.md](ENUNCIADO.md). Los diagramas y el informe final deben ubicarse en `docs/` con los nombres indicados en ese documento.

## Entrega

La entrega se identifica mediante el tag Git `{nombre-del-grupo}-entrega-tp1`. El ZIP que se sube al aula virtual debe generarse desde ese mismo tag.
