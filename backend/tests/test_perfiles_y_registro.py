"""Perfiles, personas y registro de cambios (decisiones 39 y 40; correcciones 2 y 3 de Peña)."""

import sqlite3
from datetime import timedelta

from app.auth import ahora, iso
from app.db import Base
from tests.conftest import cabecera, parear
from tests.test_revision_y_severidades import primera_alta, revision

MOTIVO = "El backup de cada noche usa SSH y dispara alertas de fuerza bruta."


def sumar(cliente, auth, nombre: str = "Laura", perfil: str = "analista", telefono: str = "Teléfono de Laura") -> tuple[str, dict]:
    """Un administrador genera el código en Ajustes → Personas y la persona lo usa en su teléfono."""
    codigo = cliente.post("/personas/codigo", json={"nombre": nombre, "perfil": perfil}, headers=auth)
    assert codigo.status_code == 200, codigo.text
    pareo = cliente.post("/pareo", json={"codigo": codigo.json()["codigo"], "nombreDispositivo": telefono})
    assert pareo.status_code == 200, pareo.text
    return pareo.json()["dispositivoId"], cabecera(pareo.json()["token"])


def mi_id(cliente, auth) -> str:
    return next(p["id"] for p in cliente.get("/personas", headers=auth).json() if p["vos"])


def test_los_telefonos_pareados_antes_quedan_como_administradores(tmp_path):
    ruta = tmp_path / "anterior.db"
    con = sqlite3.connect(ruta)
    # Las tablas como eran en la 1.0.4.
    con.executescript(
        """
        CREATE TABLE dispositivos (
            id TEXT PRIMARY KEY, nombre TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE, creado_en TEXT NOT NULL,
            vence_en TEXT NOT NULL, revocado_en TEXT, telegram_chat_id TEXT, telegram_vinculado_en TEXT,
            aviso_alta TEXT NOT NULL DEFAULT 'sonido', aviso_media TEXT NOT NULL DEFAULT 'vibracion',
            aviso_baja TEXT NOT NULL DEFAULT 'sin_aviso'
        );
        CREATE TABLE codigos_pareo (codigo TEXT PRIMARY KEY, creado_en TEXT NOT NULL, vence_en TEXT NOT NULL, usado_en TEXT, dispositivo_id TEXT);
        CREATE TABLE catalogo_equipos (ip TEXT PRIMARY KEY, nombre TEXT NOT NULL);
        INSERT INTO dispositivos (id, nombre, token_hash, creado_en, vence_en) VALUES ('d-moto', 'moto g(9) plus', 'h', '2026-10-14T21:00:00+00:00', '2027-01-12T21:00:00+00:00');
        INSERT INTO catalogo_equipos (ip, nombre) VALUES ('192.168.10.50', 'srv-sueldos');
        """
    )
    con.commit()
    con.close()

    base = Base(ruta)
    base.iniciar()

    fila = base.conexion().execute("SELECT perfil, persona, ultimo_uso FROM dispositivos WHERE id = 'd-moto'").fetchone()
    assert (fila["perfil"], fila["persona"], fila["ultimo_uso"]) == ("administrador", None, "2026-10-14T21:00:00+00:00")
    assert base.conexion().execute("SELECT importancia FROM catalogo_equipos").fetchone()[0] == "normal"


