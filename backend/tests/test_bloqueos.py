"""Bloqueos del origen de una alerta en el firewall (decisión 51): decide una persona y el firewall pregunta."""

import asyncio
import sqlite3
from datetime import timedelta

import pytest

from app.auth import ahora, iso
from app.db import Base
from tests.conftest import cabecera, todas_las_alertas
from tests.test_perfiles_y_registro import mi_id, sumar
from tests.test_revision_y_severidades import primera_alta
from tests.test_telegram import vincular

TOKEN_FIREWALL = "token-del-firewall-de-prueba"
FIREWALL = {"X-Token-Firewall": TOKEN_FIREWALL}
MOTIVO = "Fuerza bruta de pc-rrhh a srv-sueldos."


@pytest.fixture
def config(config):
    """La red de prueba con un firewall conectado: sin su token, los bloqueos están apagados."""
    config.firewall_token = TOKEN_FIREWALL
    return config


def bloquear(cliente, auth, alerta_id: str, accion: str = "1", duracion: int | None = 3600, motivo: str = MOTIVO):
    return cliente.post(
        "/bloqueos",
        json={"accionId": f"bloqueo-{accion}", "alertaId": alerta_id, "duracion": duracion, "motivo": motivo},
        headers=auth,
    )


def lo_que_bloquea_el_firewall(cliente) -> dict[str, int | None]:
    respuesta = cliente.get("/firewall/bloqueos", headers=FIREWALL)
    assert respuesta.status_code == 200, respuesta.text
    return {b["ip"]: b["segundos"] for b in respuesta.json()["bloqueos"]}


def bloqueos(cliente, auth) -> list[dict]:
    return cliente.get("/bloqueos", headers=auth).json()["bloqueos"]


def de_origenes_distintos(cliente, auth, cuantas: int) -> list[dict]:
    """Alertas con orígenes distintos: un equipo no se bloquea dos veces a la vez."""
    elegidas: dict[str, dict] = {}
    for alerta in todas_las_alertas(cliente, auth):
        elegidas.setdefault(alerta["origenIp"], alerta)
    assert len(elegidas) >= cuantas
    return list(elegidas.values())[:cuantas]


def con_bloquea(cliente, auth, persona_id: str, puede: bool = True):
    """Ajustes → Personas → «Bloqueos»: «Bloquea» (en el acto) o «Pide aprobación»."""
    return cliente.put(f"/personas/{persona_id}/bloqueos", json={"puedeBloquear": puede}, headers=auth)


def persona(cliente, auth, persona_id: str) -> dict:
    return next(p for p in cliente.get("/personas", headers=auth).json() if p["id"] == persona_id)


def parear_tablero(cliente, auth, tipo: str | None = "navegador") -> dict:
    """Un navegador pareado con el tablero web, como administrador."""
    codigo = cliente.post("/personas/codigo", json={"nombre": "Taiel", "perfil": "administrador"}, headers=auth).json()["codigo"]
    entrada = {"codigo": codigo, "nombreDispositivo": "Tablero web · Chrome en Windows"}
    if tipo is not None:
        entrada["tipo"] = tipo
    pareo = cliente.post("/pareo", json=entrada)
    assert pareo.status_code == 200, pareo.text
    return cabecera(pareo.json()["token"])


def test_personas_dice_que_el_tablero_es_un_navegador(cliente, auth):
    parear_tablero(cliente, auth)

    tipos = {p["telefono"]: p["tipo"] for p in cliente.get("/personas", headers=auth).json()}

    assert tipos == {"Teléfono de prueba": "telefono", "Tablero web · Chrome en Windows": "navegador"}


def test_sin_firewall_conectado_los_bloqueos_estan_apagados(cliente, auth):
    cliente.app.state.config.firewall_token = ""
    alerta = primera_alta(cliente, auth)

    assert bloquear(cliente, auth, alerta["id"]).status_code == 503
    lista = cliente.get("/bloqueos", headers=auth).json()
    assert (lista["firewall"]["habilitado"], lista["bloqueos"]) == (False, [])
    assert cliente.get("/firewall/bloqueos", headers=FIREWALL).status_code == 503


