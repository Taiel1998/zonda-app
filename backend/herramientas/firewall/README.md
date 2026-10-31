# Agente del firewall (decisión 51)

Aplica en el firewall los bloqueos que aprueban las personas en Zonda. Corre en el firewall (en el laboratorio, `fw-lab`: Debian 13 con nftables) y le pregunta al servidor cada 10 segundos qué tiene que estar bloqueado. El servidor no guarda ninguna clave del firewall: es el firewall el que pregunta, con un token propio que solo sirve para eso.

| Archivo | Qué es | Dónde queda en el firewall |
|---|---|---|
| `agente_firewall.py` | el programa, solo con la biblioteca estándar de Python | `/opt/zonda-firewall/` |
| `zonda.nft` | la tabla de Zonda en nftables | `/etc/zonda-firewall/` |
| `zonda-firewall.service` | el servicio de systemd, con la lista de «nunca bloquear» del laboratorio | `/etc/systemd/system/` |
| `instalar.sh` | copia todo, pide el token y arranca el servicio | se corre desde esta carpeta |

## Qué hace en cada vuelta

1. Pregunta qué tiene que estar bloqueado: `GET /firewall/bloqueos`, con el token en la cabecera `X-Token-Firewall`. Sin proxies ni redirecciones: el token va solo al servidor configurado.
2. Valida cada bloqueo por su cuenta. Solo una IPv4 de un equipo (ni difusión, ni multidifusión, ni enlace local); nada de su lista de «nunca bloquear», ni la dirección del servidor de Zonda, aunque no esté en la lista; de 1 segundo a 24 horas, o hasta desbloquear; 20 equipos a la vez como mucho, y con más siguen primero los que ya estaban.
3. Deja la tabla igual a eso en una sola transacción de nft: agrega lo que falta, saca lo que se desbloqueó y vuelve a poner lo que cambió de duración.
4. Cuenta qué hizo: `POST /firewall/informe`, con lo que quedó aplicado (lo que lee de nuevo en la tabla, no lo que mandó), lo que rechazó y por qué, y su lista de «nunca bloquear», que el servidor usa para no aceptar esos equipos.

Si el servidor no contesta, no toca nada: el núcleo saca solo lo que vence, y lo demás sigue hasta que vuelva. Si alguien recarga las reglas del firewall (`nftables.conf` empieza con `flush ruleset`) o cambia la tabla de Zonda, la arma de nuevo y vuelve a poner los bloqueos: quedan hasta 10 segundos sin ellos. Si la tabla no queda bien ni después de armarla, no bloquea nada y lo dice en el registro.

## La tabla de Zonda

Va aparte de las reglas del firewall, que no se tocan:

```
table inet zonda {
	set bloqueados {
		type ipv4_addr
		flags timeout
		size 256
	}
	chain antes_del_filtro {
		type filter hook forward priority filter - 10; policy accept;
		ip saddr @bloqueados counter drop
		ip daddr @bloqueados counter drop
	}
}
```

La cadena corre antes que las reglas del firewall y descarta lo que sale de un equipo bloqueado o va hacia él, también en las conexiones ya establecidas. El tráfico entre dos equipos de la misma VLAN no pasa por el firewall, así que un bloqueo no lo corta.

## Instalar en fw-lab

Lo hace una persona con la contraseña de root; después el programa corre sin root, con un usuario que crea systemd y un solo permiso del núcleo, `CAP_NET_ADMIN`.

1. En la notebook, generar el token del firewall, ponerlo en `backend/.env` como `ZONDA_FIREWALL_TOKEN=` (distinto del de la ingesta) y reiniciar la API. Para generarlo:
   ```
   python -c "import secrets; print(secrets.token_urlsafe(32))"
   ```
2. Copiar esta carpeta al firewall, desde la raíz del repositorio:
   ```
   scp -P 2223 -r backend/herramientas/firewall taiel@127.0.0.1:/tmp/zonda-firewall
   ```
3. En el firewall, como root (`su -`): `cd /tmp/zonda-firewall` y `sh instalar.sh`. Pide el token, que no se ve al pegarlo.
4. Ver que ande: `journalctl -u zonda-firewall -f` y `nft list set inet zonda bloqueados`.

Para ver qué haría, sin tocar la tabla ni avisarle al servidor (como root, porque el token solo lo lee root):

```
python3 /opt/zonda-firewall/agente_firewall.py --probar --token-archivo /etc/zonda-firewall/token --nunca-bloquear 192.168.10.1,192.168.20.1,192.168.30.1,192.168.40.1,192.168.20.0/24,10.0.2.0/24
```

## Probar la regla a mano

Con el programa parado: si no, en la vuelta siguiente saca lo que el servidor no pidió.

```
systemctl stop zonda-firewall
nft add element inet zonda bloqueados '{ 192.168.30.50 timeout 2m }'
nft list set inet zonda bloqueados
systemctl start zonda-firewall
```

## Soltar todo o desinstalar

- Un bloqueo: «Desbloquear» en la app; el firewall lo saca en la vuelta siguiente.
- Todo, ya: `systemctl stop zonda-firewall && nft delete table inet zonda`. Al arrancar de nuevo, vuelve a poner lo que el servidor tenga aprobado.
- Desinstalar: lo anterior, `systemctl disable zonda-firewall` y `rm -r /opt/zonda-firewall /etc/zonda-firewall /etc/systemd/system/zonda-firewall.service`.

## Pruebas

`backend/tests/test_agente_firewall.py`, con nft y el núcleo simulados (corren también en Windows) y con el servidor de verdad. La forma de la salida de `nft -j` es la que documenta libnftables-json(5).
