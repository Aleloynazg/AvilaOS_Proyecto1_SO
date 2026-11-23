# ÁvilaOS – Simulador de un sistema operativo con disco, sistema de archivos y swap

> Borrador del README del Proyecto 2 (`README2.md`). Cuando Ale lo revise, la idea
> es que reemplace a `README.md` (que todavía describe el Proyecto 1) y se borre esta nota.

Proyecto 2 de Sistemas Operativos (2627-1). Simula **un computador** con su CPU,
su RAM y un disco de 300 bloques (200 para archivos y 100 de swap), todo
sincronizado por un reloj global. Los procesos hacen operaciones de archivos
(`CREATE`, `READ`, `UPDATE`, `DELETE`): el kernel las convierte en solicitudes
al disco, que las atiende con una de cinco políticas. Si falta RAM para admitir
un proceso, el planificador a mediano plazo suspende a uno Bloqueado y lo manda
al swap (siete estados).

Del Proyecto 1 se conservan el planificador de CPU (FCFS, Round Robin,
Priority y EDF), la memoria, el PCB y el modelo de instrucciones. El clúster de
varios computadores, la red y los buffers productor-consumidor ya no existen.

## Estado del proyecto

| Parte | Estado |
| --- | --- |
| F0 a F4: disco, sistema de archivos, procesos de E/S, siete estados, swap y planificador a mediano plazo | ✅ Listo y merged en `develop` |
| F5: interfaz (cargar los dos JSON, mapa del disco, tablas, métricas, guardar el sistema de archivos) | ❌ Pendiente |
| F6: experimentos, informe, `README.md` final, tag `v2.0` | ❌ Pendiente |

La lógica del Proyecto 2 ya funciona y se prueba por código. Lo que falta es la
parte visual: la ventana actual sigue siendo la del Proyecto 1 (ver más abajo).

## Requisitos

- **JDK 21 o superior.** El enunciado pide una versión posterior a la 21: hoy
  `nbproject/project.properties` tiene `javac.source` y `javac.target` en 21 y
  se probó con JDK 27. Hay que subir esos valores antes de la entrega.
- **NetBeans** (proyecto Ant).
- Librerías incluidas en `lib/` (no hay que descargar nada):
  - `gson-2.14.0.jar`: leer y guardar los JSON.
  - `jfreechart-1.5.6.jar`: gráficas.

## Cómo abrirlo y ejecutarlo

1. Clonar el repositorio y, en NetBeans, `File > Open Project` y elegir la
   carpeta del proyecto.
2. Si NetBeans pregunta por la plataforma Java, elegir un JDK 21 o superior
   (`Tools > Java Platforms`).
3. `Run > Run Project` (F6). La clase principal es `avilaos.AvilaOS`.

Ojo: por ahora `AvilaOS` abre la vista de configuración del Proyecto 1 (un
computador con procesos de CPU y de E/S de la cola, por ejemplo con
`config/ejemplo.json`). La carga obligatoria de los dos JSON desde la ventana,
el mapa del disco y los demás paneles son la fase 5.

