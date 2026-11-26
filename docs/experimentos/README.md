# Experimentos del Proyecto 2 (F6.1)

Comparación de las políticas de disco y del efecto de la suspensión. Las tablas
y gráficas completas están en [`resultados.md`](resultados.md); las capturas de
la aplicación, en [`capturas/`](capturas/).

## Cómo se midió

- El reloj se maneja a mano (un ciclo = `Computer.onTick` + `Disk.onTick`), así
  que los números salen iguales en cada corrida.
- Escenarios de disco aislado: anexo del enunciado y casos P1 a P4, con todas
  las solicitudes en cola desde el ciclo 0.
- Sistema completo: los dos JSON del enunciado (`config/p2/`), con velocidad del
  cabezal 5 y con la velocidad por defecto (1).
- Suspensión: `config/p2/suspension.json` y
  [`carga_suspension.json`](carga_suspension.json) (3 procesos de E/S y 3 de
  cálculo en 128 MB, creado para estos experimentos). El escenario "sin
  suspensión" quita el planificador a mediano plazo solo dentro del experimento
  (por reflexión, porque el programa no tiene un interruptor para eso).
- LOOK no es obligatoria; se incluye como referencia.
- La respuesta promedio es el tiempo desde que el proceso llega hasta que usa la
  CPU por primera vez.

Para repetirlos, desde la carpeta del proyecto:

```powershell
javac -encoding UTF-8 -cp "lib/*" -d out (Get-ChildItem -Recurse src, docs\experimentos -Filter *.java).FullName
java -cp "out;lib/*" Experimentos
```

## Conclusiones: políticas de disco

| Escenario | FIFO | SSTF | SCAN | C-SCAN | LOOK |
| --- | ---: | ---: | ---: | ---: | ---: |
| Anexo: desplazamiento | 518 | 208 | 332 | **192** | 314 |
| Anexo: espera promedio | 171,6 | **58,1** | 184,0 | 118,9 | 174,4 |
| P1 a P4: desplazamiento | 644 | 236 | 337 | **183** | 299 |
| P1 a P4: espera promedio | 321,0 | 88,3 | 93,0 | **73,8** | 85,6 |
| JSON del enunciado (vel. 5): desplazamiento | 644 | **299** | 537 | 313 | **299** |
| JSON del enunciado (vel. 5): espera promedio | 106,3 | **69,4** | 91,0 | 72,4 | 69,9 |
| JSON del enunciado (vel. 1): terminan bien de 8 | 4 | **8** | 4 | **8** | **8** |

- **FIFO** atiende en orden de llegada sin mirar dónde está el cabezal, así que
  es la que más se mueve en los tres escenarios (hasta 3,5 veces más que
  C-SCAN en P1 a P4) y casi siempre la que más hace esperar.
- **SSTF** da la espera promedio más baja en el anexo y con el sistema completo.
  Su riesgo es la inanición: si siguen llegando solicitudes cerca del cabezal,
  una lejana puede quedarse esperando (con estas cargas, finitas, no llega a
  pasar).
- **SCAN** barre hasta el extremo antes de volver. Eso le cuesta caro cuando
  quedan solicitudes atrás: en el anexo su espera promedio (184,0) es peor que
  la de FIFO. En el sistema completo se mueve todavía más (537) porque el disco
  real tiene 300 bloques: el barrido llega al 299, pasando por el swap.
- **C-SCAN** es la que menos se mueve en el disco aislado y tiene esperas
  parejas. Hay que tener presente que, como pide el anexo, el salto de regreso
  al bloque 0 no se cuenta en el desplazamiento.
- **LOOK** se comporta como SCAN sin ir hasta los extremos; con el sistema
  completo empata con SSTF.
- **La política decide si se cumplen los deadlines.** Con 1 posición por ciclo,
  FIFO y SCAN pierden 4 de los 8 procesos del enunciado y SSTF, C-SCAN y LOOK
  terminan los 8. En esa corrida, el desplazamiento y la espera de FIFO y SCAN
  no son comparables: solo cuentan las 4 solicitudes que alcanzaron a atenderse.

En resumen, para estas cargas **SSTF y C-SCAN son las mejores opciones**: SSTF
si importa la espera promedio, C-SCAN si además se quiere evitar que una
solicitud quede relegada.

## Conclusiones: suspensión

| Escenario (disco FIFO) | Con suspensión (128 MB) | Sin suspensión (128 MB) | 1024 MB |
| --- | ---: | ---: | ---: |
| `suspension.json`: "Urgente" entra a RAM (ciclo) | 129 | 309 | 1 |
| `suspension.json`: "Urgente" termina (ciclo) | 173 | 342 | 40 |
| `suspension.json`: respuesta promedio | 46,0 | 106,0 | 5,3 |
| `suspension.json`: ciclos totales | 502 | 342 | 309 |
| `suspension.json`: desplazamiento | 560 | 45 | 45 |
| `carga_suspension.json`: "Calculo_1" entra a RAM (ciclo) | 59 | 459 | 1 |
| `carga_suspension.json`: respuesta promedio | 73,5 | 275,5 | 48,5 |
| `carga_suspension.json`: ciclos totales | 653 | 708 | 565 |
| `carga_suspension.json`: uso de CPU | 39,1 % | 36,0 % | 45,1 % |

- Suspender a un Bloqueado **adelanta mucho a los que esperan en Nuevos**: el
  proceso observado entra 180 ciclos antes en `suspension.json` y 400 antes en
  la carga mayor, y la respuesta promedio baja a menos de la mitad.
- **No es gratis**: escribir y leer el swap son solicitudes de disco (un bloque
  por MB). En `suspension.json` el desplazamiento pasa de 45 a 560 bloques y el
  total termina 160 ciclos más tarde (47 % más), porque los suspendidos tienen
  que volver para poder terminar.
- **Compensa cuando los bloqueados retienen la RAM mucho tiempo y hay trabajo de
  CPU esperando**: con la carga mayor, además de mejorar la respuesta, el total
  baja de 708 a 653 ciclos y el uso de CPU sube de 36,0 % a 39,1 %.
- Con RAM suficiente (1024 MB) todo es mejor: la suspensión reparte una memoria
  escasa, no la reemplaza.
- Con suspensión activa la política de disco influye poco en esta carga, porque
  la cola del disco casi siempre es corta. La excepción es SCAN, que se mueve más
  (788 bloques contra 270 de FIFO) y alarga el total de 653 a 695 ciclos
  (tabla 7 de `resultados.md`).

## Capturas

Son de la ventana real, con el reloj avanzado a mano (por eso el botón Iniciar
aparece habilitado).

| Archivos | Qué muestran |
| --- | --- |
| `politica1` a `politica5`, `_disco` | Pestaña Disco en el ciclo 60 con el JSON del enunciado (vel. 5): mapa, cabezal, cola y tabla de asignación |
| `politica1` a `politica5`, `_metricas` | Ventana de métricas al terminar cada corrida |
| `app_grafica_cabezal_cambio_de_politica` | Gráfica del cabezal de la app, cambiando de FIFO a SSTF en el ciclo 75 |
| `suspension_1` a `suspension_4` | `suspension.json`: suspendidos, swap ocupado, reactivación y métricas |
| `carga_1` a `carga_4` | Lo mismo con `carga_suspension.json` |
