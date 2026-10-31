// La vista En vivo: el estado del sistema, las alertas sin revisar, el tráfico de ahora, las conexiones por
// servicio con su leyenda en tabla, el volumen de tráfico y las conexiones sin respuesta, los tres con las
// alertas marcadas, y las últimas alertas, con un aviso cuando entra una nueva. El mapa está en mapa.js.

import {
  bitsPorSegundo, bytes, el, fechaHora, fechaHoraSegundos, haceCuanto, hora, horaConSegundos, icono, numero, porcentaje, tasa, vaciar,
} from './util.js';
import {
  GRUPOS, SEVERIDADES, colores, formatoBits, grupoDe, opcionesMini, opcionesSerieSimple, opcionesServicios,
} from './graficos.js';
import {
  $, ESTADOS, NIVEL, ORDEN_SEVERIDAD, ahoraServidor, chipEstado, chipSeveridad, dibujar, e, nombreEquipo, pedir,
  refrescarTablas, servicioDe, tipoCorto,
} from './comun.js';
import {
  claveRango, esRelativo, esperarArrastre, fraseRango, nombrePaso, pasoPara, prepararGraficoDeTiempo, rangoActual,
} from './tiempo.js';
import { abrirDetalle } from './alertas.js';

/** «2026-10-31 06:15:05», la hora de este navegador para los CSV. */
function fechaCsv(ms) {
  const f = new Date(ms);
  const dos = (n) => String(n).padStart(2, '0');
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())} ${dos(f.getHours())}:${dos(f.getMinutes())}:${dos(f.getSeconds())}`;
}

// ---- Estado del sistema y alertas sin revisar ----

export async function actualizarSalud() {
  const inicio = performance.now();
  let salud;
  try {
    salud = await pedir('/salud');
  } catch (falla) {
    anotarRespuesta(null);
    pintarEstado();
    throw falla;
  }
  anotarRespuesta(performance.now() - inicio);
  e.salud = salud;
  if (salud.hora) e.desfase = Date.parse(salud.hora) - Date.now();
  e.conteo = await pedir('/estado');
  pintarEstado();
  pintarGraficoServidor();
  pintarSinRevisar();
}

/** Cuánto tardó el servidor en contestar /salud (null si no contestó), para el gráfico de su pieza. */
function anotarRespuesta(ms) {
  const ahora = Date.now();
  e.respuestas.push({ t: ahora, ms: ms == null ? null : Math.round(ms) });
  while (e.respuestas.length && e.respuestas[0].t < ahora - 3600e3) e.respuestas.shift();
}

/** El tráfico del sensor y los latidos del análisis de la última hora, por minuto, para los gráficos chicos. */
export async function actualizarPiezas() {
  const hasta = Math.floor(ahoraServidor() / 60000) * 60 + 60;
  const desde = hasta - 3600;
  const [trafico, latidos] = await Promise.all([
    pedir(`/trafico?desde=${desde}&hasta=${hasta}&paso=60`),
    pedir(`/latidos?desde=${desde}&hasta=${hasta}`),
  ]);
  e.piezas = { desde, hasta, trafico, latidos };
  pintarGraficosPiezas();
}

// Como la tarjeta de la app (decisión 48): la luz verde si funciona, ámbar si el sensor ve parcialmente y
// la roja si no funciona o no hay datos; el semáforo no lleva palabras (las dice para el lector de pantalla).
const LUZ = { funciona: 'funciona', parcial: 'parcial', detenido: 'detenido', sin_datos: 'detenido', no_responde: 'detenido' };
const COLOR_PUNTO = { funciona: 'var(--baja)', parcial: 'var(--media)', detenido: 'var(--alta)' };
const NOMBRE_ESTADO = { funciona: 'Funciona', parcial: 'Viendo parcialmente', detenido: 'Detenido', sin_datos: 'Sin datos', no_responde: 'No responde' };

/** Las tres piezas: el punto, el semáforo y un dato corto con la misma forma en las tres. Se pinta cada
    segundo (el dato cuenta los segundos) y cada vez que contesta /salud. */
export function pintarEstado() {
  const sistema = e.salud && e.salud.sistema;
  const ahora = ahoraServidor();
  const piezas = {
    sensor: { estado: sistema ? sistema.sensor : 'sin_datos', dato: datoSensor(sistema, ahora) },
    analisis: { estado: sistema ? sistema.analisis : 'sin_datos', dato: datoAnalisis(sistema, ahora) },
    servidor: { estado: e.servidorCaido ? 'no_responde' : 'funciona', dato: datoServidor() },
  };
  for (const [id, pieza] of Object.entries(piezas)) {
    const luz = LUZ[pieza.estado] || 'detenido';
    const semaforo = $(`semaforo-${id}`);
    if (!semaforo) continue;
    semaforo.dataset.estado = luz;
    semaforo.setAttribute('aria-label', NOMBRE_ESTADO[pieza.estado] || 'Sin datos');
    $(`punto-${id}`).style.background = COLOR_PUNTO[luz];
    $(`dato-${id}`).textContent = pieza.dato;
  }
}

/** «Último tráfico hace 4 min» («Escuchando la red» si todavía no vio tráfico); «Pierde el 5,0 % del
    tráfico» si ve parcialmente; «No escucha la red» si Zeek no anda; «Sin datos» si no hay noticias. */
function datoSensor(sistema, ahora) {
  if (!sistema || sistema.sensor === 'sin_datos' || sistema.sensor === 'no_responde') return 'Sin datos';
  if (sistema.sensor === 'detenido') return 'No escucha la red';
  if (sistema.sensor === 'parcial' && sistema.perdidaCaptura != null) {
    const pierde = `Pierde el ${numero(sistema.perdidaCaptura, 1)} % del tráfico`;
    return sistema.perdidaSimulada ? `${pierde} (simulado)` : pierde;
  }
  if (!sistema.ultimoFlujo) return 'Escuchando la red';
  return `Último tráfico hace ${cuanto(ahora - Date.parse(sistema.ultimoFlujo))}`;
}

/** «Última revisión hace 12 s». */
function datoAnalisis(sistema, ahora) {
  if (!sistema || !sistema.ultimaCorrida) return 'Sin datos';
  return `Última revisión hace ${cuanto(ahora - Date.parse(sistema.ultimaCorrida))}`;
}

/** «Última respuesta hace 7 s», «Respondió recién» o «No responde». */
function datoServidor() {
  if (e.servidorCaido) return 'No responde';
  if (!e.ultimaRespuesta) return 'Sin datos';
  const hace = Date.now() - e.ultimaRespuesta;
  return hace < 3000 ? 'Respondió recién' : `Última respuesta hace ${cuanto(hace)}`;
}

/** Cuánto pasó, en una sola unidad para que el dato sea corto: 12 s, 24 min, 3 h, 2 días (como la app). */
function cuanto(ms) {
  const segundos = Math.max(0, Math.floor(ms / 1000));
  if (segundos < 60) return `${segundos} s`;
  if (segundos < 3600) return `${Math.floor(segundos / 60)} min`;
  if (segundos < 86400) return `${Math.floor(segundos / 3600)} h`;
  if (segundos < 2 * 86400) return '1 día';
  return `${Math.floor(segundos / 86400)} días`;
}

/** Los gráficos chicos del sensor y del análisis: conexiones y latidos de cada minuto de la última hora
    (un minuto sin latidos es un rato en que el análisis no anduvo). En grande, con sus ejes. */
export function pintarGraficosPiezas() {
  const p = e.piezas;
  const ejes = e.panelGrande === 'estado';
  if (p) {
    const c = colores();
    const minutos = Array.from({ length: (p.hasta - p.desde) / 60 }, (_, i) => p.desde + i * 60);
    const conexiones = new Map(p.trafico.total.map((x) => [x.t, x.conexiones]));
    const latidos = new Map(p.latidos.minutos.map((x) => [x.minuto, x.latidos]));
    const ventana = { desde: p.desde * 1000, hasta: p.hasta * 1000 };
    dibujar('pieza-sensor', opcionesMini(c, { ...ventana, puntos: minutos.map((t) => [t * 1000, conexiones.get(t) || 0]) },
      { tipo: 'barras', color: c['--serie-2'], nombre: 'conexiones', paso: 60, ejes }));
    const valores = minutos.map((t) => latidos.get(t) || 0);
    dibujar('pieza-analisis', opcionesMini(c, { ...ventana, puntos: minutos.map((t, i) => [t * 1000, valores[i]]) },
      { tipo: 'barras', color: c['--verde'], nombre: 'revisiones', paso: 60, maximo: Math.max(3, ...valores), ejes }));
    // Los datos del panel: cada minuto, con lo que tardó en promedio el servidor en contestar.
    const respuesta = (t) => {
      const medidas = e.respuestas.filter((r) => r.ms != null && r.t >= t * 1000 && r.t < (t + 60) * 1000).map((r) => r.ms);
      return medidas.length ? Math.round(medidas.reduce((a, b) => a + b, 0) / medidas.length) : null;
    };
    const filas = minutos.map((t) => [t, conexiones.get(t) || 0, latidos.get(t) || 0, respuesta(t)]);
    e.tablas.estado = {
      columnas: ['Minuto', 'Conexiones', 'Revisiones', 'Respuesta del servidor'],
      numericas: 3,
      filas: filas.slice().reverse().map(([t, n, l, r]) => [fechaHora(t * 1000), numero(n), numero(l), r == null ? '–' : `${numero(r)} ms`]),
      csv: { columnas: ['Minuto', 'Conexiones', 'Revisiones', 'Respuesta del servidor (ms)'], filas: filas.map(([t, n, l, r]) => [fechaCsv(t * 1000), n, l, r ?? '']) },
    };
    refrescarTablas('estado');
  }
  pintarGraficoServidor();
}

/** El gráfico chico del servidor: cuánto tardó en contestarle a este navegador, desde que se abrió. */
function pintarGraficoServidor() {
  const c = colores();
  const ahora = Date.now();
  dibujar('pieza-servidor', opcionesMini(c, { desde: ahora - 3600e3, hasta: ahora, puntos: e.respuestas.map((r) => [r.t, r.ms]) },
    { tipo: 'linea', color: c['--serie-1'], nombre: 'de respuesta', formato: (v) => `${numero(v)} ms`, ejes: e.panelGrande === 'estado' }));
}

export function pintarSinRevisar() {
  const conteo = (e.conteo && e.conteo.sinRevisar) || { alta: 0, media: 0, baja: 0 };
  vaciar($('sin-revisar'), SEVERIDADES.map((s) => el('div', { class: 'contador' },
    el('div', { class: 'contador-titulo' }, el('span', { class: 'punto', style: `background:var(${s.color})` }), s.nombre),
    el('div', { class: 'contador-numero' }, numero(conteo[s.id])))));
}

// ---- Tráfico ----

/** La serie que mandó el servidor, en una grilla pareja de tramos (los que faltan son ceros). */
function enGrilla(serie) {
  const paso = serie.paso;
  const n = Math.max(0, Math.round((serie.hasta - serie.desde) / paso));
  const indice = (t) => Math.round((t - serie.desde) / paso);
  const tiempos = Array.from({ length: n }, (_, i) => (serie.desde + i * paso) * 1000);
  const ceros = () => new Array(n).fill(0);
  const conexiones = ceros(); const fallidas = ceros(); const bytesT = ceros(); const paquetes = ceros();
  for (const p of serie.total) {
    const i = indice(p.t);
    if (i < 0 || i >= n) continue;
    conexiones[i] = p.conexiones; fallidas[i] = p.fallidas; bytesT[i] = p.bytes; paquetes[i] = p.paquetes;
  }
  const porGrupo = Object.fromEntries(GRUPOS.map((g) => [g.id, ceros()]));
  for (const [servicio, puntos] of Object.entries(serie.servicios)) {
    const grupo = porGrupo[grupoDe(servicio)];
    for (const p of puntos) {
      const i = indice(p.t);
      if (i >= 0 && i < n) grupo[i] += p.conexiones;
    }
  }
  return { paso, desde: serie.desde * 1000, hasta: serie.hasta * 1000, tiempos, conexiones, fallidas, bytes: bytesT, paquetes, porGrupo };
}

/** El tráfico del horario elegido, con el tramo de barra que le corresponde (tiempo.js). Si el rango
    termina ahora, el servidor pone el final (por si el reloj del sensor va adelantado). */
export async function actualizarTrafico() {
  const { desde, hasta } = rangoActual();
  const paso = pasoPara(hasta - desde);
  const pedido = claveRango();
  const fin = esRelativo() ? '' : `&hasta=${Math.ceil(hasta / 1000 / paso) * paso}`;
  const serie = await pedir(`/trafico?desde=${Math.floor(desde / 1000 / paso) * paso}${fin}&paso=${paso}`);
  if (pedido !== claveRango()) return;
  e.trafico = { rango: pedido, ...enGrilla(serie), ultimoTramo: serie.ultimoTramo };
  pintarTrafico();
}

export async function actualizarAhora() {
  const desde = Math.floor(ahoraServidor() / 1000 / 5) * 5 - 45;
  e.ahora = enGrilla(await pedir(`/trafico?desde=${desde}&paso=5`));
  pintarAhora();
}

function pintarAhora() {
  const g = e.ahora;
  if (!g) return;
  // Los seis tramos de 5 segundos antes del que está en curso: Zeek escribe cada conexión cuando termina.
  const fin = Math.max(0, Math.floor((ahoraServidor() - g.desde) / 5000));
  const desdeI = Math.max(0, fin - 6);
  let conexiones = 0; let bytesT = 0;
  for (let i = desdeI; i < fin && i < g.tiempos.length; i += 1) { conexiones += g.conexiones[i]; bytesT += g.bytes[i]; }
  const segundos = Math.max(1, (fin - desdeI) * 5);
  $('conexiones-ahora').textContent = tasa(conexiones / segundos);
  $('trafico-ahora').textContent = bitsPorSegundo((bytesT * 8) / segundos);
}

export function alertasEnVentana(desdeMs, hastaMs) {
  const lista = [];
  for (const a of e.alertas.values()) {
    const t = Date.parse(a.inicio);
    if (t >= desdeMs && t < hastaMs) lista.push({ ...a, t });
  }
  return lista;
}

/** Las alertas del horario como marcas de los gráficos de tiempo (graficos.js): hasta 200, las más graves primero. */
function marcasDe(g) {
  return alertasEnVentana(g.desde, g.hasta)
    .sort((a, b) => (ORDEN_SEVERIDAD.indexOf(a.severidad) - ORDEN_SEVERIDAD.indexOf(b.severidad)) || b.t - a.t)
    .slice(0, 200)
    .map((a) => ({
      id: a.id,
      t: a.t,
      severidad: a.severidad,
      nivel: NIVEL[a.severidad],
      tipo: tipoCorto(a),
      par: `${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')}`,
      servicio: servicioDe(a),
      estado: ESTADOS[a.estadoRevision],
    }));
}

