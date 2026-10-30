// Lo que usan todas las partes del tablero: hablar con la API, dar formato a números y horas, y armar
// elementos de la página sin meter texto como HTML (los nombres vienen de la red y no son de confianza).

const CLAVE_ACCESO = 'zonda.tablero.acceso';

export class ErrorApi extends Error {
  constructor(estado, detalle) {
    super(detalle || `Error ${estado}`);
    this.estado = estado;
  }
}

export function leerAcceso() {
  try {
    const acceso = JSON.parse(localStorage.getItem(CLAVE_ACCESO) || 'null');
    if (acceso && acceso.token && (!acceso.vence || Date.parse(acceso.vence) > Date.now())) return acceso;
  } catch (_) { /* sin almacenamiento: se pide el código de nuevo */ }
  return null;
}

export function guardarAcceso(acceso) {
  try { localStorage.setItem(CLAVE_ACCESO, JSON.stringify(acceso)); } catch (_) { /* queda solo en memoria */ }
}

export function borrarAcceso() {
  try { localStorage.removeItem(CLAVE_ACCESO); } catch (_) { /* nada que borrar */ }
}

/** Pide algo a la API del mismo servidor. Con `token`, va como en la app: Authorization: Bearer. */
export async function api(ruta, { token, metodo = 'GET', cuerpo, espera = 10000 } = {}) {
  const control = new AbortController();
  const reloj = setTimeout(() => control.abort(), espera);
  const cabeceras = { Accept: 'application/json' };
  if (token) cabeceras.Authorization = `Bearer ${token}`;
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';
  let respuesta;
  try {
    respuesta = await fetch(ruta, {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      signal: control.signal,
      cache: 'no-store',
      credentials: 'omit',
    });
  } catch (error) {
    throw new ErrorApi(0, 'El servidor no contesta');
  } finally {
    clearTimeout(reloj);
  }
  if (respuesta.status === 204) return null;
  let datos = null;
  try { datos = await respuesta.json(); } catch (_) { /* sin cuerpo */ }
  if (!respuesta.ok) {
    const detalle = datos && typeof datos.detail === 'string' ? datos.detail : null;
    throw new ErrorApi(respuesta.status, detalle);
  }
  return datos;
}

// ---- Números y horas, en castellano rioplatense ----

const formatos = new Map();
function formato(decimales) {
  if (!formatos.has(decimales)) {
    formatos.set(decimales, new Intl.NumberFormat('es-AR', { minimumFractionDigits: decimales, maximumFractionDigits: decimales }));
  }
  return formatos.get(decimales);
}

export function numero(valor, decimales = 0) {
  if (valor === null || valor === undefined || Number.isNaN(valor)) return '–';
  return formato(decimales).format(valor);
}

/** Con decimales solo cuando hacen falta: 0,4 · 3,2 · 18 · 1.204. */
export function tasa(valor) {
  if (!valor) return '0';
  if (valor < 10 && !Number.isInteger(valor)) return numero(valor, 1);
  return numero(valor, 0);
}

/** Las marcas de un eje: sin ceros de más (0,25 · 0,5 · 1 · 2,5 · 40). */
export function ejeNumero(valor) {
  if (!valor) return '0';
  const decimales = valor < 1 ? 2 : valor < 10 ? 1 : 0;
  return new Intl.NumberFormat('es-AR', { maximumFractionDigits: decimales }).format(valor);
}

export function porcentaje(parte, total, decimales = 0) {
  if (!total) return '–';
  return `${numero((100 * parte) / total, decimales)} %`;
}

const UNIDADES_BITS = ['bit/s', 'kbit/s', 'Mbit/s', 'Gbit/s'];
export function bitsPorSegundo(valor) {
  if (!valor) return '0 bit/s';
  let i = 0;
  while (valor >= 1000 && i < UNIDADES_BITS.length - 1) { valor /= 1000; i += 1; }
  return `${numero(valor, valor < 10 && i > 0 ? 1 : 0)} ${UNIDADES_BITS[i]}`;
}

const UNIDADES_BYTES = ['B', 'kB', 'MB', 'GB'];
export function bytes(valor) {
  if (!valor) return '0 B';
  let i = 0;
  while (valor >= 1000 && i < UNIDADES_BYTES.length - 1) { valor /= 1000; i += 1; }
  return `${numero(valor, valor < 10 && i > 0 ? 1 : 0)} ${UNIDADES_BYTES[i]}`;
}

