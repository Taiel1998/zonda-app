import hashlib, json, os, sys, time
from datetime import datetime, timezone

import numpy as np
import pandas as pd
import pyarrow.parquet as pq

# -----------------------------------------------------------------------------------------------
# Exportacion de las detecciones reales a la semilla de la app Zonda (alertas_semilla.json)
#
# Convierte lo que detecto la plataforma sobre CICIDS2017 (martes y viernes) en los episodios que
# carga el backend de la app (backend/app/semilla.py), con el mismo formato que la semilla de ejemplo.
#
#  Flujos      Alcance y etiquetas vigentes. Martes: destino 192.168.10.0/24, fuerza bruta = 172.16.0.1
#              a los puertos 21 y 22 (Seccion 52). Viernes: friday_alcance_escaneo_cic.parquet y
#              friday_alcance_ddos_cic.parquet (Seccion 57.4). El resto del trafico del atacante se
#              excluye, como en el Random Forest.
#  Detectores  Los tres sin modificacion, con los puntajes por flujo ya guardados (scores_tuesday.parquet,
#              scores_escaneo_cic.parquet, scores_ddos_cic.parquet): reglas escenario B, umbrales con
#              score > 1 y autoencoder v3 con error > tau. Se verifican contra lo documentado.
#  Tipo        rf_test_predicciones.parquet (Seccion 58): clase mas votada; benigno pasa a
#              probable_falsa_alarma y, si la clase mas votada no alcanza theta = 0,50 de los arboles,
#              sin_tipificar (misma regla que rf_oe03_desconocido.py). Solo existe sobre la particion de
#              prueba del clasificador.
#  Particion   Por eso cada ataque entra solo con su tramo de prueba (el ultimo 30 % por orden temporal);
#              los flujos de su tramo de entrenamiento quedan fuera, los marque quien los marque, para que
#              ningun episodio muestre un ataque recortado a medias. El benigno de martes y viernes entra
#              completo: el clasificador se entreno con el benigno del lunes.
#  Episodios   Flujos marcados por al menos un metodo, agrupados por dia, origen y destino, con hasta
#              5 minutos sin flujos nuevos (decision 3 de la app).
#
# No usa torch ni modifica artefactos existentes: solo lee data/ y escribe en semilla/.
# Uso (desde ~/pfi): ~/pfi-env/bin/python exportar_semilla_real.py
# Salidas: semilla/alertas_semilla.json y semilla/semilla_flujos.parquet (los flujos marcados, para
# poder rearmar los episodios sin volver a correr nada).
# -----------------------------------------------------------------------------------------------

try:
    sys.stdout.reconfigure(line_buffering=True)
except Exception:
    pass

RUTA = "data"
SALIDA = "semilla"
TAU = 0.00306383
THETA = 0.50
GAP = 300
UMBRAL_CATALOGO = 50
ATACANTE = "172.16.0.1"
INTERNO = "192.168.10."
ZONA = "America/Halifax"
DIAS = {"tuesday": "martes", "friday": "viernes"}

# Nombres ilustrativos de los equipos: los del diseno de la app (los mismos de la semilla de ejemplo).
# Las IP son las reales de CICIDS2017, donde los equipos no tienen nombre.
EQUIPOS = {
    "192.168.10.3": "srv-facturacion",
    "192.168.10.50": "srv-sueldos",
    "192.168.10.51": "srv-archivos",
    "192.168.10.5": "pc-05",
    "192.168.10.8": "pc-08",
    "192.168.10.9": "pc-09",
    "192.168.10.12": "pc-contabilidad",
    "192.168.10.14": "pc-14",
    "192.168.10.15": "pc-15",
    "192.168.10.16": "impresora-2",
    "192.168.10.17": "pc-17",
    "192.168.10.19": "pc-19",
    "192.168.10.25": "mac-25",
    "172.16.0.1": "pc-rrhh",
}

# Nombre del servicio que muestra la app, por puerto de destino (como la semilla de ejemplo).
SERVICIOS = {
    20: "ftp", 21: "ftp", 22: "ssh", 23: "telnet", 25: "smtp", 53: "dns", 67: "dhcp", 68: "dhcp", 69: "tftp",
    80: "http", 88: "kerberos", 110: "pop3", 111: "rpcbind", 123: "ntp", 135: "rpc", 137: "netbios",
    138: "netbios", 139: "netbios", 143: "imap", 161: "snmp", 162: "snmp", 389: "ldap", 443: "https",
    445: "smb", 464: "kerberos", 465: "smtp", 514: "syslog", 587: "smtp", 631: "ipp", 636: "ldap",
    993: "imap", 995: "pop3", 1433: "mssql", 1900: "ssdp", 3268: "ldap", 3269: "ldap", 3306: "mysql",
    3389: "rdp", 5353: "mdns", 5355: "llmnr", 5432: "postgresql", 5900: "vnc", 8080: "http", 8443: "https",
}

