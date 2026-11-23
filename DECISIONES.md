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


## 7. Organización del disco

| Tema | Decisión | Por qué |
| --- | --- | --- |
| Capacidad | 300 bloques físicos. | Permite separar el almacenamiento de archivos del área de intercambio. |
| Sistema de archivos | Bloques 0–199. | Se reservan 200 bloques para archivos y directorios. |
| Área de intercambio (swap) | Bloques 200–299. | Se reservan 100 bloques para suspender procesos. |
| Destino de solicitudes | Primer bloque del archivo o elemento involucrado. Para SUSPEND y RESUME, primer bloque del espacio de intercambio del proceso. | Permite determinar hacia dónde debe desplazarse el cabezal. |

## 8. Planificación del disco

| Política | Regla |
| --- | --- |
| FIFO | Atiende las solicitudes en orden de llegada. |
| SSTF | Atiende la solicitud cuyo bloque de destino esté más cerca del cabezal actual. |
| SCAN | El cabezal avanza en una dirección atendiendo solicitudes y llega hasta el extremo del disco antes de cambiar de dirección. |
| C-SCAN | El cabezal atiende solicitudes en una dirección. Al llegar al extremo, regresa al bloque 0 sin contar ese desplazamiento como movimiento atendido. |
| LOOK | Política adicional opcional: cambia de dirección al llegar a la última solicitud pendiente, sin recorrer hasta el extremo físico. |

**- Dirección inicial:** el cabezal comienza desplazándose hacia los bloques de mayor numeración.

**- Costo de atención:** se calcula sumando los ciclos necesarios para el movimiento del cabezal y los ciclos de transferencia. Se añade un ciclo por cada bloque transferido.

**- Velocidad del cabezal:** por defecto, avanza una posición por ciclo. Este valor será configurable mediante `headSpeed`.

**- Posición final:** al completar una solicitud, el cabezal queda ubicado en el bloque de destino.

## 9. Solicitudes de entrada y salida

| Operación | Bloques involucrados |
| --- | --- |
| CREATE | Cantidad de bloques correspondientes al tamaño del archivo. |
| READ | Cantidad de bloques correspondientes al tamaño del archivo. |
| UPDATE | 1 bloque. |
| DELETE | 1 bloque. |
| SUSPEND | Cantidad de bloques necesarios para almacenar la memoria del proceso. |
| RESUME | Cantidad de bloques correspondientes a la memoria del proceso almacenada en swap. |

**- Las solicitudes almacenan el proceso que las genera, la operación, el bloque de destino, la cantidad de bloques y el ciclo de llegada. También pueden incluir la ruta, el nuevo nombre, el propietario y los permisos del elemento.**

**- Las operaciones CREATE reservan los bloques al generar la solicitud. Las operaciones UPDATE y DELETE se aplican cuando el disco termina de atenderlas. Si una solicitud falla o se cancela, los recursos reservados deben liberarse.**

**- Las operaciones de directorios utilizarán como destino el primer bloque del directorio correspondiente.**

## 10. Validación y permisos del sistema de archivos

| Elemento | Decisión |
| --- | --- |
| Usuarios | Se contemplan los modos ADMIN y USER. |
| Permisos | Los elementos podrán ser PUBLIC o PRIVATE. |
| Lectura | Un usuario puede leer sus propios archivos y los archivos públicos. |
| Creación | Un usuario puede crear elementos únicamente dentro de sus propios directorios. |
| Eliminación | Un usuario puede eliminar únicamente sus propios archivos. |
| Renombrado | Solo ADMIN puede renombrar elementos. |

**- Las solicitudes se validan al crearse y nuevamente cuando el disco termina de atenderlas. Si las condiciones cambiaron mientras la solicitud esperaba, la operación se marca como fallida y se registra en el log.**

**- Una ruta inexistente, un permiso insuficiente o la falta de bloques disponibles deben provocar el rechazo de la operación sin detener la simulación.**

## 11. Integración con los procesos

**- Las operaciones de disco se representan mediante instrucciones `DISK_REQUEST` ejecutadas en modo sistema operativo.**

**- Al generar una solicitud, el proceso pasa al estado `BLOCKED` con motivo `DISK_IO`. Cuando termina la operación, vuelve a `READY` y continúa con sus instrucciones pendientes en modo usuario.**

**- Si no hay memoria RAM suficiente para admitir un proceso, se selecciona primero un proceso bloqueado como candidato a suspensión. Entre los candidatos se selecciona el de menor prioridad; si hay empate, se elige el que lleve más tiempo bloqueado. Si no hay procesos bloqueados, el nuevo proceso permanece en la cola de Nuevos.**

**- Cuando se libera memoria, se prioriza la reactivación de procesos suspendidos que estén listos, antes de admitir procesos nuevos. La memoria necesaria para reactivar un proceso se reserva al generar la solicitud RESUME. Si la operación falla, se libera la reserva.**

