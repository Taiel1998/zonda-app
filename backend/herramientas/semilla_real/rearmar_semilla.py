import hashlib, json, os, sys, time
from datetime import datetime, timezone

import numpy as np
import pandas as pd
import pyarrow.parquet as pq

# -----------------------------------------------------------------------------------------------
# Rearma los episodios de la semilla real a partir de semilla_flujos.parquet, sin volver a correr nada
# en zeek-lab. Usa el mismo codigo de episodios que exportar_semilla_real.py (copiado sin cambios) y
# solo permite cambiar el corte entre flujos (gap). Con el gap de 300 s reproduce exactamente la
# semilla exportada.
# Uso: python rearmar_semilla.py <alertas_semilla.json exportado> <semilla_flujos.parquet> <gap en s> <salida.json>
# -----------------------------------------------------------------------------------------------

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



base = json.load(open(sys.argv[1], encoding="utf-8"))
GAP = int(sys.argv[3])
S = pd.read_parquet(sys.argv[2])
S = S.drop(columns=["episodio"]).sort_values(["dia", "id.orig_h", "id.resp_h", "ts", "uid"], kind="mergesort").reset_index(drop=True)
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
semilla = dict(base)
semilla["generadoEn"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
semilla["parametros"] = dict(base["parametros"], gapSegundos=GAP)
if GAP != base["parametros"]["gapSegundos"]:
    semilla["nota"] = base["nota"] + (" Episodios rearmados con rearmar_semilla.py a partir de los mismos flujos, con " +
                                      str(GAP) + " s sin flujos nuevos como corte.")
semilla["resumen"] = {
    "episodios": int(len(A)),
    "flujos": int(A["cantidadFlujos"].sum()),
    "porTipo": {str(k): int(v) for k, v in A["tipo"].value_counts().items()},
    "porSeveridadRecomendada": {s: int((A["severidad"] == s).sum()) for s in ["alta", "media", "baja"]},
}
ips = sorted(set(A["origenIp"]) | set(A["destinoIp"]))
semilla["catalogo"] = dict(base["catalogo"], equipos={ip: EQUIPOS.get(ip) for ip in ips}, servicios=sorted(set(A["servicio"])))
semilla["alertas"] = alertas
with open(sys.argv[4], "w", encoding="utf-8") as f:
    json.dump(semilla, f, ensure_ascii=False, indent=1)
print("gap " + str(GAP) + " s: " + str(len(A)) + " episodios | " + ", ".join(k + " " + str(int(v)) for k, v in A["tipo"].value_counts().items()) +
      " | severidad: " + ", ".join(s + " " + str(int((A["severidad"] == s).sum())) for s in ["alta", "media", "baja"]))
