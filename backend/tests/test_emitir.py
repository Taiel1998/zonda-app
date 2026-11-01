"""`zonda emitir`: la alerta que pide la demo, en el momento justo (guion de la demo, paso 7)."""

import asyncio
import sqlite3
from datetime import datetime, timedelta, timezone

import pytest

from app.emisor import emitir_periodicamente
from tests.conftest import todas_las_alertas
from tests.test_telegram import alerta_del_boton, vincular


def id_emitido(salida: str) -> str:
    return salida.split("Alerta nueva ")[1].split(":")[0]


def test_zonda_emitir_sin_argumentos_crea_ya_una_alta_de_fuerza_bruta_por_ssh(cliente, auth, zonda, capsys):
    antes = {alerta["id"] for alerta in todas_las_alertas(cliente, auth)}

    assert zonda(["emitir"]) == 0

    salida = capsys.readouterr().out
    nueva = id_emitido(salida)
    assert nueva not in antes and "fuerza_bruta por SSH, severidad alta" in salida
    alerta = cliente.get(f"/alertas/{nueva}", headers=auth).json()
    assert (alerta["tipoAtaque"], alerta["servicio"], alerta["severidad"]) == ("fuerza_bruta", "ssh", "alta")
    assert alerta["estadoRevision"] == "sin_revisar" and alerta["historial"] == []
    # Con la hora de ahora: en la app aparece arriba de todo y Estado la cuenta como sin revisar.
    assert datetime.now(timezone.utc) - datetime.fromisoformat(alerta["creadaEn"]) < timedelta(minutes=1)
    assert len(todas_las_alertas(cliente, auth)) == len(antes) + 1


def test_zonda_emitir_copia_una_alerta_del_tipo_y_el_servicio_pedidos(cliente, auth, zonda, capsys):
    # Las que la IA no vio no tienen tipo: se piden por el detector que las marcó, con su clave de severidades (cambio 9).
    assert zonda(["emitir", "servicio_no_autorizado", "SSH"]) == 0
    reglas = cliente.get(f"/alertas/{id_emitido(capsys.readouterr().out)}", headers=auth).json()
    assert (reglas["tipoAtaque"], reglas["servicio"], reglas["severidad"], reglas["veredictos"]["reglas"]) == (None, "ssh", "media", True)
    assert zonda(["emitir", "volumen_inusual"]) == 0
    umbrales = cliente.get(f"/alertas/{id_emitido(capsys.readouterr().out)}", headers=auth).json()
    assert (umbrales["tipoAtaque"], umbrales["veredictos"]["umbrales"], umbrales["veredictos"]["reglas"]) == (None, True, False)

    # Sin servicio vale cualquiera de ese tipo.
    assert zonda(["emitir", "escaneo"]) == 0
    escaneo = cliente.get(f"/alertas/{id_emitido(capsys.readouterr().out)}", headers=auth).json()
    assert (escaneo["tipoAtaque"], escaneo["severidad"]) == ("escaneo", "media")

    total = cliente.get("/salud").json()["alertas"]
    assert zonda(["emitir", "ddos", "ssh"]) == 1
    assert "No hay ninguna alerta de tipo ddos por ssh para copiar." in capsys.readouterr().out
    assert cliente.get("/salud").json()["alertas"] == total

    # Un tipo que no está en la tabla de severidades ni siquiera llega a la base.
    with pytest.raises(SystemExit) as salida:
        zonda(["emitir", "phishing"])
    assert salida.value.code == 2


def test_el_aviso_de_la_alerta_emitida_suena_y_abre_esa_alerta(cliente, auth, telegram, bot, zonda, capsys):
    vincular(cliente, auth, telegram, bot)  # los avisos del Figma: alta con sonido
    telegram.enviados.clear()

    assert zonda(["emitir"]) == 0
    nueva = id_emitido(capsys.readouterr().out)

    # La vuelta que el bot da cada 5 segundos.
    assert asyncio.run(bot.avisar_nuevas()) == 1
    aviso = telegram.enviados[0]
    assert aviso["text"].startswith("<b>Alerta ALTA · SSH · ")
    assert aviso["disable_notification"] is False
    assert alerta_del_boton(aviso) == nueva


def test_el_emisor_de_la_demo_sigue_si_la_base_esta_trabada(cliente, monkeypatch):
    """Con ZONDA_EMITIR_CADA, una vuelta con la base trabada se saltea y la siguiente emite: antes la tarea se
    cortaba sin avisar y la demo se quedaba sin alertas nuevas."""
    base = cliente.app.state.base
    emitidas: list[str] = []

    def emitir_con_una_falla(base_de_datos) -> str:
        if not emitidas:
            emitidas.append("falla")
            raise sqlite3.OperationalError("database is locked")
        emitidas.append("ok")
        return "a-prueba"

    class FinDeLaPrueba(Exception):
        pass

    vueltas: list[int] = []

    async def esperar(segundos: float) -> None:
        vueltas.append(1)
        if len(vueltas) == 3:
            raise FinDeLaPrueba

    monkeypatch.setattr("app.emisor.emitir_una", emitir_con_una_falla)
    monkeypatch.setattr("app.emisor.asyncio.sleep", esperar)
    with pytest.raises(FinDeLaPrueba):
        asyncio.run(emitir_periodicamente(base, 30))

    assert emitidas == ["falla", "ok"]
