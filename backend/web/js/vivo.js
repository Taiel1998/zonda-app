// La vista En vivo: el estado del sistema, las alertas sin revisar, el tráfico de ahora, las conexiones por
// servicio con su leyenda en tabla, el volumen de tráfico y las conexiones sin respuesta, los tres con las
// alertas marcadas, y las últimas alertas, con un aviso cuando entra una nueva. El mapa está en mapa.js.

import {
  bitsPorSegundo, bytes, dia, el, fechaCsv, fechaHora, fechaHoraSegundos, haceCuanto, hora, horaConSegundos, icono, numero, porcentaje, tasa, vaciar,
} from './util.js';
import {
  GRUPOS, SEVERIDADES, colores, formatoBits, grupoDe, opcionesAlertasTiempo, opcionesMini, opcionesSerieSimple, opcionesServicios,
} from './graficos.js';
import {
  $, ESTADOS, NIVEL, ORDEN_SEVERIDAD, ahoraServidor, chipEstado, chipSeveridad, dibujar, e, nombreEquipo, pedir,
  refrescarTablas, servicioDe, tipoCorto,
} from './comun.js';
import {
  claveRango, esRelativo, esperarArrastre, fraseRango, nombrePaso, pasoPara, prepararGraficoDeTiempo, rangoActual,
} from './tiempo.js';
import { abrirDetalle } from './alertas.js';

// Tocar una barra de «Alertas en el tiempo» abre la lista de Alertas con ese horario (lo arma tablero.js).
let verAlertasEntre = () => {};
export function configurarVivo({ verAlertas }) { verAlertasEntre = verAlertas; }

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

/** Las piezas: el punto, el semáforo y un dato corto con la misma forma en todas. Se pinta cada segundo (el
    dato cuenta los segundos) y cada vez que contesta /salud. La del firewall, solo si la red tiene uno
    (decisión 51): en modo de observación no aparece. */
