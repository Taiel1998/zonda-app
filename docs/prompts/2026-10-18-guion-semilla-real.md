# 18 de octubre de 2026: el guion de la demo con la semilla real

## Qué se pidió

El 17/10 a la noche la demo pasó a una base nueva con la semilla real (516 alertas de CICIDS2017) y el teléfono se pareó otra vez. Al probar el recorrido corto salió que el guion todavía suponía la semilla de ejemplo: con `ZONDA_REVISAR_SEMILLA=1`, Estado arranca en amarillo y no en rojo, y el paso 9 revisaba una alta de la semilla que ya no existe. Se pidió ajustar el guion a la semilla real, sumar lo que se vio de Telegram en el paso 5 y corregir cómo los README del backend cuentan contra qué se verificó la semilla.

Resumen del pedido, después del plan: solo documentación, en una rama aparte, con los commits a nombre de Melinda. Cómo quedan los pasos 6 y 9 lo decide Taiel.

## Qué salió

- Con la semilla real y las revisiones, Estado arranca con 0 altas, 60 medias y 99 bajas sin revisar: las dos altas de la semilla (fuerza bruta por FTP y por SSH de pc-rrhh a srv-sueldos) salen revisadas como ataque real. Sale siempre igual, porque el sorteo de qué alertas se revisan usa un valor fijo.
- Antes de tocar el guion, el asistente cargó la semilla en una base aparte, con el mismo código de la API, y siguió los pasos de la demo para sacar las cantidades. Salieron tres cosas que el guion no decía: en el paso 10 bis, con srv-sueldos crítico, 12 medias pasan a alta y Estado vuelve a rojo; por eso en el paso 11 quedan 95 bajas y no 99; y en el gráfico de 7 días la fuerza bruta cae tres días antes de la demo, no un viernes.
- El guion queda así: el paso 5 sin "Iniciar" si el chat con el bot ya existe, el paso 6 en amarillo, los pasos 7 y 8 con el rojo y la vuelta a amarillo, el paso 9 con una media revisada sin conexión y los pasos 10, 10 bis y 11 con las cantidades de la semilla real. En "Media hora antes", la base se borra con su `-wal` y su `-shm`, como aprendimos al armar la base nueva.
- Los README del backend dicen que la exportación se verificó contra los resultados documentados de la plataforma de detección, y el de la semilla real ya no da la ruta de la carpeta en zeek-lab.

## Qué corrigió o decidió el equipo

- Taiel eligió, entre las opciones que le presentó el asistente con el semáforo de la app dibujado paso por paso, que el paso 6 arranque en amarillo, como queda la semilla, y que el rojo aparezca recién con el ataque del paso 7. Se descartó largar una alta de FTP media hora antes para arrancar en rojo: el ataque del paso 7 ya no cambiaba el color y sumaba algo más para preparar. También se descartó cambiar la semilla para que no revise las altas: tocaba código y el semáforo habría dicho "Última alta sin revisar: hace 3 días".
- Para el paso 9, Taiel eligió revisar sin conexión la primera media de la lista (un paquete ICMP de afuera a pc-05, falso positivo con el motivo "Otro") en lugar de largar otra alta: muestra la cola sin conexión igual, suma un falso positivo después del ataque real del paso 8 y no repite el aviso de Telegram.
- Commits a nombre de Melinda, con su acuerdo.

## Cómo se comprobó

- Las cantidades salen de la base aparte, con el código de la API de `main`: 0, 60 y 99 al arrancar, 59 medias después del paso 9, 12 altas, 51 medias y 95 bajas con srv-sueldos crítico, y `zonda tranquila` revisando 12 altas y 51 medias. Las del arranque coinciden con lo que se vio en el teléfono el 17/10.
- Los textos entre comillas se buscaron en los textos de la app (`strings.xml`) y en el código de la API.
