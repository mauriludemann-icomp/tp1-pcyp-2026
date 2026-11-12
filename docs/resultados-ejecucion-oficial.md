# Resultados de una ejecución oficial

## Configuración

Se utilizó la configuración oficial contenida en `config/tp1.properties`, con los siguientes parámetros:

| Parámetro | Valor |
|---|---:|
| Órdenes | 500 |
| Matriz de impresoras | 10 × 20 |
| Total de impresoras | 200 |
| Hilos de asignación | 3 |
| Hilos de validación | 2 |
| Hilos de impresión | 3 |
| Hilos de control de calidad | 2 |
| Probabilidad de modelo válido | 85 % |
| Probabilidad de impresión exitosa | 90 % |
| Probabilidad de calidad aprobada | 95 % |
| Demora de asignación | 50 ms |
| Demora de validación | 120 ms |
| Demora de impresión | 180 ms |
| Demora de control de calidad | 80 ms |
| Semilla | 20261001 |

## Entorno y reproducción

El entorno local verificado dispone de Windows 11, Java Temurin 21.0.11 y Apache Maven 3.9.16.

Desde la carpeta raíz del proyecto, que contiene `pom.xml`, se compila y ejecuta mediante:

```powershell
mvn clean package
java -jar target/tp1.jar
```

El programa carga automáticamente `config/tp1.properties` y escribe los archivos de salida en `resultados`.

## Resultados obtenidos

Los archivos revisados el 13/10/2026 contienen los siguientes resultados:

| Resultado | Cantidad |
|---|---:|
| Órdenes procesadas | 500 |
| Aprobadas (`APPROVED`) | 359 |
| Rechazadas (`REJECTED`) | 71 |
| Impresiones fallidas (`PRINT_FAILED`) | 45 |
| Defectuosas (`DEFECTIVE`) | 25 |
| Órdenes en estados intermedios | 0 |

El tiempo informado por la simulación fue de **31.937 ms, equivalentes a 31,937 segundos**. Esta medición se encuentra dentro del intervalo de 30 a 45 segundos indicado por la consigna, en el equipo utilizado.

La duración se mide desde el inicio de la simulación hasta después del cierre del registro de eventos, antes de escribir los archivos finales de resultados.

## Archivos incluidos como evidencia

- **`resultados/eventos.csv`:** contiene 2.313 eventos, sin contar la cabecera: 500 creaciones, 500 asignaciones, 500 validaciones, 429 impresiones y 384 controles de calidad.
- **`resultados/elementos.csv`:** contiene una fila por cada una de las 500 órdenes, con su estado final, impresora histórica y contadores.
- **`resultados/resumen.properties`:** contiene los totales, la duración y los indicadores de finalización.

## Comprobaciones realizadas

Se verificó que:

- Los IDs de las órdenes finales fueran consecutivos de 1 a 500, sin duplicados.
- Todas las órdenes tuvieran un estado terminal.
- Los contadores correspondieran al recorrido de cada orden.
- El último evento de cada orden coincidiera con su estado final y su impresora histórica en `elementos.csv`.
- Las secuencias de eventos fueran estrictamente crecientes y los tiempos no decrecientes.
- Los totales por estado coincidieran entre `elementos.csv` y `resumen.properties`.

El resumen informa `allThreadsTerminated=true` y `remainingIntermediateOrders=0`. La implementación espera la terminación de los workers mediante `join()` antes de devolver el resultado.

Estas comprobaciones respaldan la consistencia de esta ejecución. El análisis de sincronización y las pruebas adicionales complementan esta evidencia para evaluar la corrección concurrente.