def test_un_administrador_suma_a_laura_como_analista_con_un_codigo_de_un_uso(cliente, auth):
    codigo = cliente.post("/personas/codigo", json={"nombre": "Laura", "perfil": "analista"}, headers=auth).json()
    assert (codigo["nombre"], codigo["perfil"]) == ("Laura", "analista")
    # El QR lleva lo mismo que el de zonda pair: la dirección, el código y cuándo vence.
    assert codigo["contenido"] == f"zonda:https://prueba.duckdns.org#{codigo['codigo']};vence={codigo['vence']}"
    # Y viene dibujado en SVG para el tablero web, que lo muestra sin librerías (decisión 50).
    assert codigo["qrSvg"].startswith("<svg") and "<path" in codigo["qrSvg"]

    pareo = cliente.post("/pareo", json={"codigo": codigo["codigo"], "nombreDispositivo": "Teléfono de Laura"}).json()
    assert (pareo["perfil"], pareo["persona"]) == ("analista", "Laura")
    assert cliente.post("/pareo", json={"codigo": codigo["codigo"], "nombreDispositivo": "Otro"}).status_code == 410

    personas = cliente.get("/personas", headers=auth).json()
    assert [(p["nombre"], p["telefono"], p["perfil"], p["vos"]) for p in personas] == [
        (None, "Teléfono de prueba", "administrador", True),
        ("Laura", "Teléfono de Laura", "analista", False),
    ]
    assert all(p["ultimoUso"] for p in personas)
    # Lo ve también la analista, que es "vos" en su teléfono.
    laura = cabecera(pareo["token"])
    assert [p["vos"] for p in cliente.get("/personas", headers=laura).json()] == [False, True]

    cambio = cliente.get("/registro", headers=auth).json()[0]
    assert (cambio["tipo"], cambio["autor"], cambio["nombre"], cambio["despues"], cambio["motivo"]) == (
        "persona_nueva", "Teléfono de prueba", "Laura", "analista", None
    )


def test_un_analista_recibe_403_en_lo_que_cambia_la_configuracion(cliente, auth):
    yo = mi_id(cliente, auth)
    _, laura = sumar(cliente, auth)

    assert cliente.put("/severidades", json={"severidades": {"escaneo": "alta"}, "motivo": MOTIVO}, headers=laura).status_code == 403
    assert cliente.put("/equipos/192.168.10.50", json={"importancia": "critico", "motivo": MOTIVO}, headers=laura).status_code == 403
    assert cliente.post("/personas/codigo", json={"nombre": "Meli", "perfil": "analista"}, headers=laura).status_code == 403
    assert cliente.put(f"/personas/{yo}/perfil", json={"perfil": "analista"}, headers=laura).status_code == 403
    assert cliente.delete(f"/personas/{yo}", headers=laura).status_code == 403
    # Ver, puede ver todo.
    for ruta in ("/severidades", "/personas", "/equipos", "/registro"):
        assert cliente.get(ruta, headers=laura).status_code == 200
    assert cliente.get("/severidades", headers=auth).json()["escaneo"] == "media"


def test_siempre_queda_al_menos_un_administrador(cliente, auth):
    yo = mi_id(cliente, auth)
    laura_id, laura = sumar(cliente, auth)

    assert cliente.put(f"/personas/{yo}/perfil", json={"perfil": "analista"}, headers=auth).status_code == 409
    assert cliente.delete(f"/personas/{yo}", headers=auth).status_code == 409
    assert cliente.delete("/dispositivos/actual", headers=auth).status_code == 409

    # Con Laura de administradora, ya se puede.
    assert cliente.put(f"/personas/{laura_id}/perfil", json={"perfil": "administrador"}, headers=auth).json()["perfil"] == "administrador"
    assert cliente.put(f"/personas/{yo}/perfil", json={"perfil": "analista"}, headers=auth).json()["perfil"] == "analista"
    assert cliente.delete(f"/personas/{yo}", headers=laura).status_code == 204
    assert cliente.get("/estado", headers=auth).status_code == 401
    # Y el único teléfono que queda se puede quitar: no deja a nadie sin administrador.
    assert cliente.delete("/dispositivos/actual", headers=laura).status_code == 204


def test_el_registro_anota_la_gente_sin_motivo(cliente, auth):
    laura_id, laura = sumar(cliente, auth)
    cliente.put(f"/personas/{laura_id}/perfil", json={"perfil": "administrador"}, headers=auth)
    cliente.put(f"/personas/{laura_id}/perfil", json={"perfil": "administrador"}, headers=auth)  # no cambia nada
    cliente.delete(f"/personas/{laura_id}", headers=auth)

    registro = [c for c in cliente.get("/registro", headers=auth).json() if c["nombre"] == "Laura"]
    assert [(c["tipo"], c["nombre"], c["antes"], c["despues"], c["motivo"]) for c in registro] == [
        ("persona_quitada", "Laura", "administrador", None, None),
        ("perfil", "Laura", "analista", "administrador", None),
        ("persona_nueva", "Laura", None, "analista", None),
    ]
    assert {c["autor"] for c in registro} == {"Teléfono de prueba"}
    assert cliente.get("/estado", headers=laura).status_code == 401


