from fastapi.testclient import TestClient

from app.main import VERSION, app

cliente = TestClient(app)


def test_salud_devuelve_version_y_hora():
    respuesta = cliente.get("/salud")
    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["version"] == VERSION
    assert "hora" in cuerpo
    assert cuerpo["alertas"] == 0
