from tests.conftest import cabecera, parear, todas_las_alertas


def primera_alta(cliente, auth):
    return next(a for a in todas_las_alertas(cliente, auth) if a["severidad"] == "alta")


def revision(accion_id="accion-0001", resultado="ataque_real", motivo="escalado_al_proveedor", comentario="Escalado al proveedor", version_vista=None, forzar=False):
    return {"accionId": accion_id, "resultado": resultado, "motivo": motivo, "comentario": comentario, "versionVista": version_vista, "forzar": forzar}


def test_revisar_cambia_el_estado_y_queda_en_el_historial(cliente, auth):
    alerta = primera_alta(cliente, auth)

    respuesta = cliente.post(f"/alertas/{alerta['id']}/revision", json=revision(version_vista=1), headers=auth)

    assert respuesta.status_code == 200, respuesta.text
    cuerpo = respuesta.json()
    assert cuerpo["estadoRevision"] == "ataque_real"
    assert cuerpo["version"] == 2
    assert len(cuerpo["historial"]) == 1
    assert cuerpo["historial"][0]["autor"] == "Teléfono de prueba"
    assert cuerpo["historial"][0]["motivo"] == "escalado_al_proveedor"

    estado = cliente.get("/estado", headers=auth).json()["sinRevisar"]
    assert estado["alta"] == len([a for a in todas_las_alertas(cliente, auth) if a["severidad"] == "alta"]) - 1


def test_la_misma_accion_reenviada_no_se_aplica_dos_veces(cliente, auth):
    alerta = primera_alta(cliente, auth)
    cuerpo = revision(accion_id="accion-repetida", version_vista=1)

    primera = cliente.post(f"/alertas/{alerta['id']}/revision", json=cuerpo, headers=auth).json()
    segunda = cliente.post(f"/alertas/{alerta['id']}/revision", json=cuerpo, headers=auth).json()

    assert primera["version"] == 2
    assert segunda["version"] == 2
    assert len(segunda["historial"]) == 1


def test_si_otra_persona_reviso_antes_da_409_y_forzar_la_aplica_igual(cliente, auth):
    alerta = primera_alta(cliente, auth)
    otro = cabecera(parear(cliente, nombre="Teléfono de Lucía"))
    cliente.post(f"/alertas/{alerta['id']}/revision", json=revision(accion_id="accion-lucia", version_vista=1), headers=otro)

    conflicto = cliente.post(
        f"/alertas/{alerta['id']}/revision",
        json=revision(accion_id="accion-martin", resultado="falso_positivo", motivo="tarea_programada", comentario="Era el respaldo", version_vista=1),
        headers=auth,
    )

    assert conflicto.status_code == 409
    detalle = conflicto.json()["detail"]
    assert detalle["revisionAjena"]["autor"] == "Teléfono de Lucía"
    assert detalle["alerta"]["version"] == 2

    forzada = cliente.post(
        f"/alertas/{alerta['id']}/revision",
        json=revision(accion_id="accion-martin", resultado="falso_positivo", motivo="tarea_programada", comentario="Era el respaldo", version_vista=1, forzar=True),
        headers=auth,
    )

    assert forzada.status_code == 200
    assert forzada.json()["version"] == 3
    assert forzada.json()["estadoRevision"] == "falso_positivo"
    assert len(forzada.json()["historial"]) == 2


def test_el_motivo_tiene_que_corresponder_al_resultado(cliente, auth):
    alerta = primera_alta(cliente, auth)

    respuesta = cliente.post(f"/alertas/{alerta['id']}/revision", json=revision(motivo="tarea_programada"), headers=auth)

    assert respuesta.status_code == 422
    assert cliente.post("/alertas/no-existe/revision", json=revision(), headers=auth).status_code == 404


def test_una_alerta_revisada_vuelve_a_salir_en_la_sincronizacion_siguiente(cliente, auth):
    cursor = None
    while True:
        pagina = cliente.get("/alertas", params={"desde": cursor} if cursor else None, headers=auth).json()
        cursor = pagina["cursorSiguiente"]
        if not pagina["hayMas"]:
            break
    alerta = primera_alta(cliente, auth)
    cliente.post(f"/alertas/{alerta['id']}/revision", json=revision(version_vista=1), headers=auth)

    novedades = cliente.get("/alertas", params={"desde": cursor}, headers=auth).json()

    assert [a["id"] for a in novedades["alertas"]] == [alerta["id"]]
    assert novedades["alertas"][0]["estadoRevision"] == "ataque_real"


def test_severidades_recomendadas_y_cambio_para_toda_la_red(cliente, auth):
    actuales = cliente.get("/severidades", headers=auth).json()
    assert actuales == {
        "ddos": "media", "escaneo": "media", "fuerza_bruta": "alta", "probable_falsa_alarma": "baja",
        "sin_tipificar": "alta", "solo_reglas_o_umbrales": "media",
    }
    cursor_previo = None
    while True:
        pagina = cliente.get("/alertas", params={"desde": cursor_previo} if cursor_previo else None, headers=auth).json()
        cursor_previo = pagina["cursorSiguiente"]
        if not pagina["hayMas"]:
            break

    respuesta = cliente.put("/severidades", json={"escaneo": "alta", "ddos": "baja"}, headers=auth)

    assert respuesta.status_code == 200
    assert respuesta.json()["escaneo"] == "alta"
    escaneos = [a for a in todas_las_alertas(cliente, auth) if a["tipoAtaque"] == "escaneo"]
    assert escaneos and all(a["severidad"] == "alta" for a in escaneos)
    # Todas las alertas vuelven a salir, porque cambió la severidad que muestran.
    assert cliente.get("/alertas", params={"desde": cursor_previo}, headers=auth).json()["hayMas"] is True
    assert cliente.put("/severidades", json={"otro_tipo": "alta"}, headers=auth).status_code == 422
