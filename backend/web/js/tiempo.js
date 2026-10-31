// El horario del tablero, como la barra de tiempo de Grafana: rangos rápidos («Últimos 15 minutos»), un
// rango a medida con desde y hasta, correrlo para atrás o para adelante, alejarlo, actualizar ahora o cada
// tanto y copiar el enlace. El horario va en la dirección de la página (?desde=now-15m&hasta=now): al
// recargar o al compartir el enlace se ve lo mismo. Las expresiones son las de Grafana: «now», «now-15m»,
// «now-1d/d» (el día entero) o una hora en milisegundos desde 1970; «todo» es desde el principio. El selector
// del horario sirve también para la lista de alertas, con sus propios rangos (alertas.js).

import { dia, el, hora, horaConSegundos, icono, vaciar } from './util.js';
import { $, ahoraServidor, e } from './comun.js';
import { abrirMenu, cerrarMenu } from './menu.js';
import { avisoInferior } from './hojas.js';

// Los pasos que acepta GET /trafico (trafico.PASOS del servidor) y cuántas barras tiene como mucho un gráfico.
const PASOS = [5, 10, 15, 30, 60, 300, 900, 3600];
const BARRAS = 300;
// El servidor guarda una semana de tráfico (ZONDA_DIAS_TRAFICO).
const SEMANA = 7 * 86400e3;
const UNIDAD = { s: 1e3, m: 60e3, h: 3600e3, d: 86400e3 };

const rangos = (lista) => lista.map(([desde, hasta]) => ({ desde, hasta }));

export const RANGOS_RAPIDOS = rangos([
  ['now-5m', 'now'], ['now-15m', 'now'], ['now-30m', 'now'], ['now-1h', 'now'], ['now-3h', 'now'],
  ['now-6h', 'now'], ['now-12h', 'now'], ['now-24h', 'now'], ['now-2d', 'now'], ['now-7d', 'now'],
  ['now/d', 'now'], ['now-1d/d', 'now-1d/d'], ['now-2d/d', 'now-2d/d'],
]);

/** Los de la lista de alertas, que se guardan más que el tráfico. */
export const RANGOS_ALERTAS = rangos([
  ['now-1h', 'now'], ['now-24h', 'now'], ['now/d', 'now'], ['now-1d/d', 'now-1d/d'],
  ['now-7d', 'now'], ['now-30d', 'now'], ['now-90d', 'now'], ['todo', 'now'],
]);

// Los rangos con nombre propio: el nombre en la barra y cómo se dice en una oración.
const DIAS = {
  'now/d|now': ['Hoy hasta ahora', 'hoy'],
  'now-1d/d|now-1d/d': ['Ayer', 'ayer'],
  'now-2d/d|now-2d/d': ['Anteayer', 'anteayer'],
  'todo|now': ['Todo', 'desde el principio'],
};

export const ACTUALIZAR = [
  { ms: 0, valor: 'apagada', texto: 'Apagada' },
  { ms: 5000, valor: '5s', texto: '5 s' },
  { ms: 10000, valor: '10s', texto: '10 s' },
  { ms: 30000, valor: '30s', texto: '30 s' },
  { ms: 60000, valor: '1m', texto: '1 min' },
  { ms: 300000, valor: '5m', texto: '5 min' },
];

const NOMBRE_PASO = {
  5: '5 segundos', 10: '10 segundos', 15: '15 segundos', 30: '30 segundos', 60: '1 minuto', 300: '5 minutos', 900: '15 minutos',
  1800: '30 minutos', 3600: '1 hora', 10800: '3 horas', 21600: '6 horas', 43200: '12 horas', 86400: '1 día',
};

let alCambiar = () => {};
let alActualizar = () => {};

// ---- Las expresiones y el rango ----

/** El momento de una expresión, en milisegundos; con «/d», redondeado al día (al principio en el «desde» y
    al final en el «hasta»). null si no se entiende. */
export function momento(expresion, ahora, esHasta = false) {
  if (expresion === 'todo') return 0;
  if (/^\d{10,}$/.test(expresion || '')) return Number(expresion);
  const partes = /^now(?:-(\d+)([smhd]))?(\/d)?$/.exec(expresion || '');
  if (!partes) return null;
  let t = ahora - (partes[1] ? Number(partes[1]) * UNIDAD[partes[2]] : 0);
  if (partes[3]) {
    const fecha = new Date(t);
    fecha.setHours(0, 0, 0, 0);
    if (esHasta) fecha.setDate(fecha.getDate() + 1);
    t = fecha.getTime();
  }
  return t;
}