Mientras tanto, para ver el sistema completo del Proyecto 2 corriendo (los 8
procesos del enunciado, las cinco políticas de disco y la suspensión), hay que
usar las clases de prueba (ver [Pruebas](#pruebas)), en particular
`IntegracionP2Prueba` y `SuspensionPrueba`.

## Los dos archivos JSON

La simulación se arma con **dos** archivos, los dos obligatorios. En `config/p2/`
están los del enunciado (`sistema_archivos_enunciado.json` y
`procesos_enunciado.json`) y uno para ver la suspensión (`suspension.json`).

### 1. Sistema de archivos

```json
{
  "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
  "fileSystem": [
    { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11,
      "owner": "admin", "permissions": "public" },
    { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" }
  ]
}
```

- `totalBlocks`: bloques de la zona de archivos (entre 100 y 500). El swap se
  suma aparte con `swapSizeBlocks` del otro archivo.
- `initialHead`: bloque donde empieza el cabezal.
- `type`: `FILE` o `DIRECTORY`; `permissions`: `public` o `private`.
- Un archivo fragmentado puede traer `"chain": [0, 1, 2, 5]` (sus bloques en
  orden). Los del enunciado no lo usan.
- Al cargarlo se valida: bloques fuera de rango, bloques solapados y rutas sin
  directorio padre se rechazan con un mensaje claro.

### 2. Procesos y configuración

```json
{
  "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
  "processes": [
    { "name": "Proc_95", "memory": 16, "deadline": 500, "instructions": 2,
      "targetPath": "/sistema/config.sys", "operation": "READ" }
  ]
}
```

- `initialDiskPolicy`: `FIFO`, `SSTF`, `SCAN`, `C-SCAN` o `LOOK`.
- `policy` (CPU): `FCFS`, `ROUND_ROBIN`, `PRIORITY_PREEMPTIVE` o `EDF`; `quantum`
  solo lo usa Round Robin.
- `operation`: `CREATE`, `READ`, `UPDATE` o `DELETE` sobre `targetPath`.
- Campos opcionales de `globalConfig`: `headSpeed` (posiciones del cabezal por
  ciclo, de 1 a 100; por defecto 1) e `initialDirection` (`UP` o `DOWN`).
- Campos opcionales de un proceso: `priority` (1 a 10, por defecto 5), `newName`
  (`UPDATE`), `blocks`, `owner`, `permissions` y `targetType` (`CREATE`) y
  `type`. Sin `type` el proceso es de E/S de archivos; también puede ser
  `CPU_BOUND` o `IO_BOUND` (con `ioEvery` e `ioDuration`).
- Rangos: `cycleDurationMs` de 50 a 5000, `ramCapacity` de 128 a 8192, `quantum`
  de 1 a 20, `swapSizeBlocks` de 0 a 1000.

### Cargarlos desde código

```java
FileSystemConfig fs = ConfigStore.loadFileSystem(new File("config/p2/sistema_archivos_enunciado.json"));
ProcessesConfig pr = ConfigStore.loadProcesses(new File("config/p2/procesos_enunciado.json"));
SimulationFactory.Simulation sim = SimulationFactory.createP2(pr, fs);
// sim.clock (reloj) y sim.computer (computador con disco, kernel y sistema de archivos)
```

Para guardar el estado actual del sistema de archivos:
`ConfigStore.saveFileSystem(FileSystemConfig.fromState(computer.getFileSystem(), computer.getDisk()), file)`.

## Cómo funciona

- **Disco:** 200 bloques de archivos (0 a 199) y 100 de swap (200 a 299) por
  defecto, con asignación encadenada. Atender una solicitud cuesta 1 ciclo
  para tomarla, los ciclos que tarda el cabezal en llegar al bloque (distancia
  ÷ `headSpeed`, redondeado hacia arriba) y 1 ciclo por bloque transferido.
  Políticas: FIFO, SSTF, SCAN, C-SCAN y LOOK. Con el anexo del enunciado dan
  518, 208, 332 y 192 bloques recorridos (FIFO, SSTF, SCAN y C-SCAN);
  `PoliticasDiscoPrueba` lo comprueba.
- **Sistema de archivos:** árbol propio de directorios y archivos con dueño,
  ruta, bloques y color. Modo `ADMIN` o `USER` y permisos `public` o `private`:
  un usuario lee lo propio y lo público, crea solo en sus directorios y elimina
  solo sus archivos; renombrar es solo de `ADMIN`. Una operación imposible
  (ruta que no existe, sin permiso, sin bloques) termina el proceso con
  `IO_FAILED` y queda explicada en el log, sin detener la simulación.
- **Procesos de E/S:** ejecutan una instrucción `DISK_REQUEST` en modo sistema
  operativo. El proceso queda `BLOCKED` con motivo `DISK_IO` hasta que el disco
  termina; entonces el kernel aplica la operación y lo despierta.
- **Siete estados y swap:** Nuevo, Listo, Ejecución, Bloqueado, Terminado, más
  Listo suspendido y Bloqueado suspendido. Si un Nuevo no cabe en la RAM, el
  `MediumTermScheduler` suspende a Bloqueados (el de menor prioridad y, si hay
  empate, el que lleva más tiempo bloqueado): el disco escribe su memoria en
  el swap (1 bloque por MB) con una solicitud `SUSPEND`. Cuando hay RAM, los
  Listos suspendidos se reactivan por prioridad con `RESUME`, antes que los
  Nuevos. El log cuenta cada paso (`Se suspende a ... para admitir a ...`).

Las reglas que el enunciado deja abiertas están en [`DECISIONES.md`](DECISIONES.md):
disco y swap (secciones 7 a 9), permisos (10), integración con los procesos (11)
y las decisiones de implementación del Proyecto 2 (12), dos de ellas marcadas
como (confirmar).

## Estructura del código

| Paquete | Contenido |
| --- | --- |
| `avilaos.hardware` | Reloj global, CPU, `Computer` (un computador con disco), métricas |
| `avilaos.hardware.disk` | `Disk`, `DiskBlock`, estado y dirección del cabezal |
| `avilaos.kernel` | `Kernel`: colas (incluidas las de suspendidos), admisión por memoria, E/S de archivos |
| `avilaos.kernel.process` | PCB, enums de estado/tipo/bloqueo/operación, instrucciones, `ProcessFactory` |
| `avilaos.kernel.scheduler` | Políticas de CPU y `MediumTermScheduler` (suspender y reactivar) |
| `avilaos.kernel.disk` | `DiskRequest`, `DiskScheduler` y las 5 políticas de disco |
| `avilaos.kernel.fs` | Árbol de archivos, `FileSystem`, `AccessControl` (permisos) y `FileIoManager` |
| `avilaos.kernel.swap` | `SwapArea`: reserva de bloques de swap por proceso |
| `avilaos.kernel.memory` | Control de RAM usada y libre |
| `avilaos.structures` | `LinkedQueue`: estructura propia (no se usan colecciones de `java.util`) |
| `avilaos.config` | Los dos JSON, `ConfigStore` y `SimulationFactory` |
| `avilaos.gui` | Ventanas: solo observan al sistema, no contienen lógica del SO (la del Proyecto 2 es la F5) |

## Pruebas

Las clases `*Prueba.java` son programas con `main` (21 en total). Se corren desde
NetBeans (clic derecho > *Run File*) **con la raíz del proyecto como carpeta de
trabajo**, porque algunas leen `config/p2/`. Terminan con código 1 si alguna
comprobación falla.

| Área | Clases |
| --- | --- |
| Disco | `DiskPrueba`, `DiskEncadenadoPrueba`, `DiskTamanoPrueba`, `DiskIntegracionPrueba`, `DiskVelocidadPrueba`, `PoliticasDiscoPrueba` |
| Sistema de archivos | `FileSystemPrueba`, `AccessControlPrueba`, `FileSystemJsonPrueba` |
| Procesos de E/S | `ProcesoArchivosPrueba`, `ComputerArchivosPrueba`, `ProcesosJsonPrueba`, `IntegracionP2Prueba` |
| Siete estados y suspensión | `SieteEstadosPrueba`, `SwapAreaPrueba`, `MediumTermSchedulerPrueba`, `SuspensionPrueba` |
| Base del Proyecto 1 | `KernelPrueba`, `MemoryManagerPrueba`, `InstructionModelPrueba`, `LinkedQueuePrueba` |

Para correr una desde la consola, en la raíz del proyecto:

```powershell
javac -encoding UTF-8 -cp "lib/*" -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
java -cp "out;lib/*" avilaos.hardware.disk.PoliticasDiscoPrueba
```

## Pendiente

- Fase 5 (interfaz): carga de los dos JSON, selector de política de disco, mapa
  del disco, tabla de asignación, cola del disco, colas de suspendidos,
  métricas con la gráfica del cabezal y botón para guardar el sistema de
  archivos. Cuando esté, se completa aquí la sección de qué se ve en la ventana.
- Fase 6: experimentos con las 4 políticas y con y sin suspensión, informe,
  Clean and Build en NetBeans, tag `v2.0`.