const HORA = new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false });
const HORA_SEGUNDOS = new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
const DIA = new Intl.DateTimeFormat('es-AR', { day: '2-digit', month: '2-digit' });
const DIA_LARGO = new Intl.DateTimeFormat('es-AR', { weekday: 'short', day: 'numeric', month: 'short' });

export const hora = (ms) => HORA.format(ms);
export const horaConSegundos = (ms) => HORA_SEGUNDOS.format(ms);
export const dia = (ms) => DIA.format(ms);
export const diaLargo = (ms) => DIA_LARGO.format(ms);

/** "14:05" si es de hoy; "29/10 14:05" si no. */
export function fechaHora(ms, hoy = Date.now()) {
  const fecha = new Date(ms);
  const ahora = new Date(hoy);
  const mismoDia = fecha.toDateString() === ahora.toDateString();
  return mismoDia ? hora(ms) : `${dia(ms)} ${hora(ms)}`;
}

/** "14:05:32" si es de hoy; "29/10 14:05:32" si no. */
export function fechaHoraSegundos(ms, hoy = Date.now()) {
  const mismoDia = new Date(ms).toDateString() === new Date(hoy).toDateString();
  return mismoDia ? horaConSegundos(ms) : `${dia(ms)} ${horaConSegundos(ms)}`;
}

export function haceCuanto(ms, ahora = Date.now()) {
  const segundos = Math.max(0, Math.round((ahora - ms) / 1000));
  if (segundos < 60) return `hace ${segundos} s`;
  const minutos = Math.round(segundos / 60);
  if (minutos < 60) return `hace ${minutos} min`;
  const horas = Math.round(minutos / 60);
  if (horas < 48) return `hace ${horas} h`;
  return `hace ${Math.round(horas / 24)} días`;
}

/** Escapa texto que va dentro de un globito de ECharts, que sí se arma como HTML. */
export function escapar(texto) {
  return String(texto ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

/** Un elemento con atributos e hijos; el texto va siempre como texto, nunca como HTML. */
export function el(etiqueta, atributos = {}, ...hijos) {
  const nodo = document.createElement(etiqueta);
  for (const [clave, valor] of Object.entries(atributos)) {
    if (valor === null || valor === undefined || valor === false) continue;
    if (clave === 'class') nodo.className = valor;
    else if (clave === 'style') nodo.setAttribute('style', valor);
    else if (clave.startsWith('on') && typeof valor === 'function') nodo.addEventListener(clave.slice(2), valor);
    else if (clave === 'dataset') Object.assign(nodo.dataset, valor);
    else nodo.setAttribute(clave, valor === true ? '' : valor);
  }
  for (const hijo of hijos.flat()) {
    if (hijo === null || hijo === undefined || hijo === false) continue;
    nodo.append(hijo instanceof Node ? hijo : document.createTextNode(String(hijo)));
  }
  return nodo;
}

export function vaciar(nodo, ...hijos) {
  nodo.replaceChildren(...hijos.flat().filter((h) => h !== null && h !== undefined && h !== false));
}

/** Un ícono de la librería chica del tablero, como SVG en línea (sin archivos de afuera). */
const TRAZOS = {
  check: '<path d="M20 6 9 17l-5-5"/>',
  alerta: '<path d="M12 9v4"/><path d="M12 17h.01"/><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/>',
  cruz: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  flecha: '<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>',
  luna: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>',
  sol: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.9 4.9 1.4 1.4"/><path d="m17.7 17.7 1.4 1.4"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m4.9 19.1 1.4-1.4"/><path d="m17.7 6.3 1.4-1.4"/>',
  ia: '<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M9 9h6v6H9z"/><path d="M9 1v3"/><path d="M15 1v3"/><path d="M9 20v3"/><path d="M15 20v3"/><path d="M20 9h3"/><path d="M20 14h3"/><path d="M1 9h3"/><path d="M1 14h3"/>',
  umbral: '<path d="M3 3v18h18"/><path d="m7 15 4-4 3 3 6-7"/><path d="M3 8h18" stroke-dasharray="2 3"/>',
  regla: '<path d="M9 11V7a3 3 0 0 1 6 0v4"/><rect x="5" y="11" width="14" height="10" rx="2"/>',
  pregunta: '<circle cx="12" cy="12" r="9"/><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 2.5-3 4"/><path d="M12 17h.01"/>',
};
export function icono(nombre) {
  const plantilla = document.createElement('template');
  plantilla.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${TRAZOS[nombre] || ''}</svg>`;
  return plantilla.content.firstChild;
}