def test_salud_con_token_dice_como_esta_el_firewall_para_la_pieza_de_estado(cliente, auth):
    def firewall():
        return cliente.get("/salud", headers=auth).json()["firewall"]

    assert cliente.get("/salud").json()["firewall"] is None
    assert (firewall()["habilitado"], firewall()["conectado"], firewall()["ultimaConsulta"]) == (True, False, None)
    lo_que_bloquea_el_firewall(cliente)
    assert (firewall()["conectado"], firewall()["ultimaConsulta"] is not None) == (True, True)
    cliente.app.state.config.firewall_token = ""
    assert firewall()["habilitado"] is False


def test_un_administrador_bloquea_desde_el_telefono_y_el_firewall_lo_aplica(cliente, auth):
    alerta = primera_alta(cliente, auth)

    respuesta = bloquear(cliente, auth, alerta["id"])

    assert respuesta.status_code == 201, respuesta.text
    bloqueo = respuesta.json()
    assert (bloqueo["ip"], bloqueo["estado"], bloqueo["duracion"], bloqueo["aprobadoPor"]) == (alerta["origenIp"], "aprobado", 3600, "Teléfono de prueba")
    assert bloqueo["venceEn"] > iso(ahora() + timedelta(minutes=59))
    # El firewall lo ve con lo que le queda y cuenta que lo aplicó.
    assert 3590 <= lo_que_bloquea_el_firewall(cliente)[alerta["origenIp"]] <= 3600
    assert cliente.post("/firewall/informe", json={"aplicados": [bloqueo["id"], bloqueo["id"]]}, headers=FIREWALL).json() == {"aplicados": 1, "rechazados": 0}
    lista = cliente.get("/bloqueos", headers=auth).json()
    assert (lista["bloqueos"][0]["estado"], lista["firewall"]["conectado"]) == ("aplicado", True)
    assert lista["bloqueos"][0]["aplicadoEn"] is not None
    # Queda en el registro, pero solo para quien lo pide: la app 1.0.8 no conoce ese tipo.
    assert all(c["tipo"] != "bloqueo" for c in cliente.get("/registro", headers=auth).json())
    cambio = cliente.get("/registro", params={"bloqueos": "true"}, headers=auth).json()[0]
    assert (cambio["tipo"], cambio["objeto"], cambio["despues"], cambio["motivo"], cambio["autor"]) == ("bloqueo", alerta["origenIp"], "3600", MOTIVO, "Teléfono de prueba")


def test_el_motivo_es_obligatorio_y_la_duracion_una_de_las_permitidas(cliente, auth):
    alerta = primera_alta(cliente, auth)

    assert bloquear(cliente, auth, alerta["id"], motivo="   ").status_code == 422
    # Las duraciones son 1 o 24 horas (o hasta desbloquear): ni 10 ni 15 minutos, ni 8 horas.
    for duracion in (600, 900, 28800):
        assert bloquear(cliente, auth, alerta["id"], duracion=duracion).status_code == 422
    assert bloquear(cliente, auth, "no-existe").status_code == 404
    assert bloqueos(cliente, auth) == []


def test_hasta_desbloquear_no_pide_la_alerta_revisada(cliente, auth):
    alerta = primera_alta(cliente, auth)
    hasta = bloquear(cliente, auth, alerta["id"], duracion=None).json()

    assert (hasta["duracion"], hasta["venceEn"], hasta["estado"]) == (None, None, "aprobado")
    assert lo_que_bloquea_el_firewall(cliente) == {alerta["origenIp"]: None}


def test_cambiar_la_duracion_a_hasta_desbloquear_y_a_24_horas(cliente, auth):
    alerta = primera_alta(cliente, auth)
    bloqueo = bloquear(cliente, auth, alerta["id"]).json()
    permanente = {"duracion": None, "motivo": "pc-rrhh queda aislada hasta formatearla."}

    cambiado = cliente.put(f"/bloqueos/{bloqueo['id']}/duracion", json=permanente, headers=auth).json()

    assert (cambiado["duracion"], cambiado["venceEn"], cambiado["estado"]) == (None, None, "aprobado")
    assert lo_que_bloquea_el_firewall(cliente) == {alerta["origenIp"]: None}
    cambio = cliente.get("/registro", params={"bloqueos": "true"}, headers=auth).json()[0]
    assert (cambio["tipo"], cambio["antes"], cambio["despues"], cambio["motivo"]) == ("bloqueo", "3600", "hasta_desbloquear", permanente["motivo"])
    un_dia = cliente.put(f"/bloqueos/{bloqueo['id']}/duracion", json={"duracion": 86400, "motivo": "Hasta mañana."}, headers=auth).json()
    assert un_dia["duracion"] == 86400
    assert 86390 <= lo_que_bloquea_el_firewall(cliente)[alerta["origenIp"]] <= 86400


