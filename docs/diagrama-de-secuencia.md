# Diagrama de secuencia

El diagrama muestra la simulación completa con el modelo de clases acordado. Se ve que las cuatro etapas
trabajan **al mismo tiempo** sobre órdenes distintas (bloques `par`), y que el hilo principal solo
espera para **cerrar** cada cola en cascada, sin frenar a los workers.

Cantidad de hilos por etapa (configuración oficial): asignación 3, validación 2, impresión 3 y
control de calidad 2. Cada participante `…Worker` representa a todos los hilos de su etapa.

```mermaid
%%{init: {'theme': 'dark'}}%%
sequenceDiagram
    autonumber
    participant M as ConcurrentSimulation<br/>(hilo main)
    participant AQ as assignmentQueue
    participant AW as AssignmentWorker ×3
    participant VQ as validationQueue
    participant VW as ValidationWorker ×2
    participant PM as PrinterManager
    participant PQ as printingQueue
    participant PW as PrintingWorker ×3
    participant QQ as qualityQueue
    participant QW as QualityControlWorker ×2
    participant L as SimulationLogger

    rect rgba(144, 164, 174, 0.2)
        Note over M,L: Fase 1 · Inicio
        Note over M: crea órdenes, impresoras,<br/>4 colas y los 10 workers
        M->>AW: start()
        M->>VW: start()
        M->>PW: start()
        M->>QW: start()
        Note over VW,QW: sus colas están vacías:<br/>take() hace wait() sin consumir CPU
        loop cada orden, en orden de id
            M->>L: logCreated(o)
            M->>AQ: put(o)
        end
        M->>AQ: close()
    end

    rect rgba(30, 136, 229, 0.2)
        Note over M,L: Fase 2 · Línea de producción en régimen (etapas en paralelo)
        AW->>AQ: take() → o1
        Note over AW: sleep, assignmentCount++
        AW->>PM: reservePrinter(o1)
        Note over PM: synchronized: reserva P, o1 pasa a<br/>WAITING_VALIDATION y se registra
        PM->>L: logTransition(o1 → WAITING_VALIDATION)
        PM-->>AW: impresora P
        AW->>VQ: put(o1) → notifyAll()

        par Asignación
            AW->>AQ: take() → o2
            AW->>PM: reservePrinter(o2)
            AW->>VQ: put(o2)
        and Validación
            VW->>VQ: take() → o1
            Note over VW: sleep, validationCount++,<br/>isModelValid
            alt modelo válido
                VW->>L: logTransition(o1 → READY_TO_PRINT)
                VW->>PQ: put(o1)
            else modelo inválido
                VW->>PM: releasePrinter(o1, VALIDATION, REJECTED)
                PM->>L: logTransition(o1 → REJECTED)
            end
        end

        par Asignación
            AW->>AQ: take() → o3
        and Validación
            VW->>VQ: take() → o2
        and Impresión
            PW->>PQ: take() → o1
            Note over PW: sleep, printingCount++,<br/>isPrintSuccessful
            alt impresión exitosa
                PW->>PM: releasePrinter(o1, PRINTING, PRINTED)
                PM->>L: logTransition(o1 → PRINTED)
                Note over PM: notifyAll(): despierta a<br/>AssignmentWorker en espera
                PW->>QQ: put(o1)
            else impresión fallida
                PW->>PM: markOutOfService(o1)
                PM->>L: logTransition(o1 → PRINT_FAILED)
            end
        end

        par Asignación
            AW->>AQ: take() → o4
        and Validación
            VW->>VQ: take() → o3
        and Impresión
            PW->>PQ: take() → o2
        and Control de calidad
            QW->>QQ: take() → o1
            Note over QW: sleep, qualityControlCount++,<br/>isQualityApproved
            QW->>L: logTransition(o1 → APPROVED / DEFECTIVE)
        end

        Note over AW,PM: sin impresoras AVAILABLE:<br/>reservePrinter hace wait() hasta un releasePrinter
    end

    rect rgba(67, 160, 71, 0.2)
        Note over M,L: Fase 3 · Cierre en cascada (las etapas siguientes siguen trabajando)
        AW->>AQ: take() → null (cerrada y vacía)
        Note over AW: los 3 hilos terminan
        M->>AW: join()
        M->>VQ: close() → notifyAll()
        VW->>VQ: take() → null al vaciarse
        M->>VW: join()
        M->>PQ: close() → notifyAll()
        PW->>PQ: take() → null al vaciarse
        M->>PW: join()
        M->>QQ: close() → notifyAll()
        QW->>QQ: take() → null al vaciarse
        M->>QW: join()
        Note over M: no queda ningún hilo vivo
        Note over M: buildResult() → SimulationResult
        M->>L: writeEvents / writeElements / writeSummary(result)
    end
```

## Cómo leerlo

| Fase | Qué muestra |
|---|---|
| 1 · Inicio | Todos los workers se inician **antes** de que haya trabajo. Los que no tienen órdenes quedan en `wait()` dentro de `take()`, sin espera activa. El bucle registra cada orden como creada (`logCreated`) y recién después la pone en `assignmentQueue`, una por una: así ningún asignador puede registrar su transición antes que la creación. Después la cola se cierra enseguida porque no van a llegar más. |
| 2 · Régimen | Cada bloque `par` es un instante en el que varias etapas trabajan a la vez sobre órdenes distintas: mientras validación procesa `o1`, asignación ya procesa `o2`, y así. El tiempo total se aproxima al de la etapa más lenta, no a la suma de las cuatro. |
| 3 · Cierre | El hilo principal hace `join()` de una etapa y recién ahí cierra la cola de la siguiente, porque ya nadie puede ponerle órdenes. Los workers de esa etapa terminan de vaciarla y reciben `null`. Ninguna etapa espera a la anterior para empezar; solo el **cierre** es escalonado. Al final se arma el resultado y recién después se escriben los tres archivos, porque `resumen.properties` usa ese resultado. |

## Recursos compartidos y sincronización

| Recurso | Mecanismo | Quién espera / quién despierta |
|---|---|---|
| Cada `StageQueue` | `synchronized` + `wait`/`notifyAll` | `take()` espera mientras esté vacía y abierta; `put()` y `close()` despiertan. |
| `PrinterManager` | `synchronized` + `wait`/`notifyAll`. En la misma sección crítica cambia la impresora, cambia el estado de la orden y registra el evento | `reservePrinter` espera si no hay `AVAILABLE`; `releasePrinter` despierta; `markOutOfService` no. |
| `SimulationLogger` | `synchronized` | Solo exclusión mutua sobre `sequence` y la lista de eventos (`logCreated`, `logTransition`). |
| `Order` | Sin lock | La tiene un solo worker a la vez; el `put`/`take` sincronizado garantiza la visibilidad. |

Orden de locks: `PrinterManager` → `SimulationLogger`. El logger nunca llama al `PrinterManager`, por lo
que no puede haber deadlock entre ambos.