## 12. Decisiones de implementación del Proyecto 2

Decisiones que se tomaron al implementar las fases 1 a 4 (PR #83 a #96) y que completan las secciones anteriores. Las marcadas con **(confirmar)** esperan el visto bueno del equipo.

### 12.1 Archivos y E/S de archivos

| Tema | Decisión |
| --- | --- |
| Costo de una solicitud de archivos | READ y CREATE de un archivo cuestan tantos bloques como tiene el archivo. UPDATE y DELETE cuestan 1 bloque (solo cambia la entrada del directorio). |
| Operaciones sobre un directorio | Los directorios no ocupan bloques propios. La solicitud va al primer bloque del primer archivo que contienen, o al bloque 0 (tabla del sistema de archivos) si están vacíos o se están creando. Costo: 1 bloque. |
| UPDATE sin nombre nuevo | El kernel agrega `_v2` antes de la extensión: `notas.txt` pasa a `notas_v2.txt` y `datos` pasa a `datos_v2`. |
| Permisos | Se validan al crear el proceso (la interfaz lo impide y explica el motivo con `Computer.checkFileOperation`). El kernel valida que la operación sea posible (la ruta existe, hay bloques, el nombre es válido) al generar la solicitud y otra vez cuando el disco la termina. |
| Operación imposible | El proceso termina con causa `IO_FAILED` y el log explica el motivo. La simulación sigue. |
| Proceso muerto con solicitud en cola | La solicitud se cancela y se liberan los bloques que había reservado un CREATE. Si el disco ya la estaba atendiendo, la descarta al terminar. |
| SCAN y C-SCAN | Barren hasta el último bloque físico del disco (el 299 con los valores por defecto, que incluye el swap), no solo hasta el último bloque de archivos. |
| Campos opcionales del JSON de procesos | `priority` (por defecto 5), `type` (por defecto `FILE_IO`), `blocks`, `newName`, `owner` y, en `globalConfig`, `headSpeed` e `initialDirection`, como indica `plan.txt`. Un archivo sin esos campos es válido y se parece al del enunciado. |
| Velocidad del cabezal contra el deadline del enunciado **(confirmar)** | Los 8 procesos del enunciado traen deadline 500 y el JSON no trae `headSpeed`. Con el valor por defecto (1 posición por ciclo) solo el desplazamiento de FIFO ya son 644 ciclos y los últimos procesos mueren por deadline. Con `headSpeed` 5 o más terminan los 8. Hay que decidir qué valor usar en la defensa (`IntegracionP2Prueba`, prueba 3, deja la tabla). |

### 12.2 Siete estados, swap y planificador a mediano plazo

| Tema | Decisión |
| --- | --- |
| Memoria de cada estado | Nuevo, Terminado y los dos suspendidos no ocupan RAM. Un proceso que se está suspendiendo (su SUSPEND espera al disco) todavía la ocupa; uno que se está reactivando ya la tiene reservada. |
| Espacio en el swap | 1 bloque por cada MB de memoria del proceso, en bloques consecutivos. Se reserva al generar la solicitud SUSPEND y se libera al reactivar al proceso o al terminar. El costo en disco de SUSPEND y RESUME es ese mismo número de bloques. |
| A quién se suspende | Solo a Bloqueados (nunca Listos ni en Ejecución), con el criterio de la sección 11. Si el swap no tiene espacio consecutivo para esa víctima se prueba con la siguiente. Si ni suspendiendo a todos los Bloqueados cabría el Nuevo, no se suspende a nadie. |
| Mientras se suspende | El proceso sigue en la RAM hasta que el disco termina de escribir. Si su espera termina antes pasa a Listo suspendido (sigue en tránsito). Si muere, su RAM, su swap y la RAM pendiente se liberan una sola vez. |
| Estando suspendido | El tiempo sigue corriendo: la E/S con tiempo y el deadline se siguen descontando. Un Bloqueado suspendido que termina su espera pasa a Listo suspendido, no a Listo. Si se vence el deadline, el proceso termina. |
| Reactivación | Solo se reactivan Listos suspendidos, de mayor a menor prioridad, y se detiene en el primero que no cabe (como la admisión de Nuevos). Un Bloqueado suspendido primero debe despertar. |
| Excepción a "reactivar antes de admitir Nuevos" **(confirmar)** | Mientras haya suspensiones en curso no se reactiva a nadie, y al terminar la última se admite primero al Nuevo que las provocó. Si no, el primer suspendido que despertara volvería enseguida, se llevaría la RAM y el Nuevo no entraría nunca (visto en `SuspensionPrueba`: más de 500 ciclos de espera). |
| Contadores | El kernel cuenta las suspensiones y las reactivaciones completadas (`getSuspensionCount`, `getReactivationCount`). |