def test_desbloquear_pide_el_motivo_y_el_firewall_lo_saca(cliente, auth):
    alerta = primera_alta(cliente, auth)
    bloqueo = bloquear(cliente, auth, alerta["id"]).json()
    assert cliente.post(f"/bloqueos/{bloqueo['id']}/desbloqueo", json={"motivo": " "}, headers=auth).status_code == 422

    hecho = cliente.post(f"/bloqueos/{bloqueo['id']}/desbloqueo", json={"motivo": "Era el backup de la noche."}, headers=auth).json()

    assert (hecho["estado"], hecho["terminadoPor"], hecho["detalle"]) == ("desbloqueado", "Teléfono de prueba", "Era el backup de la noche.")
    assert lo_que_bloquea_el_firewall(cliente) == {}
    # Ya terminado, desbloquear otra vez no cambia nada; el equipo se puede volver a bloquear.
    otra_vez = cliente.post(f"/bloqueos/{bloqueo['id']}/desbloqueo", json={"motivo": "Otra vez"}, headers=auth).json()
    assert (otra_vez["terminadoEn"], otra_vez["detalle"]) == (hecho["terminadoEn"], hecho["detalle"])
    cambio = cliente.get("/registro", params={"bloqueos": "true"}, headers=auth).json()[0]
    assert (cambio["tipo"], cambio["antes"], cambio["motivo"]) == ("desbloqueo", "3600", "Era el backup de la noche.")
    assert bloquear(cliente, auth, alerta["id"], accion="2").status_code == 201


def test_un_analista_pide_y_lo_aprueba_un_administrador_desde_el_telefono(cliente, auth):
    _, laura = sumar(cliente, auth)
    alerta = primera_alta(cliente, auth)

    pedido = bloquear(cliente, laura, alerta["id"]).json()

    assert (pedido["estado"], pedido["pedidoPor"], pedido["aprobadoPor"], pedido["venceEn"]) == ("pedido", "Laura", None, None)
    assert lo_que_bloquea_el_firewall(cliente) == {}
    # Un pedido no queda en el registro: no cambió nada en la red todavía.
    assert cliente.get("/registro", params={"bloqueos": "true"}, headers=auth).json()[0]["tipo"] == "persona_nueva"
    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=laura).status_code == 403
    aprobado = cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=auth).json()
    assert (aprobado["estado"], aprobado["aprobadoPor"]) == ("aprobado", "Teléfono de prueba")
    assert alerta["origenIp"] in lo_que_bloquea_el_firewall(cliente)
    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=auth).status_code == 409
    # Con «Pide aprobación», tampoco desbloquea ni cambia la duración.
    assert cliente.post(f"/bloqueos/{pedido['id']}/desbloqueo", json={"motivo": "Ya está"}, headers=laura).status_code == 403
    assert cliente.put(f"/bloqueos/{pedido['id']}/duracion", json={"duracion": None, "motivo": "Más largo"}, headers=laura).status_code == 403


def test_un_pedido_se_puede_rechazar_con_motivo(cliente, auth):
    _, laura = sumar(cliente, auth)
    alerta = primera_alta(cliente, auth)
    pedido = bloquear(cliente, laura, alerta["id"]).json()

    rechazado = cliente.post(f"/bloqueos/{pedido['id']}/rechazo", json={"motivo": "Es la tarea programada de las 2."}, headers=auth).json()

    assert (rechazado["estado"], rechazado["terminadoPor"], rechazado["detalle"]) == ("rechazado", "Teléfono de prueba", "Es la tarea programada de las 2.")
    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=auth).status_code == 409
    assert cliente.post(f"/bloqueos/{pedido['id']}/rechazo", json={"motivo": "Otra vez"}, headers=auth).status_code == 409