TIPO_DE_CLASE = {"benigno": "probable_falsa_alarma", "fuerza_bruta": "fuerza_bruta", "escaneo": "escaneo", "ddos": "ddos"}
SEVERIDAD = {"fuerza_bruta": "alta", "sin_tipificar": "alta", "escaneo": "media", "ddos": "media",
             "probable_falsa_alarma": "baja", None: "media"}
PRIORIDAD = {"fuerza_bruta": 0, "sin_tipificar": 1, "escaneo": 2, "ddos": 3, "probable_falsa_alarma": 4}

T0 = time.time()
DIFERENCIAS = []


def titulo(texto):
    print("")
    print("=" * 100)
    print(texto)
    print("=" * 100)


def verificar(nombre, obtenido, documentado):
    ok = obtenido == documentado
    if not ok:
        DIFERENCIAS.append(nombre)
    print("  " + nombre.ljust(60) + str(obtenido).rjust(9) + "   documentado " + str(documentado).rjust(9) +
          ("   OK" if ok else "   <-- DIFERENTE"))


def proto_v3(dia, n):
    archivo = RUTA + "/" + dia + ("_preprocessed.parquet" if dia == "monday" else "_preprocessed_v3.parquet")
    p = pd.read_parquet(archivo, columns=["proto_icmp", "proto_tcp", "proto_udp"])
    assert len(p) == n, dia + ": preprocessed con distinta cantidad de filas que meta"
    return pd.Series(p.to_numpy().argmax(axis=1)).map({0: "icmp", 1: "tcp", 2: "udp"}).to_numpy()


def contexto(dia, uids):
    archivo = RUTA + "/" + dia + "_context.parquet"
    cols = pq.read_schema(archivo).names
    c = pd.read_parquet(archivo, columns=[x for x in ["uid", "ctx_flujos_mismo_servicio"] if x in cols]).reset_index(drop=True)
    assert len(c) == len(uids), dia + ": context con distinta cantidad de filas que meta"
    if "uid" in c.columns:
        alineado = bool((c["uid"].to_numpy() == np.asarray(uids)).all())
        print("  " + dia.ljust(8) + " context alineado con meta por uid: " + str(alineado))
        if not alineado:
            DIFERENCIAS.append(dia + ": context no alineado con meta")
    return c["ctx_flujos_mismo_servicio"].to_numpy()


def cargar_meta(dia):
    archivo = RUTA + "/" + dia + "_meta.parquet"
    disp = pq.read_schema(archivo).names
    quiero = ["ts", "uid", "id.orig_h", "id.resp_h", "id.resp_p", "history", "conn_state", "service"]
    meta = pd.read_parquet(archivo, columns=[c for c in quiero if c in disp]).reset_index(drop=True)
    assert meta["uid"].is_unique, dia + ": uid duplicados en meta"
    meta["proto"] = proto_v3(dia, len(meta))
    meta["ctx"] = contexto(dia, meta["uid"].to_numpy())
    meta["id.orig_h"] = meta["id.orig_h"].astype(str)
    meta["id.resp_h"] = meta["id.resp_h"].astype(str)
    print("  " + dia.ljust(8) + " flujos " + str(len(meta)).rjust(8) + " | columnas de meta: " + ", ".join(disp))
    return meta


def detectores(D, dia):
    # Magnitudes de cada metodo para mostrar en la app, recalculadas como en eval_tuesday_scores.py y
    # eval_friday_cic.py y comparadas con las guardadas. Los veredictos salen de los puntajes guardados.
    u_lunes = D["id.resp_p"].map(umbrales)
    D["umbral_lunes"] = u_lunes.to_numpy()
    D["umbral"] = u_lunes.fillna(1).clip(lower=1).to_numpy()
    dif = np.abs(D["ctx"].to_numpy() / D["umbral"].to_numpy() - D["score_umbral"].to_numpy())
    print("  " + dia + ": diferencia maxima entre el puntaje de umbrales recalculado y el guardado: " +
          format(float(np.nanmax(dif)), ".2e"))
    pares = pd.Series(list(zip(D["id.resp_h"], D["id.resp_p"])), index=D.index)
    ok = pares.isin(catalogo).to_numpy() & D["proto"].isin(["tcp", "udp"]).to_numpy()
    distintos = int((~ok != D["regla_B"].to_numpy(dtype=bool)).sum())
    print("  " + dia + ": flujos donde la regla B recalculada difiere de la guardada: " + str(distintos))
    if distintos:
        DIFERENCIAS.append(dia + ": regla B recalculada distinta de la guardada")
    D["par"] = D["id.resp_h"] + ":" + D["id.resp_p"].astype(str)
    D["ae"] = D["error_ae"].to_numpy() > TAU
    D["umb"] = D["score_umbral"].to_numpy() > 1
    D["reg"] = D["regla_B"].to_numpy(dtype=bool)
    return D


