import random
from datetime import datetime, timezone

from app.cli import contenido_qr
from app.cli import main as cli
from app.emisor import emitir_una
from tests.conftest import todas_las_alertas


def test_catalogo_tiene_equipos_y_servicios(cliente, auth):
    catalogo = cliente.get("/catalogo", headers=auth).json()

    assert catalogo["equipos"]["192.168.10.3"] == "dc-01"
    assert catalogo["equipos"]["192.168.10.50"] == "srv-web"
    assert "ssh" in catalogo["servicios"]
    assert catalogo["servicios"] == sorted(catalogo["servicios"])


def test_vinculo_de_telegram_entrega_codigo_y_enlace_al_bot(cliente, auth):
    vinculo = cliente.post("/telegram/vinculo", headers=auth).json()

    assert len(vinculo["codigo"]) == 9 and vinculo["codigo"][4] == "-"
    assert vinculo["enlaceBot"] == f"https://t.me/zonda_alertas_bot?start={vinculo['codigo']}"
    fila = cliente.app.state.base.conexion().execute("SELECT * FROM codigos_telegram").fetchone()
    assert fila["codigo"] == vinculo["codigo"] and fila["usado_en"] is None


def test_quitar_el_dispositivo_revoca_el_token(cliente, auth):
    assert cliente.delete("/dispositivos/actual", headers=auth).status_code == 204

    assert cliente.get("/estado", headers=auth).status_code == 401
    assert cliente.get("/salud", headers=auth).status_code == 401


def test_el_emisor_publica_una_alerta_nueva_que_llega_en_la_sincronizacion(cliente, auth):
    cursor = None
    while True:
        pagina = cliente.get("/alertas", params={"desde": cursor} if cursor else None, headers=auth).json()
        cursor = pagina["cursorSiguiente"]
        if not pagina["hayMas"]:
            break

    nuevo_id = emitir_una(cliente.app.state.base, random.Random(1))

    novedades = cliente.get("/alertas", params={"desde": cursor}, headers=auth).json()["alertas"]
    assert [a["id"] for a in novedades] == [nuevo_id]
    assert novedades[0]["estadoRevision"] == "sin_revisar"
    assert cliente.get("/salud").json()["alertas"] == len(todas_las_alertas(cliente, auth))


def test_cli_pair_revoke_y_dispositivos(cliente, config, monkeypatch, capsys):
    monkeypatch.setattr("app.cli.Config.desde_entorno", classmethod(lambda cls: config))
    monkeypatch.setattr("app.cli.RAIZ", config.db_path.parent)

    assert cli(["pair"]) == 0
    salida = capsys.readouterr().out
    assert "Código de pareo:" in salida and "https://prueba.duckdns.org" in salida
    codigo = salida.split("Código de pareo:")[1].split()[0]

    respuesta = cliente.post("/pareo", json={"codigo": codigo, "nombreDispositivo": "Desde la CLI"})
    assert respuesta.status_code == 200
    dispositivo_id = respuesta.json()["dispositivoId"]

    assert cli(["dispositivos"]) == 0
    listado = capsys.readouterr().out
    assert "Desde la CLI" in listado and "activo" in listado

    assert cli(["revoke", dispositivo_id]) == 0
    assert cliente.get("/estado", headers={"Authorization": f"Bearer {respuesta.json()['token']}"}).status_code == 401
    assert cli(["revoke", "d-inexistente"]) == 1


def test_el_qr_lleva_la_direccion_el_codigo_y_cuando_vence():
    vence = datetime(2026, 10, 12, 2, 19, tzinfo=timezone.utc)

    contenido = contenido_qr("https://zonda-empresa.duckdns.org", "K7M2-QX9P", vence)

    assert contenido == "zonda:https://zonda-empresa.duckdns.org#K7M2-QX9P;vence=2026-10-12T02:19:00+00:00"