def test_un_administrador_pasa_a_bloquea_al_analista_de_guardia(cliente, auth):
    laura_id, laura = sumar(cliente, auth)
    assert persona(cliente, auth, laura_id)["puedeBloquear"] is False
    # Lo elige un administrador.
    assert con_bloquea(cliente, laura, laura_id).status_code == 403

    cambiada = con_bloquea(cliente, auth, laura_id)

    assert cambiada.status_code == 200, cambiada.text
    assert cambiada.json()["puedeBloquear"] is True
    assert persona(cliente, auth, laura_id)["puedeBloquear"] is True
    # Repetirlo no suma otra línea; volver a «Pide aprobación», sí.
    con_bloquea(cliente, auth, laura_id)
    con_bloquea(cliente, auth, laura_id, puede=False)
    cambios = [c for c in cliente.get("/registro", params={"bloqueos": "true"}, headers=auth).json() if c["tipo"] == "permiso_bloqueo"]
    assert [(c["nombre"], c["antes"], c["despues"], c["autor"]) for c in cambios] == [
        ("Laura", "bloquea", "pide_aprobacion", "Teléfono de prueba"),
        ("Laura", "pide_aprobacion", "bloquea", "Teléfono de prueba"),
    ]
    # La app 1.0.8 no conoce ese tipo: solo lo ve quien pide los bloqueos.
    assert all(c["tipo"] != "permiso_bloqueo" for c in cliente.get("/registro", headers=auth).json())
    # Un administrador siempre bloquea.
    yo = mi_id(cliente, auth)
    assert con_bloquea(cliente, auth, yo, puede=False).status_code == 409
    assert persona(cliente, auth, yo)["puedeBloquear"] is True


def test_el_analista_con_bloquea_lo_hace_desde_su_telefono_y_aprueba_pedidos(cliente, auth):
    laura_id, laura = sumar(cliente, auth)
    _, diego = sumar(cliente, auth, nombre="Diego", telefono="Teléfono de Diego")
    con_bloquea(cliente, auth, laura_id)
    primera, segunda, tercera = de_origenes_distintos(cliente, auth, 3)

    bloqueo = bloquear(cliente, laura, primera["id"], duracion=86400).json()

    assert (bloqueo["estado"], bloqueo["aprobadoPor"], bloqueo["duracion"]) == ("aprobado", "Laura", 86400)
    assert primera["origenIp"] in lo_que_bloquea_el_firewall(cliente)
    # Diego pide aprobación: lo pide, y lo aprueba Laura, que bloquea.
    pedido = bloquear(cliente, diego, segunda["id"], accion="2").json()
    assert pedido["estado"] == "pedido"
    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=diego).status_code == 403
    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=laura).json()["aprobadoPor"] == "Laura"
    otro = bloquear(cliente, diego, tercera["id"], accion="3").json()
    rechazado = cliente.post(f"/bloqueos/{otro['id']}/rechazo", json={"motivo": "Es el backup."}, headers=laura).json()
    assert (rechazado["estado"], rechazado["terminadoPor"]) == ("rechazado", "Laura")
    # Laura cambia la duración y desbloquea; Diego, no.
    hasta = {"duracion": None, "motivo": "pc-rrhh queda aislada hasta formatearla."}
    assert cliente.put(f"/bloqueos/{bloqueo['id']}/duracion", json=hasta, headers=diego).status_code == 403
    assert cliente.put(f"/bloqueos/{bloqueo['id']}/duracion", json=hasta, headers=laura).json()["duracion"] is None
    assert cliente.post(f"/bloqueos/{bloqueo['id']}/desbloqueo", json={"motivo": "Ya está"}, headers=diego).status_code == 403
    assert cliente.post(f"/bloqueos/{bloqueo['id']}/desbloqueo", json={"motivo": "Formateada."}, headers=laura).json()["terminadoPor"] == "Laura"


def test_al_cambiarle_el_perfil_vuelve_a_pedir_aprobacion(cliente, auth):
    laura_id, laura = sumar(cliente, auth)
    con_bloquea(cliente, auth, laura_id)

    cliente.put(f"/personas/{laura_id}/perfil", json={"perfil": "administrador"}, headers=auth)
    assert persona(cliente, auth, laura_id)["puedeBloquear"] is True
    cliente.put(f"/personas/{laura_id}/perfil", json={"perfil": "analista"}, headers=auth)

    assert persona(cliente, auth, laura_id)["puedeBloquear"] is False
    assert bloquear(cliente, laura, primera_alta(cliente, auth)["id"]).json()["estado"] == "pedido"