// Las marcas que se dibujaron, para su globito.
let marcasPorId = new Map();

/** El globito de una marca va aparte del de ECharts, así se ve solo en el gráfico que tiene el mouse. */
function mostrarGlobitoMarca(p) {
  const m = marcasPorId.get(p.data && p.data.id);
  const ev = p.event && p.event.event;
  if (!m || !ev) return;
  const globito = $('globito-marca');
  vaciar(globito, el('div', { class: 'globito' },
    el('div', { class: 'globito-titulo' }, m.tipo),
    el('div', { class: 'globito-fila' }, el('i', { class: 'globito-llave punto', style: `background:var(--${m.severidad})` }), el('b', {}, m.nivel), el('em', {}, `empezó ${fechaHoraSegundos(m.t)}`)),
    el('div', { class: 'globito-nota' }, `${m.par} · ${m.servicio}`),
    el('div', { class: 'globito-nota' }, `${m.estado}. Tocá la marca para ver el detalle.`)));
  globito.hidden = false;
  const x = Math.min(window.innerWidth - globito.offsetWidth - 8, ev.clientX + 14);
  const arriba = ev.clientY - globito.offsetHeight - 14;
  globito.style.left = `${Math.max(8, x)}px`;
  globito.style.top = `${arriba < 8 ? ev.clientY + 18 : arriba}px`;
}

