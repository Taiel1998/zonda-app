"""La importancia de cada equipo (decisión 41) y asignar alertas (decisión 42); correcciones 4 y 1 de Peña."""

import asyncio

import pytest

from app.semilla import ajustar, guardar_equipo
from tests.conftest import todas_las_alertas
from tests.test_alertas import todas_las_alertas_con
from tests.test_perfiles_y_registro import MOTIVO, mi_id, sumar
from tests.test_telegram import vincular

SUELDOS = "192.168.10.50"


@pytest.mark.parametrize(
    ("tipo", "importancia", "final"),
    [
        ("alta", "critico", "alta"), ("alta", "normal", "alta"), ("alta", "poco_importante", "media"),
        ("media", "critico", "alta"), ("media", "normal", "media"), ("media", "poco_importante", "baja"),
        ("baja", "critico", "media"), ("baja", "normal", "baja"), ("baja", "poco_importante", "baja"),
    ],
)
def test_la_tabla_de_3_por_3(tipo, importancia, final):
    # La misma tabla que prueba la app (ImportanciaTest): pesan lo mismo, un escalón con tope y piso.
    assert ajustar(tipo, importancia) == final


def severidades_a_sueldos(cliente, auth) -> dict[str, set[str]]:
    """La severidad final de cada tipo en las alertas a srv-sueldos."""
    por_tipo: dict[str, set[str]] = {}
    for alerta in todas_las_alertas(cliente, auth):
        if alerta["destinoIp"] == SUELDOS:
            por_tipo.setdefault(alerta["tipoAtaque"], set()).add(alerta["severidad"])
    return por_tipo


def test_un_equipo_critico_sube_un_nivel_sus_alertas_y_uno_poco_importante_las_baja(cliente, auth):
    assert severidades_a_sueldos(cliente, auth) == {
        "fuerza_bruta": {"alta"}, "sin_tipificar": {"alta"}, "escaneo": {"media"}, "ddos": {"media"}, "probable_falsa_alarma": {"baja"},
    }

    respuesta = cliente.put(f"/equipos/{SUELDOS}", json={"importancia": "critico", "motivo": "Ahí están los sueldos de toda la empresa."}, headers=auth)

    assert respuesta.json() == {"ip": SUELDOS, "nombre": "srv-sueldos", "importancia": "critico"}
    assert severidades_a_sueldos(cliente, auth) == {
        "fuerza_bruta": {"alta"}, "sin_tipificar": {"alta"}, "escaneo": {"alta"}, "ddos": {"alta"}, "probable_falsa_alarma": {"media"},
    }
    # El filtro de la lista y los contadores de Estado usan la misma severidad final que el detalle.
    assert all(a["severidad"] == "alta" for a in todas_las_alertas_con(cliente, auth, severidad="alta"))
    esperado = {"alta": 0, "media": 0, "baja": 0}
    for alerta in todas_las_alertas(cliente, auth):
        if alerta["estadoRevision"] == "sin_revisar":
            esperado[alerta["severidad"]] += 1
    assert cliente.get("/estado", headers=auth).json()["sinRevisar"] == esperado

    cliente.put(f"/equipos/{SUELDOS}", json={"importancia": "poco_importante", "motivo": "Se mudó a la nube."}, headers=auth)
    assert severidades_a_sueldos(cliente, auth) == {
        "fuerza_bruta": {"media"}, "sin_tipificar": {"media"}, "escaneo": {"baja"}, "ddos": {"baja"}, "probable_falsa_alarma": {"baja"},
    }