def test_desde_el_tablero_se_pide_y_se_confirma_en_el_celular(cliente, auth):
    navegador = parear_tablero(cliente, auth)
    alerta = primera_alta(cliente, auth)

    pedido = bloquear(cliente, navegador, alerta["id"]).json()

    assert pedido["estado"] == "pedido"
    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=navegador).status_code == 403
    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=auth).json()["estado"] == "aprobado"
    # Sin la llave de esa computadora, el tablero tampoco cambia la duración ni desbloquea (test_llaves.py).
    hasta = {"duracion": None, "motivo": "Queda hasta formatearla."}
    assert cliente.put(f"/bloqueos/{pedido['id']}/duracion", json=hasta, headers=navegador).status_code == 403
    assert cliente.post(f"/bloqueos/{pedido['id']}/desbloqueo", json={"motivo": "Falsa alarma."}, headers=navegador).status_code == 403
    hecho = cliente.post(f"/bloqueos/{pedido['id']}/desbloqueo", json={"motivo": "Falsa alarma."}, headers=auth).json()
    assert (hecho["estado"], hecho["terminadoPor"]) == ("desbloqueado", "Teléfono de prueba")


def test_un_tablero_que_no_dice_que_es_un_navegador_se_reconoce_por_su_nombre(cliente, auth):
    navegador = parear_tablero(cliente, auth, tipo=None)

    assert bloquear(cliente, navegador, primera_alta(cliente, auth)["id"]).json()["estado"] == "pedido"


def test_los_tableros_pareados_antes_quedan_como_navegadores(tmp_path):
    ruta = tmp_path / "anterior.db"
    con = sqlite3.connect(ruta)
    con.executescript(
        """
        CREATE TABLE dispositivos (
            id TEXT PRIMARY KEY, nombre TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE, creado_en TEXT NOT NULL,
            vence_en TEXT NOT NULL, revocado_en TEXT
        );
        INSERT INTO dispositivos (id, nombre, token_hash, creado_en, vence_en) VALUES ('d-moto', 'moto g(9) plus', 'h1', '2026-10-14T21:00:00+00:00', '2027-01-12T21:00:00+00:00');
        INSERT INTO dispositivos (id, nombre, token_hash, creado_en, vence_en) VALUES ('d-web', 'Tablero web · Chrome en Windows', 'h2', '2026-10-30T20:00:00+00:00', '2027-01-28T20:00:00+00:00');
        """
    )
    con.commit()
    con.close()

    base = Base(ruta)
    base.iniciar()

    filas = {f["id"]: (f["tipo"], f["puede_bloquear"]) for f in base.conexion().execute("SELECT id, tipo, puede_bloquear FROM dispositivos")}
    # Además, nadie bloquea en el acto sin que un administrador lo elija.
    assert filas == {"d-moto": ("telefono", 0), "d-web": ("navegador", 0)}


def test_el_firewall_rechaza_lo_que_esta_en_su_lista_y_el_servidor_ya_no_lo_acepta(cliente, auth):
    alerta = primera_alta(cliente, auth)
    bloqueo = bloquear(cliente, auth, alerta["id"]).json()
    informe = {"rechazados": [{"id": bloqueo["id"], "motivo": "nunca_bloquear"}], "nuncaBloquear": ["192.168.20.0/24", alerta["origenIp"]]}

    assert cliente.post("/firewall/informe", json=informe, headers=FIREWALL).json() == {"aplicados": 0, "rechazados": 1}

    lista = cliente.get("/bloqueos", headers=auth).json()
    rechazado = lista["bloqueos"][0]
    assert (rechazado["estado"], rechazado["terminadoPor"], rechazado["detalle"]) == ("rechazado", "Firewall", "nunca_bloquear")
    assert lista["firewall"]["nuncaBloquear"] == ["192.168.20.0/24", f"{alerta['origenIp']}/32"]
    assert bloquear(cliente, auth, alerta["id"], accion="2").status_code == 422
    assert cliente.post("/firewall/informe", json={"nuncaBloquear": ["no es una red"]}, headers=FIREWALL).status_code == 422


