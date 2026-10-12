from datetime import datetime, timedelta, timezone

from tests.conftest import todas_las_alertas


def test_las_alertas_piden_token(cliente):
    assert cliente.get("/alertas").status_code == 401
    assert cliente.get("/estado").status_code == 401


def test_la_primera_sincronizacion_recorre_todas_las_paginas_por_cursor(cliente, auth):
    primera = cliente.get("/alertas", headers=auth).json()
    assert len(primera["alertas"]) == 10
    assert primera["hayMas"] is True
    assert ":" in primera["cursorSiguiente"]

    alertas = todas_las_alertas(cliente, auth)

    assert len(alertas) == cliente.get("/salud").json()["alertas"]
    assert len({a["id"] for a in alertas}) == len(alertas)
    # En orden de creación: la más vieja primero, así la app las recibe como fueron apareciendo.
    assert [a["creadaEn"] for a in alertas] == sorted(a["creadaEn"] for a in alertas)


def test_las_fechas_quedan_corridas_al_presente_y_los_id_son_los_de_la_semilla(cliente, auth):
    alertas = todas_las_alertas(cliente, auth)
    ahora = datetime.now(timezone.utc)
    fines = [datetime.fromisoformat(a["fin"]) for a in alertas]

    assert timedelta(minutes=59) < ahora - max(fines) < timedelta(minutes=61)
    assert min(fines) > ahora - timedelta(days=10)
    assert all(a["id"].startswith("a-") and len(a["id"]) == 14 for a in alertas)
    assert all(a["estadoRevision"] == "sin_revisar" and a["version"] == 1 for a in alertas)


def test_un_cursor_de_otra_instalacion_da_410(cliente, auth):
    assert cliente.get("/alertas", params={"desde": "otra:5"}, headers=auth).status_code == 410
    assert cliente.get("/alertas", params={"desde": "basura"}, headers=auth).status_code == 410


def test_filtros_de_severidad_metodo_y_estado(cliente, auth):
    altas = todas_las_alertas_con(cliente, auth, severidad="alta")
    assert altas and all(a["severidad"] == "alta" for a in altas)
    assert all(a["tipoAtaque"] in ("fuerza_bruta", "sin_tipificar") for a in altas)

    # El filtro se resuelve en SQL: cada página viene completa (10 por página en los tests), no con huecos.
    bajas = todas_las_alertas_con(cliente, auth, severidad="baja")
    primera = cliente.get("/alertas", params={"severidad": "baja"}, headers=auth).json()
    assert len(primera["alertas"]) == min(10, len(bajas))
    assert primera["hayMas"] is (len(bajas) > 10)

    reglas = todas_las_alertas_con(cliente, auth, metodo="reglas")
    assert reglas and all(a["veredictos"]["reglas"] for a in reglas)

    assert todas_las_alertas_con(cliente, auth, estado="ataque_real") == []


def test_la_severidad_sale_del_tipo_y_el_tipo_vacio_es_media(cliente, auth):
    alertas = todas_las_alertas(cliente, auth)
    por_tipo = {}
    for a in alertas:
        por_tipo.setdefault(a["tipoAtaque"], set()).add(a["severidad"])

    assert por_tipo["fuerza_bruta"] == {"alta"}
    assert por_tipo["sin_tipificar"] == {"alta"}
    assert por_tipo["escaneo"] == {"media"}
    assert por_tipo["ddos"] == {"media"}
    assert por_tipo[None] == {"media"}
    assert por_tipo["probable_falsa_alarma"] == {"baja"}


def test_detalle_con_nombres_de_equipo_y_404(cliente, auth):
    alguna = cliente.get("/alertas", headers=auth).json()["alertas"][0]

    detalle = cliente.get(f"/alertas/{alguna['id']}", headers=auth)

    assert detalle.status_code == 200
    cuerpo = detalle.json()
    assert cuerpo["id"] == alguna["id"]
    assert cuerpo["historial"] == []
    assert cuerpo["origenNombre"] is not None and cuerpo["destinoNombre"] is not None
    assert set(cuerpo["veredictos"]) == {"ia", "errorReconstruccion", "umbralIa", "umbrales", "contexto60s", "umbralServicio", "servicioSinLineaBase", "reglas", "parDestino", "parEnCatalogo"}
    assert cliente.get("/alertas/no-existe", headers=auth).status_code == 404


def test_estado_cuenta_las_sin_revisar_por_severidad(cliente, auth):
    alertas = todas_las_alertas(cliente, auth)

    estado = cliente.get("/estado", headers=auth).json()

    esperado = {"alta": 0, "media": 0, "baja": 0}
    for a in alertas:
        esperado[a["severidad"]] += 1
    assert estado["sinRevisar"] == esperado
    assert estado["ultimoFlujoProcesado"] is not None


def todas_las_alertas_con(cliente, auth, **filtros):
    alertas, cursor = [], None
    while True:
        pagina = cliente.get("/alertas", params={**filtros, **({"desde": cursor} if cursor else {})}, headers=auth).json()
        alertas.extend(pagina["alertas"])
        cursor = pagina["cursorSiguiente"]
        if not pagina["hayMas"]:
            return alertas
