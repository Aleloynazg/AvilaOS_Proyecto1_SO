# Decisiones de diseño de ÁvilaOS

Reglas que el enunciado deja abiertas. Se acordaron en la revisión de contratos
(Fase 0). Para cambiar una: abrir un Issue con la etiqueta `decision`, avisar al
equipo y actualizar este archivo en el mismo PR.

## 1. Procesos

| Tema | Decisión | Por qué |
| --- | --- | --- |
| Prioridad | Entero de 1 a 10. **Número menor = más prioridad.** | Es la convención de Stallings y de Linux. |
| Deadline | Se mide en **ciclos de reloj**. Se resta 1 por ciclo desde que el proceso **llega** (incluye Nuevo, Listo y Bloqueado). Al llegar a 0 → Terminado con causa `DEADLINE_MISSED` y se libera su memoria. | El enunciado dice "al llegar a 0 tiene que ser terminado"; contarlo siempre es lo más simple y justo para comparar políticas. |
| Instrucciones | El usuario indica cuántas. 1 instrucción = 1 ciclo. PC y MAR suben 1 por ciclo. | Lo pide la nota del enunciado. |
| ID | Contador global único (`IdGenerator`). Etiqueta en el log: `P{id}-H{computador}`, buffers `B{id}`. | Formato de los ejemplos del enunciado. |
| Proceso que pide más RAM que la total del computador | Se rechaza al crearlo (validación en la GUI). | Si no, se quedaría para siempre en Nuevos. |

## 2. Tipos de proceso

| Tipo | Comportamiento |
| --- | --- |
| CPU bound | N instrucciones normales seguidas. |
| I/O bound | Cada **K** instrucciones pide E/S y se bloquea **M** ciclos (motivo `IO`). Al terminar la E/S vuelve a Listos. K y M los pone el usuario al crear el proceso. |
| Productor | Repite: producir (k ciclos de usuario) → `semWait(e)` → `semWait(s)` → `append` → `semSignal(s)` → `semSignal(n)`. Termina al producir los elementos pedidos. |
| Consumidor | Repite: `semWait(n)` → `semWait(s)` → `take` → `semSignal(s)` → `semSignal(e)` → consumir (k ciclos de usuario). Termina al consumir los elementos pedidos. |

- `semWait`, `semSignal`, `append` y `take` cuentan **1 instrucción en modo sistema operativo** cada una.
- Para productor/consumidor, el total de instrucciones se calcula solo a partir de los elementos y los ciclos por elemento (el usuario no lo escribe).

## 3. Bloqueos

| Motivo (`BlockReason`) | Cuándo | Cómo se despierta |
| --- | --- | --- |
| `IO` | Proceso I/O bound pide E/S | Pasan M ciclos |
| `SEM_EMPTY` (e) | Productor y el buffer está lleno | Un consumidor hace `semSignal(e)` |
| `SEM_FULL` (n) | Consumidor y el buffer está vacío | Un productor hace `semSignal(n)` |
| `SEM_MUTEX` (s) | Otro proceso está en la región crítica | El que estaba dentro hace `semSignal(s)` |
| `NETWORK` | Acceso a un buffer de otro computador | Pasan `latencia` ciclos |

- Acceso remoto: **después** de cada `semWait`, `semSignal`, `append` o `take` sobre un buffer remoto, el proceso se bloquea `latencia` ciclos con motivo `NETWORK` (espera la respuesta de la red). No se bloquea si la latencia es 0, si ya quedó esperando en el semáforo o si era su última instrucción. Cada acceso remoto se anota en el log.
- Las colas de los semáforos son **FIFO**.
- Un proceso despertado por un `semSignal` remoto pasa a Listos en el Kernel de su propio computador, en ese mismo ciclo (el reloj avisa a los computadores uno tras otro en el mismo hilo, así que no hace falta una cola intermedia).
## 4. Planificación

| Política | Regla | Desempate |
| --- | --- | --- |
| FCFS | Sin expulsión; orden de llegada a Listos. | — |
| Round Robin | Quantum configurable (por defecto **4** ciclos). Al acabarse, el proceso vuelve al final de Listos. | — |
| Prioridades apropiativas | Siempre corre el de menor número. Si llega uno con prioridad **estrictamente** mejor, expulsa al actual. | Orden de llegada |
| EDF | Siempre corre el de menor deadline restante; expulsa si llega uno con deadline estrictamente menor. | Orden de llegada |

- **Cambio de contexto:** cuesta **1 ciclo en modo sistema operativo** (la CPU no avanza al proceso ese ciclo). Se ve en la GUI como modo KERNEL.
- **Cambio de política en caliente:** la cola de listos se reordena según la nueva política en el siguiente ciclo; el proceso en CPU sigue hasta que la nueva política decida expulsarlo.
- **Asignación de computador:** manual por defecto. Opción "Automático": el computador con menos procesos en Listos; si empatan, el de más RAM libre.

## 5. Valores por defecto de la configuración

| Parámetro | Valor | Rango permitido |
| --- | --- | --- |
| Duración de un ciclo | 500 ms | 50 – 5000 ms |
| Número de computadores | 2 | 2 – 4 |
| RAM por computador | 1024 MB | 128 – 8192 MB |
| Política inicial | FCFS | las 4 |
| Quantum (Round Robin) | 4 ciclos | 1 – 20 |
| Latencia de red | 3 ciclos | 0 – 20 |
| Capacidad de un buffer | 5 elementos | 1 – 50 |
| Memoria que ocupa un buffer | 1 MB por elemento de capacidad | — |

## 6. Métricas

| Métrica | Fórmula |
| --- | --- |
| Throughput | Procesos terminados con `COMPLETED` ÷ ciclos transcurridos |
| Utilización de CPU | Ciclos con un proceso en CPU (usuario + SO) ÷ ciclos transcurridos × 100 |
| Tiempo de respuesta | Ciclo de la primera vez en CPU − ciclo de llegada; se promedia |
| Cumplimiento de deadlines | `COMPLETED` ÷ (`COMPLETED` + `DEADLINE_MISSED`) × 100 |
| Equidad | Índice de Jain sobre el tiempo de espera en Listos: (Σx)² ÷ (n · Σx²). 1 = totalmente justo. |
| Productor-consumidor | Elementos producidos y consumidos por buffer; promedio de ciclos bloqueados en semáforos por proceso |

Las métricas globales se calculan sumando los contadores de todos los computadores, no promediando los porcentajes.