def tp_fp_fn(D, positivo, negativo, col):
    v = D[col].to_numpy()
    return int((v & positivo).sum()), int((v & negativo).sum()), int((~v & positivo).sum())


def iso(ts):
    return datetime.fromtimestamp(float(ts), tz=timezone.utc).isoformat(timespec="seconds")


def hora_local(ts):
    return pd.Timestamp(float(ts), unit="s", tz="UTC").tz_convert(ZONA).strftime("%H:%M")


def nombre_servicio(puerto, proto, zeek):
    if proto == "icmp":
        return "icmp"
    if int(puerto) in SERVICIOS:
        return SERVICIOS[int(puerto)]
    if zeek is not None:
        validos = [s for s in zeek if isinstance(s, str) and s not in ("", "-")]
        if validos:
            return pd.Series(validos).value_counts().index[0].split(",")[0]
    return "otro"


def mas_frecuente(serie):
    vc = serie.value_counts()
    return sorted(vc[vc == vc.max()].index, key=lambda x: (str(type(x)), x))[0]


def texto(v):
    return None if v is None or (isinstance(v, float) and np.isnan(v)) else str(v)


# =================================================================================================
titulo("1. CALIBRACION DEL LUNES (catalogo de las reglas y umbrales por puerto, sin modificacion)")
print("  python " + sys.version.split()[0] + " | pandas " + pd.__version__ + " | numpy " + np.__version__)
mon = pd.read_parquet(RUTA + "/monday_meta.parquet", columns=["uid", "id.resp_h", "id.resp_p"]).reset_index(drop=True)
mon["proto"] = proto_v3("monday", len(mon))
mon["ctx"] = contexto("monday", mon["uid"].to_numpy())
mon_int = mon["id.resp_h"].astype(str).str.startswith(INTERNO)
cnt = mon[mon_int & mon["proto"].isin(["tcp", "udp"])].groupby(["id.resp_h", "id.resp_p"]).size()
catalogo = set(cnt[cnt >= UMBRAL_CATALOGO].index)
umbrales = mon[mon_int].groupby("id.resp_p")["ctx"].max()
verificar("pares autorizados en el catalogo", len(catalogo), 16)
verificar("umbral del puerto 21 (maximo del lunes)", int(umbrales.get(21, -1)), 1)
verificar("umbral del puerto 22 (maximo del lunes)", int(umbrales.get(22, -1)), 2)
print("  Catalogo: " + ", ".join(h + ":" + str(p) for h, p in sorted(catalogo)))
del mon, mon_int, cnt

# =================================================================================================
titulo("2. MARTES: ALCANCE, ETIQUETA Y PUNTAJES (Secciones 52 y 59.2)")
tue = cargar_meta("tuesday")
dst = tue["id.resp_h"].str.startswith(INTERNO).to_numpy()
atac = (tue["id.orig_h"] == ATACANTE).to_numpy()
etq = atac & tue["id.resp_p"].isin([21, 22]).to_numpy()
T = tue[dst].copy()
T["etiqueta"] = etq[dst]
T["clase"] = np.where(T["etiqueta"], "fuerza_bruta", np.where(atac[dst], "excluido", "benigno"))
T["subgrupo"] = np.where(T["etiqueta"], np.where(T["id.resp_p"] == 21, "ftp", "ssh"),
                         np.where(T["clase"] == "benigno", "benigno_tuesday", "excluido"))