def test_el_token_del_firewall_solo_sirve_para_sus_dos_rutas(cliente, auth):
    assert cliente.get("/firewall/bloqueos").status_code == 401
    assert cliente.get("/firewall/bloqueos", headers={"X-Token-Firewall": "otro"}).status_code == 401
    # Ni el token de un teléfono sirve para el firewall, ni el del firewall para lo de la gente.
    assert cliente.get("/firewall/bloqueos", headers=auth).status_code == 401
    assert cliente.get("/bloqueos", headers=cabecera(TOKEN_FIREWALL)).status_code == 401
    assert cliente.post("/firewall/informe", json={}, headers={"X-Token-Firewall": "otro"}).status_code == 401


def test_vencen_solos_y_los_pedidos_viejos_dejan_de_valer(cliente, auth):
    _, laura = sumar(cliente, auth)
    primera, segunda = de_origenes_distintos(cliente, auth, 2)
    bloqueo = bloquear(cliente, auth, primera["id"]).json()
    pedido = bloquear(cliente, laura, segunda["id"]).json()
    base = cliente.app.state.base
    with base.transaccion() as con:
        con.execute("UPDATE bloqueos SET vence_en = ? WHERE id = ?", (iso(ahora() - timedelta(seconds=1)), bloqueo["id"]))
        con.execute("UPDATE bloqueos SET pedido_en = ? WHERE id = ?", (iso(ahora() - timedelta(hours=2)), pedido["id"]))

    assert lo_que_bloquea_el_firewall(cliente) == {}

    estados = {b["id"]: b["estado"] for b in bloqueos(cliente, auth)}
    assert estados == {bloqueo["id"]: "vencido", pedido["id"]: "vencido"}
    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=auth).status_code == 409


def test_un_equipo_no_se_bloquea_dos_veces_y_hay_topes(cliente, auth):
    primera, segunda, tercera = de_origenes_distintos(cliente, auth, 3)
    bloqueo = bloquear(cliente, auth, primera["id"]).json()

    assert bloquear(cliente, auth, primera["id"], accion="2").status_code == 409
    # La misma acción reenviada desde la cola del teléfono devuelve el mismo bloqueo.
    assert bloquear(cliente, auth, primera["id"]).json()["id"] == bloqueo["id"]
    cliente.app.state.config.max_bloqueos = 1
    assert bloquear(cliente, auth, segunda["id"], accion="3").status_code == 409
    cliente.app.state.config.max_bloqueos = 20
    cliente.app.state.config.bloqueos_por_hora = 2
    assert bloquear(cliente, auth, segunda["id"], accion="4").status_code == 201
    assert bloquear(cliente, auth, tercera["id"], accion="5").status_code == 429


def test_lo_que_no_corresponde_a_cada_estado(cliente, auth):
    _, laura = sumar(cliente, auth)
    alerta = primera_alta(cliente, auth)
    pedido = bloquear(cliente, laura, alerta["id"]).json()

    assert cliente.post("/bloqueos/b-no-existe/desbloqueo", json={"motivo": "Ya está"}, headers=auth).status_code == 404
    assert cliente.post(f"/bloqueos/{pedido['id']}/desbloqueo", json={"motivo": "Ya está"}, headers=auth).status_code == 409
    assert cliente.put(f"/bloqueos/{pedido['id']}/duracion", json={"duracion": 86400, "motivo": "Más largo"}, headers=auth).status_code == 409
    # Si el firewall suma ese equipo a su lista mientras el pedido espera, ya no se aprueba.
    cliente.post("/firewall/informe", json={"nuncaBloquear": [alerta["origenIp"]]}, headers=FIREWALL)
    assert cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=auth).status_code == 422


@pytest.mark.parametrize("origen", ["255.255.255.255", "224.0.0.251", "0.0.0.0", "fe80::1", "desconocido"])
def test_lo_que_no_es_un_equipo_no_se_bloquea(cliente, auth, origen):
    alerta = primera_alta(cliente, auth)
    with cliente.app.state.base.transaccion() as con:
        con.execute("UPDATE alertas SET origen_ip = ? WHERE id = ?", (origen, alerta["id"]))

    assert bloquear(cliente, auth, alerta["id"]).status_code == 422