def test_cambiar_la_importancia_queda_en_el_registro_y_vuelve_a_bajar_las_alertas_de_ese_equipo(cliente, auth):
    primera = cliente.get("/alertas", headers=auth).json()
    cursor = primera["cursorSiguiente"]
    while primera["hayMas"]:
        primera = cliente.get("/alertas", params={"desde": cursor}, headers=auth).json()
        cursor = primera["cursorSiguiente"]

    cliente.put(f"/equipos/{SUELDOS}", json={"importancia": "critico", "motivo": f" {MOTIVO} "}, headers=auth)

    movidas = cliente.get("/alertas", params={"desde": cursor}, headers=auth).json()["alertas"]
    assert movidas and {a["destinoIp"] for a in movidas} == {SUELDOS}
    cambio = next(c for c in cliente.get("/registro", headers=auth).json() if c["tipo"] == "importancia")
    assert (cambio["objeto"], cambio["nombre"], cambio["antes"], cambio["despues"], cambio["motivo"]) == (SUELDOS, "srv-sueldos", "normal", "critico", MOTIVO)


def test_la_importancia_pide_motivo_y_un_equipo_del_catalogo(cliente, auth):
    assert cliente.put(f"/equipos/{SUELDOS}", json={"importancia": "critico"}, headers=auth).status_code == 422
    assert cliente.put(f"/equipos/{SUELDOS}", json={"importancia": "critico", "motivo": " "}, headers=auth).status_code == 422
    assert cliente.put("/equipos/10.0.0.99", json={"importancia": "critico", "motivo": MOTIVO}, headers=auth).status_code == 404
    equipos = cliente.get("/equipos", headers=auth).json()
    assert {e["importancia"] for e in equipos} == {"normal"}
    assert next(e for e in equipos if e["ip"] == SUELDOS)["nombre"] == "srv-sueldos"


def test_el_catalogo_que_llega_despues_conserva_la_importancia(cliente, auth):
    cliente.put(f"/equipos/{SUELDOS}", json={"importancia": "critico", "motivo": MOTIVO}, headers=auth)
    base = cliente.app.state.base
    with base.transaccion() as con:
        guardar_equipo(con, SUELDOS, "srv-sueldos-2")

    equipo = next(e for e in cliente.get("/equipos", headers=auth).json() if e["ip"] == SUELDOS)
    assert (equipo["nombre"], equipo["importancia"]) == ("srv-sueldos-2", "critico")


# ---- Asignar (decisión 42) ----


def asignar(cliente, auth, alerta_id: str, responsable_id: str | None, accion: str):
    return cliente.put(f"/alertas/{alerta_id}/asignacion", json={"accionId": f"asignar-{accion}", "responsableId": responsable_id}, headers=auth)


def una_alerta(cliente, auth) -> dict:
    return next(a for a in todas_las_alertas(cliente, auth) if a["severidad"] == "alta")


def test_el_administrador_asigna_a_cualquiera_y_saca_la_asignacion(cliente, auth):
    laura_id, _ = sumar(cliente, auth)
    alerta = una_alerta(cliente, auth)
    assert alerta["asignacion"] is None and alerta["asignaciones"] == []

    asignada = asignar(cliente, auth, alerta["id"], laura_id, "1").json()

    assert (asignada["asignacion"]["responsableId"], asignada["asignacion"]["responsable"], asignada["asignacion"]["por"]) == (laura_id, "Laura", "Teléfono de prueba")
    # Asignar no es revisar: la versión de la revisión no cambia (no da conflictos).
    assert asignada["version"] == alerta["version"]

    sin_asignar = asignar(cliente, auth, alerta["id"], None, "2").json()

    assert sin_asignar["asignacion"] is None
    assert [(e["responsable"], e["por"]) for e in sin_asignar["asignaciones"]] == [("Laura", "Teléfono de prueba"), (None, "Teléfono de prueba")]
    # También se la puede asignar a sí mismo.
    assert asignar(cliente, auth, alerta["id"], mi_id(cliente, auth), "3").json()["asignacion"]["responsable"] == "Teléfono de prueba"


def test_la_analista_se_asigna_una_sin_asignar_y_la_suelta(cliente, auth):
    laura_id, laura = sumar(cliente, auth)
    alerta = una_alerta(cliente, auth)

    assert asignar(cliente, laura, alerta["id"], laura_id, "1").json()["asignacion"]["por"] == "Laura"
    soltada = asignar(cliente, laura, alerta["id"], None, "2").json()

    assert soltada["asignacion"] is None
    assert [(e["responsable"], e["por"]) for e in soltada["asignaciones"]] == [("Laura", "Laura"), (None, "Laura")]