del tue, dst, atac, etq
sc = pd.read_parquet(RUTA + "/scores_tuesday.parquet")
n_alc = len(T)
T = T.merge(sc.rename(columns={"etiqueta": "etiqueta_sc"}), on="uid", how="inner", validate="one_to_one")
assert len(T) == n_alc == len(sc), "martes: el alcance no coincide con scores_tuesday.parquet"
assert (T["etiqueta"].to_numpy() == T["etiqueta_sc"].to_numpy(dtype=bool)).all(), "martes: etiqueta distinta de scores_tuesday.parquet"
T["dia"] = "tuesday"
T = detectores(T, "martes")
pos = T["etiqueta"].to_numpy()
neg = ~pos
verificar("martes: flujos en alcance", len(T), 213338)
verificar("martes: flujos de fuerza bruta (puertos 21 y 22)", int(pos.sum()), 6971)
tp, fp, fn = tp_fp_fn(T, pos, neg, "reg")
verificar("martes: reglas B, TP", tp, 0)
verificar("martes: reglas B, FP", fp, 1788)
tp, fp, fn = tp_fp_fn(T, pos, neg, "umb")
verificar("martes: umbrales, TP", tp, 6966)
verificar("martes: umbrales, FP", fp, 32)
verificar("martes: umbrales, FN", fn, 5)
tp, fp, fn = tp_fp_fn(T, pos, neg, "ae")
verificar("martes: autoencoder, TP", tp, 6937)
verificar("martes: autoencoder, FP", fp, 470)
verificar("martes: autoencoder, FN", fn, 34)
print("  Trafico del atacante a otros puertos (fuera de las clases, se excluye): " +
      str(int((T["clase"] == "excluido").sum())) + " flujos")
del sc

# =================================================================================================
titulo("3. VIERNES: ALCANCE CIC, ETIQUETAS Y PUNTAJES (Seccion 57)")
fri = cargar_meta("friday")
a_esc = pd.read_parquet(RUTA + "/friday_alcance_escaneo_cic.parquet", columns=["uid", "etiqueta"])
a_ddos = pd.read_parquet(RUTA + "/friday_alcance_ddos_cic.parquet", columns=["uid", "etiqueta"])
ben = np.sort(a_esc.loc[~a_esc["etiqueta"].to_numpy(dtype=bool), "uid"].to_numpy())
ben_d = np.sort(a_ddos.loc[~a_ddos["etiqueta"].to_numpy(dtype=bool), "uid"].to_numpy())
assert len(ben) == len(ben_d) and (ben == ben_d).all(), "viernes: el benigno difiere entre los dos alcances"
u_esc = a_esc.loc[a_esc["etiqueta"].to_numpy(dtype=bool), "uid"].to_numpy()
u_ddos = a_ddos.loc[a_ddos["etiqueta"].to_numpy(dtype=bool), "uid"].to_numpy()
assert not (set(u_esc) & set(u_ddos)), "viernes: flujos marcados a la vez como escaneo y DDoS"
verificar("viernes: flujos en el alcance del escaneo", len(a_esc), 354510)
verificar("viernes: flujos de escaneo", len(u_esc), 160411)
verificar("viernes: flujos en el alcance del DDoS", len(a_ddos), 289782)
verificar("viernes: flujos de DDoS", len(u_ddos), 95683)
verificar("viernes: benigno (el mismo en los dos alcances)", len(ben), 194099)
s_esc = pd.read_parquet(RUTA + "/scores_escaneo_cic.parquet")
s_ddos = pd.read_parquet(RUTA + "/scores_ddos_cic.parquet")
b1 = s_esc[~s_esc["etiqueta"].to_numpy(dtype=bool)].set_index("uid").sort_index()
b2 = s_ddos[~s_ddos["etiqueta"].to_numpy(dtype=bool)].set_index("uid").sort_index()
assert (b1.index == b2.index).all(), "viernes: el benigno difiere entre los dos archivos de puntajes"
print("  Benigno igual en scores_escaneo_cic y scores_ddos_cic: error " +
      format(float(np.abs(b1["error_ae"] - b2["error_ae"]).max()), ".1e") + ", umbrales " +
      format(float(np.abs(b1["score_umbral"] - b2["score_umbral"]).max()), ".1e") + ", reglas B distintas " +
      str(int((b1["regla_B"] != b2["regla_B"]).sum())))
del b1, b2, ben_d
puntajes = pd.concat([s_esc, s_ddos[s_ddos["etiqueta"].to_numpy(dtype=bool)]], ignore_index=True)
F = fri[fri["uid"].isin(puntajes["uid"])].copy()
del fri
F = F.merge(puntajes.drop(columns=["etiqueta"]), on="uid", how="inner", validate="one_to_one")
assert len(F) == len(puntajes) == len(ben) + len(u_esc) + len(u_ddos), "viernes: el alcance no coincide con los puntajes"
F["clase"] = np.where(F["uid"].isin(u_esc), "escaneo", np.where(F["uid"].isin(u_ddos), "ddos", "benigno"))
F["subgrupo"] = np.where(F["clase"] == "benigno", "benigno_friday", F["clase"])
F["etiqueta"] = F["clase"].to_numpy() != "benigno"
F["dia"] = "friday"
del puntajes, s_esc, s_ddos, a_esc, a_ddos
F = detectores(F, "viernes")
neg = (F["clase"] == "benigno").to_numpy()
for nombre, clase, doc in [("escaneo", "escaneo", {"reg": (159885, 526), "umb": (156003, 4408), "ae": (159840, 571)}),
                           ("DDoS", "ddos", {"reg": (95683, 0), "umb": (95682, 1), "ae": (95675, 8)})]:
    pos = (F["clase"] == clase).to_numpy()
    for col, metodo in [("reg", "reglas B"), ("umb", "umbrales"), ("ae", "autoencoder")]:
        tp, fp, fn = tp_fp_fn(F, pos, neg, col)
        verificar("viernes " + nombre + ": " + metodo + ", TP", tp, doc[col][0])
        verificar("viernes " + nombre + ": " + metodo + ", FN", fn, doc[col][1])