export function pintarEstado() {
  const sistema = e.salud && e.salud.sistema;
  const ahora = ahoraServidor();
  const piezas = {
    sensor: { estado: sistema ? sistema.sensor : 'sin_datos', dato: datoSensor(sistema, ahora) },
    analisis: { estado: sistema ? sistema.analisis : 'sin_datos', dato: datoAnalisis(sistema, ahora) },
    servidor: { estado: e.servidorCaido ? 'no_responde' : 'funciona', dato: datoServidor() },
  };
  const firewall = e.salud && e.salud.firewall;
  const conFirewall = Boolean(firewall && firewall.habilitado);
  const piezaFirewall = $('pieza-firewall');
  if (piezaFirewall && piezaFirewall.hidden === conFirewall) piezaFirewall.hidden = !conFirewall;
  if (conFirewall) piezas.firewall = { estado: firewall.conectado ? 'funciona' : 'no_responde', dato: datoFirewall(firewall, ahora) };
  for (const [id, pieza] of Object.entries(piezas)) {
    const luz = LUZ[pieza.estado] || 'detenido';
    const semaforo = $(`semaforo-${id}`);
    if (!semaforo) continue;
    semaforo.dataset.estado = luz;
    semaforo.setAttribute('aria-label', `${$(`pieza-${id}`).querySelector('.pieza-titulo').textContent}: ${(NOMBRE_ESTADO[pieza.estado] || 'Sin datos').toLowerCase()}`);
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

/** «Última consulta hace 5 s», como la pieza de la app: el firewall pregunta cada 10 s qué bloquear. */
function datoFirewall(firewall, ahora) {
  if (!firewall.ultimaConsulta) return 'Sin datos';
  return `Última consulta hace ${cuanto(ahora - Date.parse(firewall.ultimaConsulta))}`;
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

/** Los datos de una pieza por minuto, para su tabla (de lo más nuevo a lo más viejo) y su CSV (en orden). */
function tablaPorMinuto(minutos, valores, columna) {
  return {
    columnas: ['Minuto', columna],
    numericas: 1,
    filas: minutos.map((t, i) => [fechaHora(t * 1000), numero(valores[i])]).reverse(),
    csv: { columnas: ['Minuto', columna], filas: minutos.map((t, i) => [fechaCsv(t * 1000), valores[i]]) },
  };
}

/** Los gráficos chicos del sensor y del análisis: conexiones y latidos de cada minuto de la última hora
    (un minuto sin latidos es un rato en que el análisis no anduvo). Cada pieza es un panel: en grande, con
    sus ejes. */
export function pintarGraficosPiezas() {
  const p = e.piezas;
  if (p) {
    const c = colores();
    const minutos = Array.from({ length: (p.hasta - p.desde) / 60 }, (_, i) => p.desde + i * 60);
    const conexiones = new Map(p.trafico.total.map((x) => [x.t, x.conexiones]));
    const latidos = new Map(p.latidos.minutos.map((x) => [x.minuto, x.latidos]));
    const ventana = { desde: p.desde * 1000, hasta: p.hasta * 1000 };
    const porMinuto = minutos.map((t) => conexiones.get(t) || 0);
    dibujar('pieza-sensor', opcionesMini(c, { ...ventana, puntos: minutos.map((t, i) => [t * 1000, porMinuto[i]]) },
      { tipo: 'barras', color: c['--serie-2'], nombre: 'conexiones', paso: 60, ejes: e.panelGrande === 'sensor' }));
    const valores = minutos.map((t) => latidos.get(t) || 0);
    dibujar('pieza-analisis', opcionesMini(c, { ...ventana, puntos: minutos.map((t, i) => [t * 1000, valores[i]]) },
      { tipo: 'barras', color: c['--verde'], nombre: 'revisiones', paso: 60, maximo: Math.max(3, ...valores), ejes: e.panelGrande === 'analisis' }));
    e.tablas.sensor = tablaPorMinuto(minutos, porMinuto, 'Conexiones');
    e.tablas.analisis = tablaPorMinuto(minutos, valores, 'Revisiones');
    refrescarTablas('sensor', 'analisis');
  }
  pintarGraficoServidor();
}

/** El gráfico chico del servidor: cuánto tardó en contestarle a este navegador, desde que se abrió. Sus
    datos son cada consulta, con la hora y lo que tardó. */
function pintarGraficoServidor() {
  const c = colores();
  const ahora = Date.now();
  dibujar('pieza-servidor', opcionesMini(c, { desde: ahora - 3600e3, hasta: ahora, puntos: e.respuestas.map((r) => [r.t, r.ms]) },
    { tipo: 'linea', color: c['--serie-1'], nombre: 'de respuesta', formato: (v) => `${numero(v)} ms`, ejes: e.panelGrande === 'servidor' }));
  e.tablas.servidor = {
    columnas: ['Hora', 'Respuesta'],
    numericas: 1,
    filas: e.respuestas.map((r) => [horaConSegundos(r.t), r.ms == null ? 'No respondió' : `${numero(r.ms)} ms`]).reverse(),
    csv: { columnas: ['Hora', 'Respuesta (ms)'], filas: e.respuestas.map((r) => [fechaCsv(r.t), r.ms ?? '']) },
  };
  refrescarTablas('servidor');
}

/** Cada contador abre la lista de Alertas con las sin revisar de su severidad (tablero.js), como la
    notificación de la app: en el teléfono la lista está justo abajo; acá, en su vista. */
export function pintarSinRevisar() {
  const conteo = e.conteo && e.conteo.sinRevisar;
  const valor = (s) => (conteo ? numero(conteo[s]) : '–');
  const lugar = $('sin-revisar');
  lugar.setAttribute('aria-busy', String(!conteo));
  // Se piden cada 5 s: si ya están, cambian solo los números, así no se pierde el foco del teclado.
  if (lugar.children.length) {
    for (const s of SEVERIDADES) lugar.querySelector(`[data-severidad="${s.id}"] .contador-numero`).textContent = valor(s.id);
    return;
  }
  vaciar(lugar, SEVERIDADES.map((s) => el('button', {
    type: 'button', class: 'contador', dataset: { severidad: s.id }, title: `Ver las alertas ${s.nombre.toLowerCase()}s sin revisar`,
  },
  el('span', { class: 'contador-titulo' }, el('span', { class: 'punto', style: `background:var(${s.color})` }), s.nombre),
  el('span', { class: 'contador-numero' }, valor(s.id)))));
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

// Las barras de «Alertas en el tiempo» tienen su propio largo, como un panel de Grafana con su intervalo:
// como mucho unas 60 en el horario, así no quedan como rayitas (las del tráfico pueden ser 300).
const PASOS_ALERTAS = [10, 30, 60, 300, 900, 1800, 3600, 10800, 21600, 43200, 86400];
const pasoDeAlertas = (ms) => PASOS_ALERTAS.find((p) => ms / 1000 / p <= 60) || 86400;

/** Las alertas que empezaron en cada barra del horario, por severidad y por tipo de ataque. Las barras arrancan
    en la medianoche de este navegador: las de 3 horas van de 0 a 3, de 3 a 6, y así. */
function alertasPorBarra(g) {
  const paso = pasoDeAlertas(g.hasta - g.desde);
  const largo = paso * 1000;
  const medianoche = new Date(g.desde).setHours(0, 0, 0, 0);
  const inicio = medianoche + Math.floor((g.desde - medianoche) / largo) * largo;
  const tiempos = Array.from({ length: Math.max(1, Math.ceil((g.hasta - inicio) / largo)) }, (_, i) => inicio + i * largo);
  const porSeveridad = Object.fromEntries(SEVERIDADES.map((s) => [s.id, new Array(tiempos.length).fill(0)]));
  const tipos = tiempos.map(() => new Map());
  for (const a of alertasEnVentana(g.desde, g.hasta)) {
    const i = Math.floor((a.t - inicio) / largo);
    if (i < 0 || i >= tiempos.length) continue;
    porSeveridad[a.severidad][i] += 1;
    const tipo = tipoCorto(a);
    tipos[i].set(tipo, (tipos[i].get(tipo) || 0) + 1);
  }
  const porTipo = tipos.map((m) => [...m.entries()].sort((x, y) => y[1] - x[1] || x[0].localeCompare(y[0])).map(([tipo, n]) => ({ tipo, n })));
  const totales = tiempos.map((_, i) => SEVERIDADES.reduce((suma, s) => suma + porSeveridad[s.id][i], 0));
  return { paso, tiempos, porSeveridad, porTipo, totales };
}

/** Tocar una barra (o el aire de su columna) abre sus alertas en la lista; arrastrar sigue siendo acercar. */
function prepararAlertasTiempo(grafico) {
  if (grafico.zondaAlertas) return;
  grafico.zondaAlertas = true;
  const zr = grafico.getZr();
  let abajo = null;
  zr.on('mousedown', (ev) => { abajo = [ev.offsetX, ev.offsetY]; });
  zr.on('click', (ev) => {
    const g = e.trafico;
    const b = e.alertasPorBarra;
    const punto = [ev.offsetX, ev.offsetY];
    if (!g || !b || !abajo || Math.hypot(punto[0] - abajo[0], punto[1] - abajo[1]) > 4 || !grafico.containPixel('grid', punto)) return;
    const largo = b.paso * 1000;
    const i = Math.floor((grafico.convertFromPixel('grid', punto)[0] - b.tiempos[0]) / largo);
    if (i < 0 || i >= b.tiempos.length || !b.totales[i]) return;
    // La primera y la última barra pueden pasarse del horario: la lista muestra lo mismo que se contó.
    verAlertasEntre(Math.max(b.tiempos[i], g.desde), Math.min(b.tiempos[i] + largo, g.hasta));
  });
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
  // Las alertas de cada barra, contadas: las marcas se tapan cuando caen varias juntas.
  const barras = alertasPorBarra(g);
  e.alertasPorBarra = barras;
  const graficoAlertas = dibujar('alertas-tiempo', opcionesAlertasTiempo(c, { tiempos: barras.tiempos, paso: barras.paso, desde: g.desde, hasta: g.hasta, porSeveridad: barras.porSeveridad, porTipo: barras.porTipo }));
  if (graficoAlertas) {
    prepararGraficoDeTiempo(graficoAlertas);
    prepararAlertasTiempo(graficoAlertas);
  }
  $('sub-alertas-tiempo').textContent = `Las alertas que empezaron en cada barra de ${nombrePaso(barras.paso)}, por severidad. Tocá una barra para verlas en la lista.`;
  const totalAlertas = barras.totales.reduce((a, b) => a + b, 0);
  $('vacio-alertas-tiempo').hidden = totalAlertas > 0;
  $('vacio-alertas-tiempo').textContent = `Ninguna alerta ${frase}.`;
  pintarLeyendaServicios(g, presentes);

  const total = g.conexiones.reduce((a, b) => a + b, 0);
  const fallidas = g.fallidas.reduce((a, b) => a + b, 0);
  const bytesTotal = g.bytes.reduce((a, b) => a + b, 0);
  $('sin-respuesta').textContent = numero(fallidas);
  $('sin-respuesta-detalle').textContent = total ? `${porcentaje(fallidas, total)} de ${numero(total)} conexiones ${frase}` : `Ninguna conexión ${frase}`;
  $('trafico-ahora-detalle').textContent = `${bytes(bytesTotal)} ${frase}`;
  $('conexiones-ahora-detalle').textContent = `Promedio de los últimos 30 segundos. ${numero(total)} conexiones ${frase}.`;

  const aviso = g.ultimoTramo == null ? 'Todavía no llegó tráfico del sensor. El sensor lo manda cada 5 segundos.'
    : `No hubo tráfico ${frase}. Lo último que vio el sensor fue ${cuandoFue(g.ultimoTramo * 1000)}.`;
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
  const columnasAlertas = ['Desde', 'Alertas', ...SEVERIDADES.map((s) => `${s.nombre}s`)];
  e.tablas['alertas-tiempo'] = {
    columnas: columnasAlertas,
    numericas: 1 + SEVERIDADES.length,
    filas: barras.tiempos.map((_, i) => i).filter((i) => barras.totales[i]).reverse()
      .map((i) => [horaTramo(barras.tiempos[i], barras.paso), numero(barras.totales[i]), ...SEVERIDADES.map((s) => numero(barras.porSeveridad[s.id][i]))]),
    csv: { columnas: columnasAlertas, filas: barras.tiempos.map((t, i) => [fechaCsv(t), barras.totales[i], ...SEVERIDADES.map((s) => barras.porSeveridad[s.id][i])]) },
  };
  refrescarTablas('servicios', 'volumen', 'fallidas', 'alertas-tiempo');
}

/** «a las 22:26» si es de hoy; «el 30/10 a las 22:26» si es de otro día. */
function cuandoFue(ms) {
  return new Date(ms).toDateString() === new Date(ahoraServidor()).toDateString() ? `a las ${hora(ms)}` : `el ${dia(ms)} a las ${hora(ms)}`;
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
  vaciar($('leyenda-alertas-tiempo'), SEVERIDADES.map((s) => el('span', {}, el('i', { style: `background:var(${s.color})` }), s.nombre)));
}

// ---- Últimas alertas y el aviso de una nueva ----

export function pintarUltimas() {
  const lista = [...e.alertas.values()].sort((a, b) => Date.parse(b.inicio) - Date.parse(a.inicio)).slice(0, 8);
  const ahora = ahoraServidor();
  // Se arma cada 30 s: si el foco estaba en una alerta, vuelve a la misma.
  const enfocada = $('ultimas-alertas').contains(document.activeElement) ? document.activeElement.dataset.id : null;
  vaciar($('ultimas-alertas'), lista.length ? lista.map((a) => el('li', {},
    el('button', { type: 'button', class: e.nuevas.has(a.id) ? 'nueva' : null, dataset: { id: a.id }, onclick: () => abrirDetalle(a.id) },
      chipSeveridad(a.severidad),
      el('span', { class: 'feed-tipo' }, tipoCorto(a)),
      el('span', { class: 'feed-par' }, `${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')} · ${(a.servicio || '').toUpperCase()}`),
      el('span', { class: 'feed-hora', title: fechaHora(Date.parse(a.inicio)) }, haceCuanto(Date.parse(a.inicio), ahora)),
      el('span', { class: 'feed-estado' }, chipEstado(a.estadoRevision)))))
    : el('li', { class: 'nota' }, 'Todavía no hay alertas.'));
  if (enfocada) {
    const boton = $('ultimas-alertas').querySelector(`[data-id="${CSS.escape(enfocada)}"]`);
    if (boton) boton.focus({ preventScroll: true });
  }
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
