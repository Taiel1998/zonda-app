"""Comandos de administración del servidor (informe, sección 13.4). Se corren en la computadora
donde está la API; no hay interfaz web de administración.

    zonda pair            genera un código de pareo de un uso (10 minutos) y lo muestra como QR
    zonda revoke <id>     revoca el token de un teléfono
    zonda dispositivos    lista los teléfonos pareados
"""

import argparse
import sys

from .auth import ahora, crear_codigo_pareo, iso
from .config import RAIZ, Config
from .db import Base


def comando_pair(base: Base, cfg: Config) -> int:
    with base.transaccion() as con:
        codigo, vence = crear_codigo_pareo(con, cfg.minutos_codigo)
    contenido = f"zonda:{cfg.url_publica}#{codigo}"
    print(f"Código de pareo: {codigo}   (vence {vence.astimezone().strftime('%H:%M')}, un solo uso)")
    print(f"Dirección de la red: {cfg.url_publica}")
    try:
        import qrcode
    except ImportError:
        print("Para ver el QR instalá qrcode: pip install 'qrcode[pil]'. Mientras tanto, cargá los datos a mano en la app.")
        return 0
    qr = qrcode.QRCode(border=1)
    qr.add_data(contenido)
    qr.make()
    try:
        ruta = RAIZ / f"pareo_{codigo}.png"
        qr.make_image().save(ruta)
        print(f"QR guardado en {ruta}")
    except Exception as error:  # sin Pillow no hay PNG, pero el QR en pantalla alcanza
        print(f"(no se pudo guardar el PNG: {error})")
    try:
        # La consola de Windows arranca en cp1252 y no sabe dibujar los bloques del QR.
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        qr.print_ascii(invert=True)
    except Exception:
        print("(la consola no puede dibujar el QR; abrí el PNG)")
    return 0


def comando_revoke(base: Base, dispositivo_id: str) -> int:
    with base.transaccion() as con:
        fila = con.execute("SELECT * FROM dispositivos WHERE id = ?", (dispositivo_id,)).fetchone()
        if fila is None:
            print(f"No existe el dispositivo {dispositivo_id}")
            return 1
        con.execute("UPDATE dispositivos SET revocado_en = ? WHERE id = ?", (iso(ahora()), dispositivo_id))
    print(f"Token de {fila['nombre']} ({dispositivo_id}) revocado. La app va a pedir un pareo nuevo.")
    return 0


def comando_dispositivos(base: Base) -> int:
    filas = base.conexion().execute("SELECT * FROM dispositivos ORDER BY creado_en").fetchall()
    if not filas:
        print("No hay teléfonos pareados.")
        return 0
    for f in filas:
        estado = "revocado" if f["revocado_en"] else ("vencido" if f["vence_en"] < iso(ahora()) else "activo")
        telegram = "Telegram vinculado" if f["telegram_chat_id"] else "sin Telegram"
        print(f"{f['id']}  {f['nombre']:<24} {estado:<9} vence {f['vence_en'][:10]}  {telegram}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="zonda", description="Administración del servidor de Zonda")
    sub = parser.add_subparsers(dest="comando", required=True)
    sub.add_parser("pair", help="genera un código de pareo y su QR")
    revoke = sub.add_parser("revoke", help="revoca el token de un teléfono")
    revoke.add_argument("dispositivo_id")
    sub.add_parser("dispositivos", help="lista los teléfonos pareados")
    args = parser.parse_args(argv)

    cfg = Config.desde_entorno()
    base = Base(cfg.db_path)
    base.iniciar()
    if args.comando == "pair":
        return comando_pair(base, cfg)
    if args.comando == "revoke":
        return comando_revoke(base, args.dispositivo_id)
    return comando_dispositivos(base)


if __name__ == "__main__":
    sys.exit(main())