for col, metodo, doc in [("reg", "reglas B", 1529), ("umb", "umbrales", 93), ("ae", "autoencoder", 452)]:
    verificar("viernes benigno: " + metodo + ", FP", int(F[col].to_numpy()[neg].sum()), doc)

# =================================================================================================
titulo("4. SEGUNDA ETAPA: PREDICCIONES DE PRUEBA DEL RANDOM FOREST (Seccion 58)")
R = pd.read_parquet(RUTA + "/rf_test_predicciones.parquet")
cols_p = [c for c in R.columns if c.startswith("proba_")]
print("  Columnas: " + ", ".join(R.columns))
for s, n in [("ftp", 1198), ("ssh", 894), ("escaneo", 48123), ("ddos", 28705),
             ("benigno_tuesday", 470), ("benigno_friday", 452)]:
    verificar("prueba del Random Forest, " + s, int((R["subgrupo"] == s).sum()), n)
R["conf"] = R[cols_p].to_numpy(dtype=float).max(axis=1)
R["tipo"] = np.where(R["conf"].to_numpy() >= THETA, R["pred"].map(TIPO_DE_CLASE).to_numpy(dtype=object), "sin_tipificar")
print("")
print("  Tipo por subgrupo de prueba (theta = " + format(THETA, ".2f") + "; benigno -> probable_falsa_alarma):")
for s in ["ftp", "ssh", "escaneo", "ddos", "benigno_tuesday", "benigno_friday"]:
    vc = R.loc[R["subgrupo"] == s, "tipo"].value_counts()
    print("    " + s.ljust(16) + " " + ", ".join(str(k) + " " + str(int(v)) for k, v in vc.items()))
todo = pd.concat([T, F], ignore_index=True)
del T, F
todo = todo.merge(R[["dia", "uid", "subgrupo", "error", "pred", "conf", "tipo"]].rename(
    columns={"subgrupo": "subgrupo_rf", "error": "error_rf"}), on=["dia", "uid"], how="left", validate="one_to_one")
todo["en_prueba_rf"] = todo["subgrupo_rf"].notna().to_numpy()
print("")
verificar("filas de prueba del Random Forest encontradas en el alcance", int(todo["en_prueba_rf"].sum()), len(R))
m = todo["en_prueba_rf"].to_numpy()
mal = int((todo.loc[m, "subgrupo_rf"].to_numpy() != todo.loc[m, "subgrupo"].to_numpy()).sum())
verificar("filas de prueba con otro subgrupo que el del alcance", mal, 0)
dif = np.abs(todo.loc[m, "error_rf"].to_numpy(dtype=float) - todo.loc[m, "error_ae"].to_numpy(dtype=float))
print("  Error del autoencoder: diferencia maxima entre rf_test_predicciones y los puntajes: " + format(float(dif.max()), ".2e") +
      " | flujos donde cambia el veredicto (error > tau): " +
      str(int(((todo.loc[m, "error_rf"].to_numpy(dtype=float) > TAU) != todo.loc[m, "ae"].to_numpy()).sum())))
del R

# =================================================================================================
titulo("5. FLUJOS QUE ENTRAN EN LA SEMILLA")
ataque = todo["clase"].isin(["fuerza_bruta", "escaneo", "ddos"]).to_numpy()
entra = (todo["clase"] == "benigno").to_numpy() | (ataque & todo["en_prueba_rf"].to_numpy())
marcado = todo["ae"].to_numpy() | todo["umb"].to_numpy() | todo["reg"].to_numpy()
print("  " + "subgrupo".ljust(17) + "flujos".rjust(9) + "marcados".rjust(10) + "entran".rjust(9) +
      "fuera (entren.)".rjust(17) + "   entran marcados por:  IA  umbrales  reglas")