function ocultarGlobitoMarca() {
  $('globito-marca').hidden = true;
}

/** Pasar el mouse por la marca de una alerta la muestra; tocarla abre su detalle. */
function prepararMarcas(grafico) {
  if (grafico.zondaMarcas) return;
  grafico.zondaMarcas = true;
  // Con la página corriéndose, el globito quedaría lejos de su marca (la misma función se anota una sola vez).
  window.addEventListener('scroll', ocultarGlobitoMarca, { passive: true });
  // Se mira la serie en cada evento en lugar de filtrar con ECharts (ver prepararMapa en mapa.js).
  const esMarca = (p) => p.seriesId === 'marcas' && p.data && p.data.id;
  grafico.on('mouseover', (p) => { if (esMarca(p)) mostrarGlobitoMarca(p); });
  grafico.on('mouseout', (p) => { if (esMarca(p)) ocultarGlobitoMarca(); });
  grafico.on('globalout', ocultarGlobitoMarca);
  grafico.on('click', (p) => {
    if (!esMarca(p)) return;
    ocultarGlobitoMarca();
    abrirDetalle(p.data.id);
  });
}

export function pintarTrafico() {
  const g = e.trafico;
  if (!g || esperarArrastre(pintarTrafico)) return;
  const c = colores();
  const frase = fraseRango();
  $('nota-paso').textContent = `Cada barra: ${nombrePaso(g.paso)}`;

  // Los tres gráficos de tiempo llevan las mismas marcas de alertas. En el de servicios van los que tuvieron
  // conexiones en el horario y, de esos, los que deja ver la leyenda. Las conexiones y las sin respuesta van
  // contadas por barra (en una red tranquila, 34 conexiones en una hora dan 0,009 por segundo: contadas se
  // leen); el tráfico, en bits por segundo.
  const marcas = marcasDe(g);
  marcasPorId = new Map(marcas.map((m) => [m.id, m]));
  const presentes = GRUPOS.filter((x) => g.porGrupo[x.id].some((v) => v > 0)).map((x) => x.id);
  let visibles = presentes.filter((id) => !e.serviciosVisibles || e.serviciosVisibles.has(id));
  if (!visibles.length) {
    e.serviciosVisibles = null;
    visibles = presentes;
  }
  const bits = g.bytes.map((b) => (b * 8) / g.paso);
  const comunes = { tiempos: g.tiempos, paso: g.paso, desde: g.desde, hasta: g.hasta, marcas };
  const graficos = [
    dibujar('servicios', opcionesServicios(c, { ...comunes, porGrupo: g.porGrupo, conexiones: g.conexiones, grupos: visibles })),
    dibujar('volumen', opcionesSerieSimple(c, { ...comunes, valores: bits }, { tipo: 'linea', formato: formatoBits, nombre: 'de tráfico' })),
    dibujar('fallidas', opcionesSerieSimple(c, { ...comunes, valores: g.fallidas }, { tipo: 'barras', formato: (v) => numero(v), nombre: 'sin respuesta' })),
  ];
  for (const grafico of graficos) {
    if (!grafico) continue;
    prepararGraficoDeTiempo(grafico);
    prepararMarcas(grafico);
  }
  pintarLeyendaServicios(g, presentes);

  const total = g.conexiones.reduce((a, b) => a + b, 0);
  const fallidas = g.fallidas.reduce((a, b) => a + b, 0);
  const bytesTotal = g.bytes.reduce((a, b) => a + b, 0);
  $('sin-respuesta').textContent = numero(fallidas);
  $('sin-respuesta-detalle').textContent = total ? `${porcentaje(fallidas, total)} de ${numero(total)} conexiones ${frase}` : `Ninguna conexión ${frase}`;
  $('trafico-ahora-detalle').textContent = `${bytes(bytesTotal)} ${frase}`;
  $('conexiones-ahora-detalle').textContent = `Promedio de los últimos 30 segundos. ${numero(total)} conexiones ${frase}.`;

  const aviso = g.ultimoTramo == null ? 'Todavía no llegó tráfico del sensor. El sensor lo manda cada 5 segundos.'
    : `No hubo tráfico ${frase}. Lo último que vio el sensor fue a las ${fechaHora(g.ultimoTramo * 1000)}.`;
  for (const id of ['vacio-servicios', 'vacio-volumen']) {
    $(id).hidden = total > 0 || bytesTotal > 0;
    $(id).textContent = aviso;
  }

  // Los datos de los tres gráficos: en la tabla, las barras con tráfico, de la más nueva a la más vieja; en
  // el CSV, todas las barras en orden y sin unidades.
  const filas = [];
  for (let i = g.tiempos.length - 1; i >= 0; i -= 1) if (g.conexiones[i] || g.bytes[i]) filas.push(i);
  const todas = g.tiempos.map((_, i) => i);
  const columnasServicios = ['Desde', 'Conexiones', ...GRUPOS.map((x) => x.nombre)];
  e.tablas.servicios = {
    columnas: columnasServicios,
    numericas: 1 + GRUPOS.length,
    filas: filas.map((i) => [horaTramo(g.tiempos[i], g.paso), numero(g.conexiones[i]), ...GRUPOS.map((x) => numero(g.porGrupo[x.id][i]))]),
    csv: { columnas: columnasServicios, filas: todas.map((i) => [fechaCsv(g.tiempos[i]), g.conexiones[i], ...GRUPOS.map((x) => g.porGrupo[x.id][i])]) },
  };
  e.tablas.volumen = {
    columnas: ['Desde', 'Bits por segundo', 'Bytes'],
    numericas: 2,
    filas: filas.map((i) => [horaTramo(g.tiempos[i], g.paso), bitsPorSegundo(bits[i]), bytes(g.bytes[i])]),
    csv: { columnas: ['Desde', 'Bits por segundo', 'Bytes'], filas: todas.map((i) => [fechaCsv(g.tiempos[i]), Math.round(bits[i]), g.bytes[i]]) },
  };
  e.tablas.fallidas = {
    columnas: ['Desde', 'Sin respuesta'],
    numericas: 1,
    filas: filas.filter((i) => g.fallidas[i]).map((i) => [horaTramo(g.tiempos[i], g.paso), numero(g.fallidas[i])]),
    csv: { columnas: ['Desde', 'Sin respuesta'], filas: todas.map((i) => [fechaCsv(g.tiempos[i]), g.fallidas[i]]) },
  };
  refrescarTablas('servicios', 'volumen', 'fallidas');
}