def test_la_analista_no_le_saca_una_alerta_a_otra_persona_ni_asigna_a_otros(cliente, auth):
    yo = mi_id(cliente, auth)
    laura_id, laura = sumar(cliente, auth)
    alerta = una_alerta(cliente, auth)
    asignar(cliente, auth, alerta["id"], yo, "1")

    assert asignar(cliente, laura, alerta["id"], laura_id, "2").status_code == 409
    assert asignar(cliente, laura, alerta["id"], None, "3").status_code == 403
    otra = next(a for a in todas_las_alertas(cliente, auth) if a["id"] != alerta["id"])
    assert asignar(cliente, laura, otra["id"], yo, "4").status_code == 403
    assert cliente.get(f"/alertas/{alerta['id']}", headers=auth).json()["asignacion"]["responsableId"] == yo
    # Asignar no traba nada: la analista la puede revisar igual.
    revision = {"accionId": "revision-de-laura", "resultado": "falso_positivo", "motivo": "backup", "comentario": "Era el backup."}
    assert cliente.post(f"/alertas/{alerta['id']}/revision", json=revision, headers=laura).status_code == 200


def test_la_misma_asignacion_reenviada_no_se_aplica_dos_veces_y_mueve_la_alerta(cliente, auth):
    laura_id, _ = sumar(cliente, auth)
    alerta = una_alerta(cliente, auth)
    cursor = None
    while True:
        pagina = cliente.get("/alertas", params={"desde": cursor} if cursor else None, headers=auth).json()
        cursor = pagina["cursorSiguiente"]
        if not pagina["hayMas"]:
            break

    asignar(cliente, auth, alerta["id"], laura_id, "1")
    asignar(cliente, auth, alerta["id"], None, "1")  # la misma acción desde la cola: no hace nada

    assert [a["id"] for a in cliente.get("/alertas", params={"desde": cursor}, headers=auth).json()["alertas"]] == [alerta["id"]]
    assert len(cliente.get(f"/alertas/{alerta['id']}", headers=auth).json()["asignaciones"]) == 1


def test_no_se_asigna_a_alguien_que_no_esta_en_el_equipo(cliente, auth):
    laura_id, _ = sumar(cliente, auth)
    alerta = una_alerta(cliente, auth)
    cliente.delete(f"/personas/{laura_id}", headers=auth)

    assert asignar(cliente, auth, alerta["id"], laura_id, "1").status_code == 422
    assert asignar(cliente, auth, "a-inexistente", None, "2").status_code == 404


def test_a_quien_le_asignan_le_llega_un_aviso_y_asignarse_una_no_avisa(cliente, auth, telegram, bot):
    laura_id, laura = sumar(cliente, auth)
    vincular(cliente, laura, telegram, bot, chat_id=777)
    enviados_antes = len(telegram.enviados)
    alerta = una_alerta(cliente, auth)

    asignar(cliente, auth, alerta["id"], laura_id, "1")
    assert asyncio.run(bot.avisar_asignaciones()) == 1

    aviso = telegram.enviados[-1]
    assert aviso["chat_id"] == "777"
    assert aviso["text"].startswith(f"<b>Teléfono de prueba te asignó una alerta ALTA · {alerta['servicio'].upper()} · ")
    assert aviso["text"].endswith("</b>\nTocá para ver el detalle en Zonda.")
    assert aviso["reply_markup"]["inline_keyboard"][0][0]["url"].startswith(f"https://taiel1998.github.io/a/{alerta['id']}?red=red-prueba")
    # Nunca dos veces el mismo aviso; y asignarse una uno mismo no avisa.
    assert asyncio.run(bot.avisar_asignaciones()) == 0
    otra = next(a for a in todas_las_alertas(cliente, auth) if a["id"] != alerta["id"])
    asignar(cliente, laura, otra["id"], laura_id, "2")
    assert asyncio.run(bot.avisar_asignaciones()) == 0
    assert len(telegram.enviados) == enviados_antes + 1
