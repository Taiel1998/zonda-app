import sqlite3

from app.db import ESQUEMA, Base


def test_una_base_anterior_suma_el_protocolo_la_hora_de_revision_y_pasa_los_motivos_viejos(tmp_path):
    ruta = tmp_path / "anterior.db"
    con = sqlite3.connect(ruta)
    con.executescript(ESQUEMA.replace("    protocolo TEXT NOT NULL DEFAULT 'tcp',\n", ""))
    con.execute(
        "INSERT INTO revisiones (alerta_id, accion_id, resultado, motivo, comentario, autor, fecha, dispositivo_id, version_resultante) "
        "VALUES ('a-1', 'accion-vieja', 'ataque_real', 'equipo_aislado', 'Lo aislamos', 'Pixel', '2026-10-12T00:00:00+00:00', 'd-1', 2)"
    )
    con.commit()
    con.close()

    base = Base(ruta)
    base.iniciar()

    con = base.conexion()
    columnas = {fila[1] for fila in con.execute("PRAGMA table_info(alertas)")}
    assert "protocolo" in columnas
    assert "revisada_en" in {fila[1] for fila in con.execute("PRAGMA table_info(revisiones)")}
    assert con.execute("SELECT motivo FROM revisiones").fetchone()[0] == "bloqueado_manualmente"
