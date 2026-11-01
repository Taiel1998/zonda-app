// Lo que usan todas las partes del tablero: hablar con la API, dar formato a números y horas, y armar
// elementos de la página sin meter texto como HTML (los nombres vienen de la red y no son de confianza).

const CLAVE_ACCESO = 'zonda.tablero.acceso';

export class ErrorApi extends Error {
  constructor(estado, detalle, datos = null) {
    super(detalle || `Error ${estado}`);
    this.estado = estado;
    // El cuerpo de la respuesta: el 409 de una revisión trae la revisión ajena y la alerta.
    this.datos = datos;
  }
}

/** Un identificador al azar para accionId: la misma acción reenviada no se aplica dos veces. */
export function nuevoId() {
  const bytes = new Uint8Array(12);
  crypto.getRandomValues(bytes);
  return `w-${Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')}`;
}

/** La hora de ahora en ISO 8601 con la zona del navegador, como manda la app "revisadaEn". */
export function isoConZona(fecha = new Date()) {
  const desfase = -fecha.getTimezoneOffset();
  const signo = desfase >= 0 ? '+' : '-';
  const dos = (n) => String(Math.floor(Math.abs(n))).padStart(2, '0');
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}T${dos(fecha.getHours())}:${dos(fecha.getMinutes())}:${dos(fecha.getSeconds())}${signo}${dos(desfase / 60)}:${dos(desfase % 60)}`;
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
    throw new ErrorApi(respuesta.status, detalle, datos && datos.detail);
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

/** «2026-10-31 06:15:05», la hora de este navegador para los CSV. */
export function fechaCsv(ms) {
  const f = new Date(ms);
  const dos = (n) => String(n).padStart(2, '0');
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())} ${dos(f.getHours())}:${dos(f.getMinutes())}:${dos(f.getSeconds())}`;
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
/** Cambia parámetros de la dirección sin recargar (null los saca). Con `nueva`, deja una entrada para el botón
    Atrás, como al abrir el detalle de una alerta. */
export function cambiarUrl(cambios, { nueva = false } = {}) {
  const p = new URLSearchParams(location.search);
  for (const [clave, valor] of Object.entries(cambios)) {
    if (valor === null || valor === undefined) p.delete(clave);
    else p.set(clave, valor);
  }
  const url = `${location.pathname}?${p.toString()}`;
  if (nueva) history.pushState(null, '', url);
  else history.replaceState(null, '', url);
}

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
  for (const hijo of hijos.flat(Infinity)) {
    if (hijo === null || hijo === undefined || hijo === false) continue;
    nodo.append(hijo instanceof Node ? hijo : document.createTextNode(String(hijo)));
  }
  return nodo;
}

export function vaciar(nodo, ...hijos) {
  nodo.replaceChildren(...hijos.flat(Infinity).filter((h) => h !== null && h !== undefined && h !== false));
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
  filtro: '<path d="M3 5h18"/><path d="M6 12h12"/><path d="M10 19h4"/>',
  persona: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  mas: '<path d="M12 5v14"/><path d="M5 12h14"/>',
  menos: '<path d="M5 12h14"/>',
  centrar: '<circle cx="12" cy="12" r="3"/><path d="M3 8V5a2 2 0 0 1 2-2h3"/><path d="M16 3h3a2 2 0 0 1 2 2v3"/><path d="M21 16v3a2 2 0 0 1-2 2h-3"/><path d="M8 21H5a2 2 0 0 1-2-2v-3"/>',
  enviar: '<path d="m22 2-7 20-4-9-9-4z"/><path d="M22 2 11 13"/>',
  salir: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/>',
  reloj: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  abajo: '<path d="m6 9 6 6 6-6"/>',
  izquierda: '<path d="m15 18-6-6 6-6"/>',
  derecha: '<path d="m9 18 6-6-6-6"/>',
  alejar: '<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/><path d="M8 11h6"/>',
  actualizar: '<path d="M21 12a9 9 0 0 1-15.5 6.2L3 16"/><path d="M3 21v-5h5"/><path d="M3 12a9 9 0 0 1 15.5-6.2L21 8"/><path d="M21 3v5h-5"/>',
  enlace: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
  menu: '<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>',
  ampliar: '<path d="M15 3h6v6"/><path d="M9 21H3v-6"/><path d="M21 3l-7 7"/><path d="M3 21l7-7"/>',
  reducir: '<path d="M4 14h6v6"/><path d="M20 10h-6V4"/><path d="m14 10 7-7"/><path d="m3 21 7-7"/>',
  tabla: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 10h18"/><path d="M9 10v10"/>',
  descargar: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m7 10 5 5 5-5"/><path d="M12 15V3"/>',
  imagen: '<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.1-3.1a2 2 0 0 0-2.8 0L6 21"/>',
  volver: '<path d="M19 12H5"/><path d="m11 18-6-6 6-6"/>',
  calendario: '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/>',
  compartir: '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4"/><path d="m15.4 6.5-6.8 4"/>',
  copiar: '<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 16v-4"/><path d="M12 8h.01"/>',
};
export function icono(nombre) {
  const plantilla = document.createElement('template');
  plantilla.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${TRAZOS[nombre] || ''}</svg>`;
  return plantilla.content.firstChild;
}
