#!/bin/sh
# Instala en el firewall el programa que aplica los bloqueos de Zonda (decision 51). Se corre como root
# (su -), desde la carpeta con los archivos:   sh instalar.sh
# No toca /etc/nftables.conf: la tabla de Zonda va aparte (zonda.nft). Para sacar todo:
#   systemctl disable --now zonda-firewall && nft delete table inet zonda
#   rm -r /opt/zonda-firewall /etc/zonda-firewall /etc/systemd/system/zonda-firewall.service
set -eu
cd "$(dirname "$0")"
[ "$(id -u)" -eq 0 ] || { echo "Hay que correrlo como root (su -)."; exit 1; }
for programa in nft python3 systemctl; do
    command -v "$programa" >/dev/null || { echo "Falta $programa."; exit 1; }
done

install -d -m 0755 /opt/zonda-firewall /etc/zonda-firewall
install -m 0644 agente_firewall.py /opt/zonda-firewall/agente_firewall.py
install -m 0644 zonda.nft /etc/zonda-firewall/zonda.nft
install -m 0644 zonda-firewall.service /etc/systemd/system/zonda-firewall.service

# El token del firewall: el mismo que ZONDA_FIREWALL_TOKEN en el .env del servidor. Se pega sin que se vea y
# queda en un archivo que solo lee root. Si ya estaba, no se pide de nuevo.
if [ ! -s /etc/zonda-firewall/token ]; then
    printf 'Pega el token del firewall (no se ve al pegarlo) y Enter: '
    stty -echo
    read -r token || true
    stty echo
    echo
    [ -n "${token:-}" ] || { echo "No se pego ningun token."; exit 1; }
    (umask 077 && printf '%s\n' "$token" > /etc/zonda-firewall/token)
fi
chown root:root /etc/zonda-firewall/token
chmod 0600 /etc/zonda-firewall/token

# La tabla de Zonda, para ver que carga bien; despues la mantiene el programa.
nft -f /etc/zonda-firewall/zonda.nft
nft list table inet zonda

systemctl daemon-reload
systemctl enable zonda-firewall
systemctl restart zonda-firewall
echo "Listo. Lo que hace:         journalctl -u zonda-firewall -f"
echo "Lo que esta bloqueado:      nft list set inet zonda bloqueados"