function horaTramo(ms, paso) {
  return paso >= 60 ? fechaHora(ms) : horaConSegundos(ms);
}

// ---- La leyenda de las conexiones por servicio ----

/** Como la leyenda de Grafana en modo tabla: cada servicio con el mínimo, el máximo, el promedio y el último
    valor de sus barras, sin la que todavía no terminó. Tocar uno deja solo ese y tocarlo de nuevo vuelve a
    todos; con Ctrl (o ⌘), se suma o se saca. */
function pintarLeyendaServicios(g, presentes) {
  const tabla = $('leyenda-servicios');
  tabla.hidden = !presentes.length;
  let fin = g.tiempos.length;
  if (fin > 1 && g.tiempos[fin - 1] + g.paso * 1000 > ahoraServidor()) fin -= 1;
  const filas = GRUPOS.filter((x) => presentes.includes(x.id)).map((x) => {
    const valores = g.porGrupo[x.id].slice(0, fin);
    const promedio = valores.reduce((s, v) => s + v, 0) / Math.max(1, valores.length);
    const visible = !e.serviciosVisibles || e.serviciosVisibles.has(x.id);
    return el('tr', { class: visible ? null : 'oculta' },
      el('th', { scope: 'row' }, el('button', {
        type: 'button',
        'aria-pressed': String(visible),
        title: 'Tocá para ver solo este. Con Ctrl, sumalo o sacalo.',
        onclick: (ev) => tocarServicio(x.id, presentes, ev.ctrlKey || ev.metaKey),
      }, el('i', { style: `background:var(${x.color})` }), x.nombre)),
      el('td', { class: 'num' }, numero(Math.min(...valores))),
      el('td', { class: 'num' }, numero(Math.max(...valores))),
      el('td', { class: 'num' }, tasa(promedio)),
      el('td', { class: 'num' }, numero(valores[valores.length - 1])));
  });
  vaciar(tabla,
    el('thead', {}, el('tr', {}, el('th', { scope: 'col' }, 'Servicio'), ['Mín', 'Máx', 'Promedio', 'Último'].map((texto) => el('th', { scope: 'col', class: 'num' }, texto)))),
    el('tbody', {}, filas));
}

