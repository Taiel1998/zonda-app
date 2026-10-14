"""`zonda tranquila`: otra persona del equipo revisa desde su teléfono las altas y medias sin revisar
(guion de la demo, paso 11)."""

from tests.test_revision_y_severidades import primera_alta, revision


def sincronizar(cliente, auth, cursor: str | None = None) -> tuple[list[dict], str]:
    """Una sincronización del teléfono: lo nuevo o modificado desde su cursor, y el cursor siguiente."""
    alertas: list[dict] = []
    while True:
        pagina = cliente.get("/alertas", params={"desde": cursor} if cursor else None, headers=auth).json()
        alertas += pagina["alertas"]
        cursor = pagina["cursorSiguiente"]
        if not pagina["hayMas"]:
            return alertas, cursor


def sin_revisar(cliente, auth) -> dict[str, int]:
    return cliente.get("/estado", headers=auth).json()["sinRevisar"]


def test_zonda_tranquila_revisa_desde_otro_telefono_las_altas_y_medias_sin_revisar(cliente, auth, zonda, capsys):
    alertas, cursor = sincronizar(cliente, auth)  # el teléfono de la demo ya bajó todo
    pendientes = {a["id"] for a in alertas if a["estadoRevision"] == "sin_revisar" and a["severidad"] != "baja"}
    antes = sin_revisar(cliente, auth)
    assert antes["alta"] + antes["media"] == len(pendientes) and antes["baja"] > 0

    assert zonda(["tranquila"]) == 0

    salida = capsys.readouterr().out
    assert salida.startswith("Teléfono de Laura (d-")
    assert f"falso positivo {antes['alta']} alta" in salida and f" y {antes['media']} media" in salida
    # Estado pasa a verde; las bajas no cambian el color y siguen esperando en la lista.
    assert sin_revisar(cliente, auth) == {"alta": 0, "media": 0, "baja": antes["baja"]}

    # La sincronización siguiente trae justo esas, con la revisión del otro teléfono en el historial.
    cambiadas, _ = sincronizar(cliente, auth, cursor)
    assert {a["id"] for a in cambiadas} == pendientes
    for alerta in cambiadas:
        assert (alerta["estadoRevision"], alerta["version"]) == ("falso_positivo", 2)
        [hecha] = alerta["historial"]
        assert (hecha["autor"], hecha["resultado"], hecha["motivo"]) == ("Teléfono de Laura", "falso_positivo", "tarea_programada")
        assert hecha["comentario"] == "Revisada desde otro teléfono del equipo."

    # Es un teléfono pareado más, que se puede revocar como cualquier otro.
    assert zonda(["dispositivos"]) == 0
    [fila] = [linea for linea in capsys.readouterr().out.splitlines() if "Teléfono de Laura" in linea]
    assert " activo " in fila


def test_no_toca_las_ya_revisadas_y_vuelve_a_usar_el_mismo_telefono(cliente, auth, zonda, capsys):
    alta = primera_alta(cliente, auth)
    assert cliente.post(f"/alertas/{alta['id']}/revision", json=revision(version_vista=1), headers=auth).status_code == 200

    assert zonda(["tranquila"]) == 0
    assert zonda(["tranquila"]) == 0
    assert capsys.readouterr().out.endswith("No hay altas ni medias sin revisar: la red ya está tranquila.\n")

    # La que revisó este teléfono quedó como la dejó.
    detalle = cliente.get(f"/alertas/{alta['id']}", headers=auth).json()
    assert detalle["estadoRevision"] == "ataque_real"
    assert [hecha["autor"] for hecha in detalle["historial"]] == ["Teléfono de prueba"]

    # Una alta nueva más tarde la revisa el mismo teléfono, sin parear otro.
    assert zonda(["emitir"]) == 0
    assert zonda(["tranquila"]) == 0
    assert "falso positivo 1 alta y 0 medias." in capsys.readouterr().out
    assert zonda(["dispositivos"]) == 0
    assert capsys.readouterr().out.count("Teléfono de Laura") == 1


def test_si_este_telefono_revisa_lo_que_vio_antes_le_muestra_la_revision_de_la_otra_persona(cliente, auth, zonda):
    alta = primera_alta(cliente, auth)  # la vio sin revisar y la revisa sin haber sincronizado

    assert zonda(["tranquila", "--nombre", "Teléfono de Lucía"]) == 0

    respuesta = cliente.post(f"/alertas/{alta['id']}/revision", json=revision(version_vista=alta["version"]), headers=auth)
    assert respuesta.status_code == 409
    conflicto = respuesta.json()["detail"]
    assert conflicto["revisionAjena"]["autor"] == "Teléfono de Lucía"
    assert conflicto["alerta"]["estadoRevision"] == "falso_positivo"
