// Los avisos de esta compu, como las notificaciones de la app: cuando llega una alerta alta y el tablero no
// está a la vista, el navegador muestra «Zonda · Red» con cuántas llegaron, sin direcciones ni nombres de
// equipos (RNF02). Hay uno solo, que se reemplaza con las que siguen llegando. Tocarlo trae el tablero al
// frente con el detalle o, si fueron varias, con la lista de las altas sin revisar. Se activan en Ajustes y
// el navegador pide confirmar el permiso; lo elegido queda en este navegador.

import { ahoraServidor, e } from './comun.js';

const CLAVE = 'zonda.tablero.avisos';
let activados = leer();
// Las altas avisadas desde la última vez que el tablero estuvo a la vista.
let pendientes = [];
let alAbrir = { detalle: () => {}, lista: () => {} };

function leer() {
  try { return localStorage.getItem(CLAVE) === 'si'; } catch (_) { return false; }
}

function guardar(si) {
  activados = si;
  try { localStorage.setItem(CLAVE, si ? 'si' : 'no'); } catch (_) { /* sin almacenamiento, vale mientras la pestaña siga abierta */ }
}

const posibles = () => 'Notification' in window && window.isSecureContext;
const aLaVista = () => document.visibilityState === 'visible' && document.hasFocus();

/** 'activados', 'apagados', 'bloqueados' (se dijo que no en el navegador) o 'imposibles' (el navegador no los muestra). */
export function estadoAvisos() {
  if (!posibles()) return 'imposibles';
  if (Notification.permission === 'denied') return 'bloqueados';
  return Notification.permission === 'granted' && activados ? 'activados' : 'apagados';
}

export const avisosActivados = () => estadoAvisos() === 'activados';

export function configurarAvisos({ abrirDetalle, abrirLista }) {
  alAbrir = { detalle: abrirDetalle, lista: abrirLista };
  // De vuelta en el tablero, la cuenta empieza de nuevo.
  const alVolver = () => { if (aLaVista()) pendientes = []; };
  document.addEventListener('visibilitychange', alVolver);
  window.addEventListener('focus', alVolver);
}

/** Pide el permiso (el navegador pregunta una sola vez) y devuelve cómo quedó. */
export async function permitirAvisos() {
  if (!posibles()) return estadoAvisos();
  guardar((await Notification.requestPermission()) === 'granted');
  return estadoAvisos();
}

export function apagarAvisos() {
  guardar(false);
  pendientes = [];
}

function mostrar(texto, alTocar, tag = 'zonda-altas') {
  const red = e.acceso && e.acceso.red ? e.acceso.red.nombre : 'Red';
  let aviso;
  try {
    aviso = new Notification(`Zonda · ${red}`, { body: texto, tag, renotify: true, icon: 'img/logo_zonda.png' });
  } catch (_) {
    return false;
  }
  aviso.onclick = () => {
    window.focus();
    aviso.close();
    alTocar();
  };
  return true;
}

/** Las altas nuevas, si el tablero no está a la vista (con él a la vista ya está el aviso de la página). */
export function avisarEnLaCompu(nuevas) {
  if (!avisosActivados() || aLaVista()) return;
  const altas = nuevas.filter((a) => a.severidad === 'alta' && a.estadoRevision === 'sin_revisar'
    && ahoraServidor() - Date.parse(a.creadaEn) < 15 * 60e3);
  if (!altas.length) return;
  pendientes.push(...altas.map((a) => a.id));
  const ids = pendientes.slice();
  mostrar(`${ids.length} ${ids.length === 1 ? 'alerta nueva' : 'alertas nuevas'} de severidad alta`,
    () => (ids.length === 1 ? alAbrir.detalle(ids[0]) : alAbrir.lista('alta')));
}

/** Devuelve si el navegador lo mostró. */
export function avisoDePrueba() {
  return mostrar('Aviso de prueba: así llegan las alertas altas cuando el tablero no está a la vista.', () => {}, 'zonda-prueba');
}