for s in ["ftp", "ssh", "escaneo", "ddos", "benigno_tuesday", "benigno_friday", "excluido"]:
    q = (todo["subgrupo"] == s).to_numpy()
    if not q.any():
        continue
    e = q & entra & marcado
    print("  " + s.ljust(17) + str(int(q.sum())).rjust(9) + str(int((q & marcado).sum())).rjust(10) +
          str(int(e.sum())).rjust(9) + str(int((q & marcado & ~entra).sum())).rjust(17) +
          "".ljust(24) + str(int(todo["ae"].to_numpy()[e].sum())).rjust(6) + str(int(todo["umb"].to_numpy()[e].sum())).rjust(10) +
          str(int(todo["reg"].to_numpy()[e].sum())).rjust(8))
S = todo[entra & marcado].copy()
del todo, ataque, entra, marcado
S["tipo_flujo"] = np.where(S["ae"].to_numpy(), S["tipo"].to_numpy(dtype=object), None)
sin_pred = S["ae"].to_numpy() & S["tipo"].isna().to_numpy()
print("  Flujos marcados por el autoencoder sin prediccion del Random Forest: " + str(int(sin_pred.sum())) +
      ("" if not sin_pred.any() else "  (quedan como sin_tipificar, sin confianza)"))
if sin_pred.any():
    S.loc[sin_pred, "tipo_flujo"] = "sin_tipificar"
    DIFERENCIAS.append("flujos del autoencoder sin prediccion del Random Forest")
print("  Flujos marcados que entran: " + str(len(S)))

# =================================================================================================
titulo("6. EPISODIOS (dia, origen y destino, hasta " + str(GAP) + " s sin flujos nuevos)")
S = S.sort_values(["dia", "id.orig_h", "id.resp_h", "ts", "uid"], kind="mergesort").reset_index(drop=True)
clave = S["dia"] + "|" + S["id.orig_h"] + "|" + S["id.resp_h"]
nuevo = (clave != clave.shift()).to_numpy() | (S["ts"].diff().to_numpy() > GAP)
S["episodio"] = np.cumsum(nuevo) - 1
alertas = []
for e, g in S.groupby("episodio", sort=True):
    ref = g.iloc[0]
    vc = g["id.resp_p"].value_counts()
    puerto = int(min(vc[vc == vc.max()].index))
    del_puerto = g[g["id.resp_p"] == puerto]
    protocolo = str(mas_frecuente(del_puerto["proto"]))
    servicio = nombre_servicio(puerto, protocolo, del_puerto["service"].tolist() if "service" in g.columns else None)
    n_ia, n_umb, n_reg = int(g["ae"].sum()), int(g["umb"].sum()), int(g["reg"].sum())
    i = g["score_umbral"].idxmax()
    u_lunes = g.at[i, "umbral_lunes"]
    par = mas_frecuente(g.loc[g["reg"], "par"]) if n_reg else mas_frecuente(g["par"])
    tipo, conf, por_tipo = None, None, {}
    if n_ia:
        vt = g.loc[g["ae"], "tipo_flujo"].value_counts()
        tipo = sorted(vt.index, key=lambda t: (-int(vt[t]), PRIORIDAD.get(t, 9)))[0]
        c = g.loc[g["ae"] & (g["tipo_flujo"] == tipo), "conf"].dropna()
        conf = round(float(c.mean()), 4) if len(c) else None
        por_tipo = {str(k): int(v) for k, v in vt.items()}
    n_atq = int(g["etiqueta"].sum())
    dia = DIAS[ref["dia"]]
    alerta = {
        "id": "a-" + hashlib.sha1((dia + "|" + ref["id.orig_h"] + "|" + ref["id.resp_h"] + "|" + ref["uid"]).encode()).hexdigest()[:12],
        "dia": dia,
        "inicio": iso(g["ts"].iloc[0]),
        "fin": iso(g["ts"].max()),
        "cantidadFlujos": int(len(g)),
        "uidReferencia": str(ref["uid"]),
        "origenIp": ref["id.orig_h"],
        "destinoIp": ref["id.resp_h"],
        "puertoReferencia": int(ref["id.resp_p"]),
        "puerto": puerto,
        "puertosDistintos": int(g["id.resp_p"].nunique()),
        "servicio": servicio,
        "protocolo": protocolo,
    }
    if "conn_state" in g.columns:
        alerta["estadoConexion"] = texto(ref["conn_state"])
    if "history" in g.columns:
        alerta["history"] = texto(ref["history"])
    alerta.update({
        "veredictoIa": n_ia > 0,
        "flujosIa": n_ia,
        "errorReconstruccion": round(float(g["error_ae"].max()), 8),
        "umbralIa": TAU,
        "veredictoUmbrales": n_umb > 0,
        "flujosUmbrales": n_umb,
        "contexto60s": int(g.at[i, "ctx"]),
        "umbralServicio": int(g.at[i, "umbral"]),
        "umbralServicioLunes": None if pd.isna(u_lunes) else int(u_lunes),
        "veredictoReglas": n_reg > 0,
        "flujosReglas": n_reg,
        "parDestino": str(par),
        "parEnCatalogo": n_reg == 0,
        "tipoAtaque": tipo,
        "confianzaTipo": conf,
        "flujosPorTipo": por_tipo,
        "etiquetaVerdad": "ataque" if 2 * n_atq >= len(g) else "benigno",
        "flujosAtaque": n_atq,
    })
    alertas.append(alerta)
