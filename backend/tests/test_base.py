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


def test_una_base_anterior_suma_lo_que_guarda_el_bot_de_telegram(tmp_path):
    ruta = tmp_path / "anterior.db"
    con = sqlite3.connect(ruta)
    con.executescript(
        """
        CREATE TABLE dispositivos (id TEXT PRIMARY KEY, nombre TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE,
            creado_en TEXT NOT NULL, vence_en TEXT NOT NULL, revocado_en TEXT, telegram_chat_id TEXT);
        CREATE TABLE alertas (id TEXT PRIMARY KEY, protocolo TEXT NOT NULL DEFAULT 'tcp',
            estado_revision TEXT NOT NULL DEFAULT 'sin_revisar', secuencia INTEGER NOT NULL);
        INSERT INTO dispositivos VALUES ('d-1', 'Pixel', 'hash', '2026-10-12T00:00:00+00:00', '2027-01-10T00:00:00+00:00', NULL, NULL);
        """
    )
    con.commit()
    con.close()

    base = Base(ruta)
    base.iniciar()

    con = base.conexion()
    assert "aviso_telegram_en" in {fila[1] for fila in con.execute("PRAGMA table_info(alertas)")}
    fila = con.execute("SELECT telegram_vinculado_en, aviso_alta, aviso_media, aviso_baja FROM dispositivos").fetchone()
    assert tuple(fila) == (None, "sonido", "vibracion", "sin_aviso")
    assert con.execute("SELECT COUNT(*) FROM avisos_telegram").fetchone()[0] == 0