/** El rango en milisegundos, con la hora del servidor. */
export function rangoActual(rango = e.rango) {
  const ahora = ahoraServidor();
  return { desde: momento(rango.desde, ahora), hasta: momento(rango.hasta, ahora, true) };
}

/** Si el rango termina ahora: se corre solo con el reloj. */
export const esRelativo = (rango = e.rango) => rango.hasta === 'now';

/** Para saber, después de un pedido, si mientras tanto cambió el horario. */
export const claveRango = (rango = e.rango) => `${rango.desde}|${rango.hasta}`;

/** El tramo de cada barra: el más chico con el que el gráfico no pasa de 300 barras. */
export function pasoPara(ms) {
  return PASOS.find((p) => ms / 1000 / p <= BARRAS) || PASOS[PASOS.length - 1];
}

export const nombrePaso = (paso) => NOMBRE_PASO[paso] || `${paso} segundos`;

/** «now-15m» para 900 segundos, con la unidad más grande que los divide (días, desde dos). */
function expresionRelativa(segundos) {
  if (segundos >= 2 * 86400 && segundos % 86400 === 0) return `now-${segundos / 86400}d`;
  if (segundos % 3600 === 0) return `now-${segundos / 3600}h`;
  if (segundos % 60 === 0) return `now-${segundos / 60}m`;
  return `now-${segundos}s`;
}

/** [«Últimos 15 minutos», «en los últimos 15 minutos»] para un rango que termina ahora. */
function nombresRelativos(segundos) {
  if (segundos === 3600) return ['Última hora', 'en la última hora'];
  if (segundos === 60) return ['Último minuto', 'en el último minuto'];
  if (segundos >= 2 * 86400 && segundos % 86400 === 0) return [`Últimos ${segundos / 86400} días`, `en los últimos ${segundos / 86400} días`];
  if (segundos % 3600 === 0) return [`Últimas ${segundos / 3600} horas`, `en las últimas ${segundos / 3600} horas`];
  if (segundos % 60 === 0) return [`Últimos ${segundos / 60} minutos`, `en los últimos ${segundos / 60} minutos`];
  return [`Últimos ${segundos} segundos`, `en los últimos ${segundos} segundos`];
}

function nombres(rango) {
  const propio = DIAS[claveRango(rango)];
  if (propio) return propio;
  const { desde, hasta } = rangoActual(rango);
  if (esRelativo(rango) && /^now-\d+[smhd]$/.test(rango.desde)) return nombresRelativos(Math.round((hasta - desde) / 1000));
  return [textoDesdeHasta(desde, hasta), 'en ese rango'];
}

/** «31/10 06:00 a 07:30», con segundos si el rango dura menos de una hora. */
function textoDesdeHasta(desde, hasta) {
  const corto = hasta - desde < 3600e3 ? horaConSegundos : hora;
  const mismoDia = new Date(desde).toDateString() === new Date(hasta - 1).toDateString();
  return `${dia(desde)} ${corto(desde)} a ${mismoDia ? corto(hasta) : `${dia(hasta)} ${corto(hasta)}`}`;
}

/** Cómo se llama el rango en la barra: «Últimos 15 minutos», «Ayer» o «31/10 06:00 a 07:30». */
export const nombreRango = (rango = e.rango) => nombres(rango)[0];

/** Cómo se dice en una oración: «en los últimos 15 minutos», «ayer» o «en ese rango». */
export const fraseRango = (rango = e.rango) => nombres(rango)[1];

/** Las horas de un rango para el globito de su botón («31/10 06:00 a 07:30»); null para «Todo». */
export function horasDe(rango) {
  if (rango.desde === 'todo') return null;
  const { desde, hasta } = rangoActual(rango);
  return textoDesdeHasta(desde, Math.min(hasta, ahoraServidor()));
}

/** Cada cuánto se piden los datos de nuevo (Infinity si la actualización está apagada). */
export const cadaCuanto = () => e.actualizar || Infinity;

// ---- Mover un rango (sirve para cualquier barra) ----

/** El rango corrido la mitad de su largo, como Grafana; si llega a ahora, vuelve a correrse solo. null si
    no se puede correr («Todo», o un rango que ya termina ahora hacia adelante). */
export function rangoCorrido(rango, sentido) {
  if (rango.desde === 'todo' || (sentido > 0 && esRelativo(rango))) return null;
  const { desde, hasta } = rangoActual(rango);
  const largo = hasta - desde;
  const nuevoHasta = hasta + (sentido * largo) / 2;
  if (nuevoHasta >= ahoraServidor()) return { desde: expresionRelativa(Math.round(largo / 1000)), hasta: 'now' };
  return { desde: String(Math.round(desde + (sentido * largo) / 2)), hasta: String(Math.round(nuevoHasta)) };
}