def test_cambiar_severidades_pide_el_motivo_y_queda_en_el_registro(cliente, auth):
    assert cliente.put("/severidades", json={"severidades": {"fuerza_bruta": "media"}}, headers=auth).status_code == 422
    assert cliente.put("/severidades", json={"severidades": {"fuerza_bruta": "media"}, "motivo": "   "}, headers=auth).status_code == 422
    # Una app anterior manda solo las severidades: tampoco se acepta.
    assert cliente.put("/severidades", json={"fuerza_bruta": "media"}, headers=auth).status_code == 422
    assert cliente.get("/severidades", headers=auth).json()["fuerza_bruta"] == "alta"

    respuesta = cliente.put("/severidades", json={"severidades": {"fuerza_bruta": "media", "escaneo": "media"}, "motivo": f"  {MOTIVO} "}, headers=auth)

    assert respuesta.status_code == 200
    # Un solo motivo para los cambios de ese Guardar; escaneo ya era media y no se anota.
    severidades = [c for c in cliente.get("/registro", headers=auth).json() if c["tipo"] == "severidad"]
    assert [(c["objeto"], c["antes"], c["despues"], c["motivo"], c["autor"]) for c in severidades] == [
        ("fuerza_bruta", "alta", "media", MOTIVO, "Teléfono de prueba"),
    ]


def test_zonda_pair_con_nombre_da_un_codigo_de_administrador_y_lo_anota_el_servidor(cliente, zonda, capsys):
    assert zonda(["pair", "--nombre", "Taiel"]) == 0
    salida = capsys.readouterr().out
    assert "Código de pareo de administrador para Taiel:" in salida
    codigo = salida.split("para Taiel:")[1].split()[0]

    pareo = cliente.post("/pareo", json={"codigo": codigo, "nombreDispositivo": "moto g(9) plus"}).json()

    assert (pareo["perfil"], pareo["persona"]) == ("administrador", "Taiel")
    cambio = cliente.get("/registro", headers=cabecera(pareo["token"])).json()[0]
    assert (cambio["tipo"], cambio["autor"], cambio["autorId"], cambio["nombre"], cambio["despues"]) == ("persona_nueva", "Servidor", None, "Taiel", "administrador")
    assert zonda(["dispositivos"]) == 0
    assert "Taiel" in capsys.readouterr().out


def test_las_revisiones_llevan_el_nombre_de_la_persona(cliente, auth):
    _, laura = sumar(cliente, auth)
    alerta = primera_alta(cliente, auth)

    revisada = cliente.post(f"/alertas/{alerta['id']}/revision", json=revision(), headers=laura).json()

    ultima = revisada["historial"][-1]
    assert (ultima["persona"], ultima["autor"]) == ("Laura", "Teléfono de Laura")


def test_el_ultimo_uso_se_anota_cuando_la_persona_usa_la_app(cliente, auth):
    yo = mi_id(cliente, auth)
    base = cliente.app.state.base
    hace_un_rato = iso(ahora() - timedelta(hours=3))
    with base.transaccion() as con:
        con.execute("UPDATE dispositivos SET ultimo_uso = ? WHERE id = ?", (hace_un_rato, yo))

    cliente.get("/estado", headers=auth)

    uso = next(p["ultimoUso"] for p in cliente.get("/personas", headers=auth).json() if p["vos"])
    assert uso > hace_un_rato


def test_un_codigo_de_la_terminal_sin_nombre_suma_al_telefono_como_administrador(cliente):
    token = parear(cliente, "Pixel 8")
    persona = cliente.get("/personas", headers=cabecera(token)).json()[0]
    assert (persona["nombre"], persona["telefono"], persona["perfil"]) == (None, "Pixel 8", "administrador")
