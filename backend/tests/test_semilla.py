import json
from dataclasses import replace
from datetime import datetime, timedelta, timezone

from fastapi.testclient import TestClient

from app.config import RAIZ
from app.main import crear_app
from app.modelos import MOTIVOS_POR_RESULTADO
from tests.conftest import cabecera, parear, todas_las_alertas


def test_sin_la_opcion_la_semilla_llega_toda_sin_revisar(cliente, auth):
    alertas = todas_las_alertas(cliente, auth)

    assert all(a["estadoRevision"] == "sin_revisar" and a["historial"] == [] for a in alertas)


def test_con_revisar_semilla_llega_revisada_una_parte_segun_el_dataset(config):
    # ZONDA_REVISAR_SEMILLA=1: para la demo y las capturas del Resumen.
    with TestClient(crear_app(replace(config, revisar_semilla=True))) as cliente:
        auth = cabecera(parear(cliente))
        alertas = todas_las_alertas(cliente, auth)
    ahora = datetime.now(timezone.utc)
    verdad = {a["id"]: a["etiquetaVerdad"] for a in json.loads((RAIZ / "alertas_semilla.json").read_text(encoding="utf-8"))["alertas"]}
    revisadas = [a for a in alertas if a["estadoRevision"] != "sin_revisar"]

    # Tres de cada cuatro de las de más de dos horas; las más nuevas quedan para revisar en la demo.
    assert 0 < len(revisadas) < len(alertas)
    assert all(datetime.fromisoformat(a["creadaEn"]) <= ahora - timedelta(hours=2) for a in revisadas)
    for alerta in revisadas:
        revision = alerta["historial"][0]
        esperado = "falso_positivo" if verdad[alerta["id"]] == "benigno" else "ataque_real"
        assert alerta["estadoRevision"] == esperado == revision["resultado"]
        assert revision["motivo"] in MOTIVOS_POR_RESULTADO[esperado]
        assert revision["comentario"]
        assert alerta["version"] == 2
        creada = datetime.fromisoformat(alerta["creadaEn"])
        assert creada < datetime.fromisoformat(revision["revisadaEn"]) < ahora


def test_la_semilla_revisada_da_siempre_las_mismas_cifras(config, tmp_path):
    def revisadas(nombre: str) -> set[str]:
        with TestClient(crear_app(replace(config, db_path=tmp_path / nombre, revisar_semilla=True))) as cliente:
            auth = cabecera(parear(cliente))
            return {a["id"] for a in todas_las_alertas(cliente, auth) if a["estadoRevision"] != "sin_revisar"}

    assert revisadas("una.db") == revisadas("otra.db")