ids = [a["id"] for a in alertas]
assert len(ids) == len(set(ids)), "ids de episodio repetidos"
alertas.sort(key=lambda a: (a["inicio"], a["id"]))
A = pd.DataFrame(alertas)
A["tipo"] = A["tipoAtaque"].fillna("sin tipo (solo reglas o umbrales)")
A["severidad"] = A["tipoAtaque"].map(lambda t: SEVERIDAD.get(t if isinstance(t, str) else None))
print("  Episodios: " + str(len(A)) + " (martes " + str(int((A["dia"] == "martes").sum())) + ", viernes " +
      str(int((A["dia"] == "viernes").sum())) + ") con " + str(int(A["cantidadFlujos"].sum())) + " flujos")
if len(A) > 1000:
    print("  ATENCION: mas de 1.000 episodios (RF02 de la app habla de 1.000 alertas en cache).")
print("")
print("  " + "tipo".ljust(36) + "sever.".ljust(8) + "martes".rjust(8) + "viernes".rjust(9) + "flujos".rjust(9) + "   etiqueta ataque | benigno")
for t, g in A.groupby("tipo"):
    print("  " + str(t).ljust(36) + str(g["severidad"].iloc[0]).ljust(8) + str(int((g["dia"] == "martes").sum())).rjust(8) +
          str(int((g["dia"] == "viernes").sum())).rjust(9) + str(int(g["cantidadFlujos"].sum())).rjust(9) +
          "   " + str(int((g["etiquetaVerdad"] == "ataque").sum())).rjust(15) + " | " + str(int((g["etiquetaVerdad"] == "benigno").sum())))
print("")
print("  Por severidad recomendada: " + ", ".join(s + " " + str(int((A["severidad"] == s).sum())) for s in ["alta", "media", "baja"]))
combo = A["veredictoIa"].map({True: "IA", False: "--"}) + " " + A["veredictoUmbrales"].map({True: "U", False: "-"}) + " " + \
    A["veredictoReglas"].map({True: "R", False: "-"})
print("  Por metodos que lo marcan (IA, umbrales, reglas): " + ", ".join(k + " " + str(int(v)) for k, v in combo.value_counts().items()))
tam = pd.cut(A["cantidadFlujos"], [0, 1, 5, 20, 100, 10 ** 9], labels=["1", "2-5", "6-20", "21-100", "mas de 100"])
print("  Por cantidad de flujos: " + ", ".join(str(k) + ": " + str(int(v)) for k, v in tam.value_counts(sort=False).items()))


def linea(a):
    return ("    " + a["dia"].ljust(8) + hora_local(pd.Timestamp(a["inicio"]).timestamp()) + "-" +
            hora_local(pd.Timestamp(a["fin"]).timestamp()) + "  " + (a["origenIp"] + " -> " + a["destinoIp"]).ljust(32) +
            (a["servicio"] + "/" + str(a["puerto"])).ljust(14) + str(a["cantidadFlujos"]).rjust(7) + " flujos  " +
            (a["tipoAtaque"] if isinstance(a["tipoAtaque"], str) else "(sin tipo)").ljust(22) +
            " IA " + str(a["flujosIa"]).rjust(6) + " | U " + str(a["flujosUmbrales"]).rjust(6) +
            " | R " + str(a["flujosReglas"]).rjust(6) + " | etiqueta " + a["etiquetaVerdad"])


print("")
print("  Los 12 episodios con mas flujos (hora local del dataset):")
for _, a in A.sort_values("cantidadFlujos", ascending=False).head(12).iterrows():
    print(linea(a))
print("")
print("  Ejemplos por tipo (los 3 primeros de cada uno):")
for t, g in A.groupby("tipo"):
    print("  " + str(t) + ":")
    for _, a in g.head(3).iterrows():
        print(linea(a))