def test_los_administradores_se_enteran_de_cada_bloqueo_por_telegram_sin_la_ip(cliente, auth, telegram, bot):
    vincular(cliente, auth, telegram, bot, chat_id=555)
    _, laura = sumar(cliente, auth)
    # Laura es analista y pide aprobación: no le llegan los avisos de los bloqueos.
    vincular(cliente, laura, telegram, bot, chat_id=777)
    alerta = primera_alta(cliente, auth)
    servicio = alerta["servicio"].upper()

    pedido = bloquear(cliente, laura, alerta["id"]).json()
    assert asyncio.run(bot.avisar_bloqueos()) == 1
    aviso = telegram.enviados[-1]
    assert aviso["chat_id"] == "555"
    assert aviso["text"].startswith(f"<b>Laura pidió bloquear el origen de una alerta ALTA · {servicio} · ")
    assert aviso["reply_markup"]["inline_keyboard"][0][0]["url"].startswith(f"https://taiel1998.github.io/a/{alerta['id']}?red=red-prueba")

    cliente.post(f"/bloqueos/{pedido['id']}/aprobacion", headers=auth)
    assert asyncio.run(bot.avisar_bloqueos()) == 1
    assert telegram.enviados[-1]["text"].startswith(f"<b>Teléfono de prueba bloqueó el origen de una alerta ALTA · {servicio} · ")
    assert telegram.enviados[-1]["text"].endswith("</b>\nPor 1 h. Tocá para ver el detalle en Zonda.")

    cliente.post(f"/bloqueos/{pedido['id']}/desbloqueo", json={"motivo": "Falsa alarma."}, headers=auth)
    assert asyncio.run(bot.avisar_bloqueos()) == 1
    assert telegram.enviados[-1]["text"].startswith("<b>Teléfono de prueba desbloqueó el origen de una alerta ALTA")
    # Nunca dos veces el mismo aviso, y nunca la IP ni el nombre del equipo.
    assert asyncio.run(bot.avisar_bloqueos()) == 0
    avisos = [m["text"] for m in telegram.enviados if "origen de una alerta" in m["text"]]
    assert len(avisos) == 3
    assert not any(alerta["origenIp"] in texto for texto in avisos)
    if alerta["origenNombre"]:
        assert not any(alerta["origenNombre"] in texto for texto in avisos)


def test_el_analista_con_bloquea_tambien_se_entera_por_telegram(cliente, auth, telegram, bot):
    vincular(cliente, auth, telegram, bot, chat_id=555)
    laura_id, laura = sumar(cliente, auth)
    vincular(cliente, laura, telegram, bot, chat_id=777)
    con_bloquea(cliente, auth, laura_id)
    _, diego = sumar(cliente, auth, nombre="Diego", telefono="Teléfono de Diego")

    bloquear(cliente, diego, primera_alta(cliente, auth)["id"])

    # Le llega a los dos que pueden aprobarlo.
    assert asyncio.run(bot.avisar_bloqueos()) == 2
    assert {m["chat_id"] for m in telegram.enviados[-2:]} == {"555", "777"}
    assert telegram.enviados[-1]["text"].endswith("</b>\nLo aprueba desde Zonda, en su teléfono, alguien que puede bloquear.")


def test_el_firewall_que_no_aplica_un_bloqueo_se_avisa(cliente, auth, telegram, bot):
    vincular(cliente, auth, telegram, bot, chat_id=555)
    alerta = primera_alta(cliente, auth)
    bloqueo = bloquear(cliente, auth, alerta["id"], duracion=86400).json()
    asyncio.run(bot.avisar_bloqueos())
    assert telegram.enviados[-1]["text"].endswith("</b>\nPor 24 h. Tocá para ver el detalle en Zonda.")

    cliente.post("/firewall/informe", json={"rechazados": [{"id": bloqueo["id"], "motivo": "tope"}]}, headers=FIREWALL)

    assert asyncio.run(bot.avisar_bloqueos()) == 1
    assert telegram.enviados[-1]["text"].startswith("<b>El firewall no aplicó el bloqueo de una alerta ALTA")
    assert telegram.enviados[-1]["text"].endswith("</b>\nTocá para ver por qué en Zonda.")