// ---- Cambiar el horario del tablero ----

export function ponerRango(rango) {
  if (!rango || claveRango(rango) === claveRango()) return;
  e.rango = rango;
  escribirUrl();
  pintarBarra();
  alCambiar();
}

/** Alejar: el doble de tiempo alrededor del centro, hasta la semana que guarda el servidor. */
function alejar() {
  const { desde, hasta } = rangoActual();
  const largo = Math.min(SEMANA, (hasta - desde) * 2);
  const centro = (desde + hasta) / 2;
  if (esRelativo() || centro + largo / 2 >= ahoraServidor()) {
    ponerRango({ desde: expresionRelativa(Math.round(largo / 1000)), hasta: 'now' });
    return;
  }
  ponerRango({ desde: String(Math.round(centro - largo / 2)), hasta: String(Math.round(centro + largo / 2)) });
}

/** Acercar al tramo que se marcó arrastrando sobre un gráfico; menos de 10 segundos es un clic. */
function acercar(t1, t2) {
  const desde = Math.round(Math.min(t1, t2) / 1000) * 1000;
  const hasta = Math.round(Math.max(t1, t2) / 1000) * 1000;
  if (hasta - desde >= 10000) ponerRango({ desde: String(desde), hasta: String(hasta) });
}

// ---- La dirección de la página ----

function parametros() {
  const p = new URLSearchParams(location.search);
  p.set('desde', e.rango.desde);
  p.set('hasta', e.rango.hasta);
  p.set('actualizar', (ACTUALIZAR.find((a) => a.ms === e.actualizar) || ACTUALIZAR[1]).valor);
  if (e.panelGrande) p.set('ver', e.panelGrande); else p.delete('ver');
  return p;
}

export function escribirUrl() {
  history.replaceState(null, '', `${location.pathname}?${parametros().toString()}`);
}

/** El enlace con el horario de ahora y, si se pide, el panel que abre en grande. */
export function enlaceCon({ ver } = {}) {
  const p = parametros();
  if (ver) p.set('ver', ver);
  return `${location.origin}${location.pathname}?${p.toString()}`;
}

/** Lee el horario de la dirección; devuelve el panel que hay que abrir en grande, si hay uno. */
export function leerUrl() {
  const p = new URLSearchParams(location.search);
  const desde = p.get('desde');
  const hasta = p.get('hasta');
  const ahora = Date.now();
  const d = desde === 'todo' ? null : momento(desde, ahora);
  const h = momento(hasta, ahora, true);
  if (d !== null && h !== null && d < h) e.rango = { desde, hasta };
  const cada = ACTUALIZAR.find((a) => a.valor === p.get('actualizar'));
  if (cada) e.actualizar = cada.ms;
  return p.get('ver');
}

// ---- Los gráficos de tiempo ----

let arrastrando = false;
let alSoltar = null;

/** Mientras se arrastra para acercar, no se repinta: el gráfico no se mueve debajo del mouse. */
export function esperarArrastre(repintar) {
  if (!arrastrando) return false;
  alSoltar = repintar;
  return true;
}

/** Arrastrar sobre un gráfico de tiempo acerca todo el tablero a ese tramo, como en Grafana. Los gráficos
    de tiempo comparten el cursor: al pasar el mouse por uno, se ve la misma hora en los otros. */
export function prepararGraficoDeTiempo(grafico) {
  if (!grafico.zondaPincel) {
    grafico.zondaPincel = true;
    grafico.dispatchAction({ type: 'takeGlobalCursor', key: 'brush', brushOption: { brushType: 'lineX', brushMode: 'single' } });
  }
  if (grafico.zondaTiempo) return;
  grafico.zondaTiempo = true;
  grafico.group = 'tiempo';
  window.echarts.connect('tiempo');
  grafico.on('brushEnd', (p) => {
    const area = p.areas && p.areas[0];
    grafico.dispatchAction({ type: 'brush', areas: [] });
    if (area && area.coordRange) acercar(area.coordRange[0], area.coordRange[1]);
  });
  const zr = grafico.getZr();
  zr.on('mousedown', () => { arrastrando = true; });
  const soltar = () => {
    if (!arrastrando) return;
    arrastrando = false;
    const repintar = alSoltar;
    alSoltar = null;
    if (repintar) setTimeout(repintar, 0);
  };
  zr.on('mouseup', soltar);
  zr.on('globalout', soltar);
}