function tocarServicio(id, presentes, sumar) {
  let visibles = new Set(presentes.filter((x) => !e.serviciosVisibles || e.serviciosVisibles.has(x)));
  if (!sumar) visibles = visibles.size === 1 && visibles.has(id) ? new Set(presentes) : new Set([id]);
  else if (!visibles.has(id)) visibles.add(id);
  else if (visibles.size > 1) visibles.delete(id);
  e.serviciosVisibles = presentes.every((x) => visibles.has(x)) ? null : visibles;
  pintarTrafico();
}

export function pintarLeyendas() {
  vaciar($('leyenda-marcas'), el('span', {}, 'Alertas:'),
    SEVERIDADES.map((s) => el('span', {}, el('i', { class: 'triangulo', style: `border-bottom-color:var(${s.color})` }), s.nombre)));
  vaciar($('leyenda-por-dia'), SEVERIDADES.map((s) => el('span', {}, el('i', { style: `background:var(${s.color})` }), s.nombre)));
}

// ---- Últimas alertas y el aviso de una nueva ----

export function pintarUltimas() {
  const lista = [...e.alertas.values()].sort((a, b) => Date.parse(b.inicio) - Date.parse(a.inicio)).slice(0, 8);
  const ahora = ahoraServidor();
  vaciar($('ultimas-alertas'), lista.length ? lista.map((a) => el('li', {},
    el('button', { type: 'button', class: e.nuevas.has(a.id) ? 'nueva' : null, onclick: () => abrirDetalle(a.id) },
      chipSeveridad(a.severidad),
      el('span', { class: 'feed-tipo' }, tipoCorto(a)),
      el('span', { class: 'feed-par' }, `${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')} · ${(a.servicio || '').toUpperCase()}`),
      el('span', { class: 'feed-hora', title: fechaHora(Date.parse(a.inicio)) }, haceCuanto(Date.parse(a.inicio), ahora)),
      el('span', { class: 'feed-estado' }, chipEstado(a.estadoRevision)))))
    : el('li', { class: 'nota' }, 'Todavía no hay alertas.'));
}