print("")
print("  Destinos:puerto mas frecuentes entre los episodios solo de reglas o umbrales:")
solo = A[A["tipoAtaque"].isna()]
if len(solo):
    print("    " + ", ".join(k + " " + str(int(v)) for k, v in (solo["destinoIp"] + ":" + solo["puerto"].astype(str)).value_counts().head(10).items()))
print("")
print("  Servicios: " + ", ".join(k + " " + str(int(v)) for k, v in A["servicio"].value_counts().items()))
ips_todas = pd.concat([A["origenIp"], A["destinoIp"]])
sin_nombre = ips_todas[~ips_todas.isin(list(EQUIPOS))].value_counts()
print("  IP sin nombre en el catalogo (la app muestra la IP): " +
      (", ".join(k + " (" + str(int(v)) + " episodios)" for k, v in sin_nombre.items()) if len(sin_nombre) else "ninguna"))

# =================================================================================================
titulo("7. ARCHIVOS")
os.makedirs(SALIDA, exist_ok=True)
ips = sorted(set(A["origenIp"]) | set(A["destinoIp"]))
semilla = {
    "generadoEn": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    "fuente": ("Exportacion real de la plataforma de deteccion (zeek-lab, exportar_semilla_real.py): CICIDS2017, martes "
               "(fuerza bruta FTP y SSH) y viernes (escaneo y DDoS), con el alcance y las etiquetas de las Secciones 52 y 57, "
               "los tres detectores sin modificacion (reglas escenario B, umbrales y autoencoder v3 con tau = p99 del lunes) "
               "y el tipo del Random Forest rf_v1 sobre su particion de prueba (Seccion 58)."),
    "zonaHorariaOriginal": ZONA,
    "nota": "Las fechas son las del dataset (julio de 2017, en UTC); el backend las corre para la demo.",
    "notaEquipos": ("Nombres de equipos ilustrativos (los del diseno de la app). Las IP, los flujos, los puntajes y los tipos son "
                    "reales. En CICIDS2017, 172.16.0.1 es la interfaz interna del firewall por la que entra el atacante externo."),
    "parametros": {
        "gapSegundos": GAP, "tau": TAU, "theta": THETA, "umbralCatalogo": UMBRAL_CATALOGO,
        "agrupacion": "dia, origen, destino",
        "particionTipos": "prueba del clasificador: ultimo 30 % de cada ataque (temporal, por subgrupo) y todo el benigno de martes y viernes",
        "escenarioReglas": "B (Zero Trust)",
        "flujoReferencia": "primer flujo del episodio",
        "magnitudes": "IA: error maximo del episodio; umbrales: flujo con mayor contexto/umbral; reglas: par fuera del catalogo mas frecuente",
        "tipoEpisodio": "el mas frecuente entre los flujos que marco el autoencoder; en empate, el de mayor severidad",
    },
    "resumen": {
        "episodios": int(len(A)),
        "flujos": int(A["cantidadFlujos"].sum()),
        "porTipo": {str(k): int(v) for k, v in A["tipo"].value_counts().items()},
        "porSeveridadRecomendada": {s: int((A["severidad"] == s).sum()) for s in ["alta", "media", "baja"]},
    },
    "catalogo": {
        "equipos": {ip: EQUIPOS.get(ip) for ip in ips},
        "servicios": sorted(set(A["servicio"])),
        "paresAutorizados": sorted(h + ":" + str(p) for h, p in catalogo),
    },
    "alertas": alertas,
}
ruta_json = SALIDA + "/alertas_semilla.json"
with open(ruta_json, "w", encoding="utf-8") as f:
    json.dump(semilla, f, ensure_ascii=False, indent=1)
guardar = [c for c in ["dia", "uid", "ts", "id.orig_h", "id.resp_h", "id.resp_p", "proto", "history", "conn_state", "service",
                       "clase", "subgrupo", "etiqueta", "en_prueba_rf", "error_ae", "ae", "ctx", "umbral_lunes", "umbral",
                       "score_umbral", "umb", "reg", "par", "pred", "conf", "tipo_flujo", "episodio"] if c in S.columns]
S[guardar].to_parquet(SALIDA + "/semilla_flujos.parquet", index=False)
print("  " + ruta_json + " (" + str(len(alertas)) + " episodios, " + format(os.path.getsize(ruta_json) / 1e6, ".2f") + " MB)")
print("  " + SALIDA + "/semilla_flujos.parquet (" + str(len(S)) + " flujos marcados)")
print("")
if DIFERENCIAS:
    print("ATENCION: diferencias con lo documentado en: " + "; ".join(DIFERENCIAS))
else:
    print("Verificacion: todo coincide con lo documentado.")
print("Tiempo total: " + format(time.time() - T0, ".0f") + " s")