// ---- El selector del horario, como el de Grafana ----

/** «2026-10-31T06:15:00» para un campo de fecha y hora, en la hora de este navegador. */
function valorLocal(ms) {
  const f = new Date(ms);
  const dos = (n) => String(n).padStart(2, '0');
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}T${dos(f.getHours())}:${dos(f.getMinutes())}:${dos(f.getSeconds())}`;
}

function zonaHoraria() {
  const minutos = -new Date().getTimezoneOffset();
  const dos = (n) => String(Math.floor(Math.abs(n))).padStart(2, '0');
  return `UTC${minutos >= 0 ? '+' : '−'}${dos(minutos / 60)}:${dos(minutos % 60)}`;
}

// Los selectores abiertos se cierran con Esc o al tocar afuera, los de las dos vistas.
const selectores = [];

/** Cierra el selector del horario que esté abierto; devuelve si había uno. */
export function cerrarSelector(enfocar = false) {
  return selectores.some((s) => s.cerrar(enfocar));
}

/**
 * Un selector de horario, como el de Grafana: a la izquierda, el rango a medida con desde y hasta (día y
 * hora, con calendario) y los usados hace poco; a la derecha, los rangos rápidos con un buscador.
 * selector: el recuadro que se abre; boton: el que lo abre; rapidos: los rangos de la lista; limite: cuánto
 * para atrás se puede elegir (ms, o null sin límite); clave: dónde recuerda este navegador los usados;
 * nota: lo que dice el pie; actual(): el rango de ahora; alElegir(rango): lo aplica.
 */
export function selectorDeHorario({ selector, boton, rapidos, limite = null, clave, nota = '', actual, alElegir }) {
  const leerRecientes = () => {
    try {
      const lista = JSON.parse(localStorage.getItem(clave) || '[]');
      return Array.isArray(lista) ? lista.filter((r) => r && momento(r.desde, 0) !== null && momento(r.hasta, 0) !== null).slice(0, 4) : [];
    } catch (_) {
      return [];
    }
  };
  const anotarReciente = (rango) => {
    const lista = [rango, ...leerRecientes().filter((r) => claveRango(r) !== claveRango(rango))].slice(0, 4);
    try { localStorage.setItem(clave, JSON.stringify(lista)); } catch (_) { /* solo por esta vez */ }
  };
  let campos = null;

  function cerrar(enfocar = false) {
    if (selector.hidden) return false;
    selector.hidden = true;
    boton.setAttribute('aria-expanded', 'false');
    if (enfocar) boton.focus();
    return true;
  }

  function elegir(rango) {
    cerrar();
    alElegir(rango);
  }

  function aplicarAMedida() {
    const desde = Date.parse(campos.desde.value);
    const hasta = Date.parse(campos.hasta.value);
    const minimo = limite ? ahoraServidor() - limite : null;
    const problema = Number.isNaN(desde) || Number.isNaN(hasta) ? 'Completá las dos fechas.'
      : desde >= hasta ? '«Desde» tiene que ser antes que «Hasta».'
        : minimo !== null && desde < minimo ? `El servidor guarda una semana: «Desde» puede ir hasta el ${dia(minimo)} a las ${hora(minimo)}.`
          : null;
    campos.error.hidden = !problema;
    campos.error.textContent = problema || '';
    if (problema) return;
    const rango = { desde: String(desde), hasta: String(hasta) };
    anotarReciente(rango);
    elegir(rango);
  }

  function botonRango(rango) {
    return el('li', {}, el('button', {
      type: 'button',
      'aria-current': claveRango(rango) === claveRango(actual()) ? 'true' : null,
      onclick: () => elegir(rango),
    }, nombreRango(rango)));
  }

  function abrir() {
    cerrarSelector();
    cerrarMenu();
    const ahora = ahoraServidor();
    const elegido = actual();
    const { hasta } = rangoActual(elegido);
    const desde = elegido.desde === 'todo' ? ahora - 30 * 86400e3 : rangoActual(elegido).desde;
    const campo = (texto, valor) => el('input', {
      type: 'datetime-local', step: '1', value: valorLocal(valor), min: limite ? valorLocal(ahora - limite) : null, max: valorLocal(ahora), 'aria-label': texto,
    });
    campos = { desde: campo('Desde', desde), hasta: campo('Hasta', Math.min(hasta, ahora)), error: el('p', { class: 'selector-error', role: 'alert', hidden: true }) };
    const recientes = leerRecientes();
    const lista = el('ul', { class: 'lista-rangos' }, rapidos.map(botonRango));
    const buscar = el('input', {
      type: 'search',
      class: 'buscar-rango',
      placeholder: 'Buscar un rango',
      'aria-label': 'Buscar un rango rápido',
      oninput: (ev) => {
        const texto = ev.target.value.trim().toLowerCase();
        for (const item of lista.children) item.hidden = Boolean(texto) && !item.textContent.toLowerCase().includes(texto);
      },
    });
    vaciar(selector,
      el('div', { class: 'selector-columna' },
        el('h3', {}, 'Rango a medida'),
        el('label', { class: 'campo' }, el('span', {}, 'Desde'), campos.desde),
        el('label', { class: 'campo' }, el('span', {}, 'Hasta'), campos.hasta),
        campos.error,
        el('button', { type: 'button', class: 'boton-principal chico', onclick: aplicarAMedida }, 'Aplicar'),
        el('h3', {}, 'Usados hace poco'),
        recientes.length ? el('ul', { class: 'lista-rangos' }, recientes.map(botonRango))
          : el('p', { class: 'nota' }, 'Acá aparecen los rangos a medida que uses.')),
      el('div', { class: 'selector-columna' }, buscar, lista),
      el('div', { class: 'selector-pie' }, `Hora de este navegador (${zonaHoraria()}).${nota ? ` ${nota}` : ''}`));
    selector.hidden = false;
    boton.setAttribute('aria-expanded', 'true');
    (lista.querySelector('[aria-current="true"]') || buscar).focus();
  }

  boton.addEventListener('click', () => { if (selector.hidden) abrir(); else cerrar(); });
  selector.addEventListener('keydown', (ev) => {
    if (ev.key === 'Enter' && ev.target.matches('input[type="datetime-local"]')) aplicarAMedida();
  });
  document.addEventListener('pointerdown', (ev) => {
    if (!selector.hidden && !selector.contains(ev.target) && !boton.contains(ev.target)) cerrar();
  });
  const instancia = { cerrar };
  selectores.push(instancia);
  return instancia;
}

// ---- La barra del horario de En vivo ----

export function pintarBarra() {
  $('tiempo-texto').textContent = nombreRango();
  $('tiempo-rango').title = horasDe(e.rango) || '';
  $('tiempo-adelante').disabled = esRelativo();
  const cada = ACTUALIZAR.find((a) => a.ms === e.actualizar) || ACTUALIZAR[1];
  $('actualizar-texto').textContent = cada.ms ? cada.texto : 'Apagada';
  $('actualizar-cada').classList.toggle('apagada', !cada.ms);
}

function elegirActualizacion() {
  abrirMenu($('actualizar-cada'), ACTUALIZAR.map((a) => ({
    texto: a.ms ? `Cada ${a.texto}` : 'Apagada',
    marcado: a.ms === e.actualizar,
    accion: () => {
      e.actualizar = a.ms;
      escribirUrl();
      pintarBarra();
    },
  })));
}

async function copiarEnlace() {
  try {
    await navigator.clipboard.writeText(enlaceCon());
    avisoInferior('Enlace copiado, con este horario');
  } catch (_) {
    avisoInferior('No se pudo copiar el enlace');
  }
}

/** alCambiarRango: volver a pedir los datos del rango nuevo; actualizarAhora: el botón Actualizar. */
export function configurarTiempo({ alCambiarRango, actualizarAhora }) {
  alCambiar = alCambiarRango;
  alActualizar = actualizarAhora;
  for (const lugar of document.querySelectorAll('#barra-tiempo [data-icono]')) lugar.prepend(icono(lugar.dataset.icono));
  selectorDeHorario({
    selector: $('selector-tiempo'),
    boton: $('tiempo-rango'),
    rapidos: RANGOS_RAPIDOS,
    limite: SEMANA,
    clave: 'zonda.tablero.rangos',
    nota: 'El servidor guarda una semana de tráfico.',
    actual: () => e.rango,
    alElegir: ponerRango,
  });
  $('tiempo-atras').addEventListener('click', () => ponerRango(rangoCorrido(e.rango, -1)));
  $('tiempo-adelante').addEventListener('click', () => ponerRango(rangoCorrido(e.rango, 1)));
  $('tiempo-alejar').addEventListener('click', alejar);
  $('boton-actualizar').addEventListener('click', () => alActualizar());
  $('actualizar-cada').addEventListener('click', elegirActualizacion);
  $('copiar-enlace').addEventListener('click', copiarEnlace);
  pintarBarra();
}
