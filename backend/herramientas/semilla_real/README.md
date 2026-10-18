# Trazabilidad de la semilla real

`backend/alertas_semilla.json` es la exportación real de la plataforma de detección del proyecto
(zeek-lab) sobre CICIDS2017: 516 episodios armados con 82.878 flujos marcados por al menos un detector.
La exportación se verificó contra los resultados documentados de la plataforma y coincide en todo (catálogo,
aciertos y errores de los tres métodos en martes y viernes, y predicciones de prueba del Random Forest).

Estos archivos quedan como registro de cómo se generó. No hace falta correrlos para usar la semilla:
el backend ya la carga desde `backend/alertas_semilla.json`.

| Archivo | Qué es |
|---|---|
| `exportar_semilla_real.py` | Script que corre en zeek-lab, en la carpeta de la plataforma. Lee los puntajes por flujo de los tres detectores, los metadatos, el contexto y las predicciones del Random Forest, y arma la semilla. No usa torch ni modifica nada. |
| `rearmar_semilla.py` | Rearma los episodios desde `semilla_flujos.parquet` sin necesidad de zeek-lab. Con corte de 300 s reproduce exacto la exportación original de 5 minutos; el corte vigente es de 15 minutos (900 s). |
| `evidencias_semilla_real.txt` | Salida de la verificación: las cifras de la exportación contra lo documentado. |

`semilla_flujos.parquet` (los 82.878 flujos con sus puntajes, para rearmar con otro corte) no se versiona
por tamaño; está en `C:\Users\melin\Desktop\semilla_real\`.

**No editar `alertas_semilla.json` a mano.** Si hace falta otro corte u otra regla de episodios, se rearma
con `rearmar_semilla.py` desde `semilla_flujos.parquet`.