let relojAviso = null;

export function avisarNuevas(nuevas) {
  const recientes = nuevas.filter((a) => ahoraServidor() - Date.parse(a.creadaEn) < 15 * 60e3);
  if (!recientes.length) return;
  // La más grave y, entre las igual de graves, la que empezó último.
  const a = recientes.sort((x, y) => (ORDEN_SEVERIDAD.indexOf(x.severidad) - ORDEN_SEVERIDAD.indexOf(y.severidad))
    || Date.parse(y.inicio) - Date.parse(x.inicio))[0];
  const aviso = $('aviso');
  aviso.className = `aviso ${a.severidad}`;
  vaciar(aviso,
    chipSeveridad(a.severidad),
    el('div', { class: 'aviso-texto' },
      el('strong', {}, `Alerta nueva: ${tipoCorto(a)}`),
      el('span', {}, `${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')} · ${servicioDe(a)} · ${hora(Date.parse(a.inicio))}${recientes.length > 1 ? ` · y ${recientes.length - 1} más` : ''}`)),
    el('button', { type: 'button', class: 'boton-secundario', onclick: () => { aviso.hidden = true; abrirDetalle(a.id); } }, 'Ver detalle'),
    el('button', { type: 'button', class: 'boton-icono', 'aria-label': 'Cerrar el aviso', onclick: () => { aviso.hidden = true; } }, icono('cruz')));
  aviso.hidden = false;
  clearTimeout(relojAviso);
  relojAviso = setTimeout(() => { aviso.hidden = true; }, 20000);
}
