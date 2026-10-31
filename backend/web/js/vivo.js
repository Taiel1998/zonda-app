// La vista En vivo: el estado del sistema, las alertas sin revisar, el tráfico de ahora, las conexiones por
// servicio con las alertas marcadas, el tráfico, las conexiones sin respuesta, el mapa de quién habla con
// quién y las últimas alertas, con un aviso cuando entra una nueva.

import { bitsPorSegundo, bytes, el, fechaHora, haceCuanto, hora, horaConSegundos, icono, numero, porcentaje, tasa, vaciar } from './util.js';
import {
  GRUPOS, SEVERIDADES, colores, formatoBits, grupoDe, masGrave, opcionesConexiones, opcionesMapa, opcionesSerieSimple,
} from './graficos.js';
import {
  $, NIVEL, ORDEN_SEVERIDAD, VENTANAS, ahoraServidor, chipEstado, chipSeveridad, dibujar, e, nombreEquipo, pedir,
  refrescarTablas, servicioDe, tipoCorto,
} from './comun.js';
import { abrirDetalle } from './alertas.js';

// ---- Estado del sistema y alertas sin revisar ----

export async function actualizarSalud() {
  const salud = await pedir('/salud');
  e.salud = salud;
  if (salud.hora) e.desfase = Date.parse(salud.hora) - Date.now();
  e.conteo = await pedir('/estado');
  pintarEstado();
  pintarSinRevisar();
}

const PIEZAS = {
  funciona: { texto: 'Funciona', color: 'var(--baja)' },
  parcial: { texto: 'Viendo parcialmente', color: 'var(--media)' },
  detenido: { texto: 'Detenido', color: 'var(--alta)' },
  sin_datos: { texto: 'Sin datos', color: 'var(--alta)' },
  no_responde: { texto: 'No responde', color: 'var(--alta)' },
};

export function pintarEstado() {
  const sistema = e.salud && e.salud.sistema;
  const servidor = e.servidorCaido ? 'no_responde' : 'funciona';
  const sensor = sistema ? sistema.sensor : 'sin_datos';
  const analisis = sistema ? sistema.analisis : 'sin_datos';
  const piezas = [
    { nombre: 'Sensor de red', estado: sensor, detalle: detalleSensor(sistema) },
    { nombre: 'Análisis en vivo', estado: analisis, detalle: sistema && sistema.segundosDesdeUltimaCorrida != null ? `Última revisión hace ${duracion(sistema.segundosDesdeUltimaCorrida)}` : 'Sin latido' },
    { nombre: 'Servidor de alertas', estado: servidor, detalle: servidor === 'funciona' ? `Versión ${e.salud ? e.salud.version : ''}` : 'No contesta' },
  ];
  let titulo; let bajada; let color; let simbolo;
  const caidas = piezas.filter((p) => !['funciona', 'parcial'].includes(p.estado));
  if (!caidas.length && sensor === 'funciona') {
    titulo = 'Todo funciona'; bajada = 'El sensor escucha y el análisis revisa el tráfico'; color = 'var(--baja)'; simbolo = 'check';
  } else if (!caidas.length) {
    titulo = 'El sensor ve parcialmente'; bajada = 'Puede haber tráfico que no ve'; color = 'var(--media)'; simbolo = 'alerta';
  } else {
    titulo = caidas.length === 1 ? `Revisar: ${caidas[0].nombre.toLowerCase()}` : 'Revisar el sistema';
    bajada = caidas.map((p) => `${p.nombre}: ${PIEZAS[p.estado].texto.toLowerCase()}`).join(' · ');
    color = 'var(--alta)'; simbolo = 'alerta';
  }
  vaciar($('estado-principal'),
    el('div', { class: 'icono', style: `background:${color}` }, icono(simbolo)),
    el('div', {}, el('strong', {}, titulo), el('span', {}, bajada)));
  vaciar($('estado-piezas'), piezas.map((p) => el('div', { class: 'pieza' },
    el('div', { class: 'pieza-nombre' }, el('span', { class: 'punto', style: `background:${PIEZAS[p.estado].color}` }), p.nombre.split(' ')[0]),
    el('div', { class: 'pieza-estado', title: `${PIEZAS[p.estado].texto} · ${p.detalle}` }, `${PIEZAS[p.estado].texto} · ${p.detalle}`))));
}

function detalleSensor(sistema) {
  if (!sistema || sistema.sensor === 'sin_datos') return 'Sin datos del sensor';
  if (sistema.sensor === 'detenido') return 'No escucha la red';
  if (sistema.perdidaCaptura != null && sistema.perdidaCaptura > 0) return `Pierde el ${numero(sistema.perdidaCaptura, 1)} % del tráfico`;
  return 'Escuchando la red';
}

function duracion(segundos) {
  if (segundos < 60) return `${segundos} s`;
  if (segundos < 3600) return `${Math.round(segundos / 60)} min`;
  return `${Math.round(segundos / 3600)} h`;
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

export async function actualizarTrafico() {
  const ventana = VENTANAS[e.ventana];
  const desde = Math.floor((ahoraServidor() / 1000 - ventana.segundos) / ventana.paso) * ventana.paso;
  const pedido = e.ventana;
  const serie = await pedir(`/trafico?desde=${desde}&paso=${ventana.paso}`);
  if (pedido !== e.ventana) return;
  e.trafico = { ventana: pedido, ...enGrilla(serie), ultimoTramo: serie.ultimoTramo };
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

export function pintarTrafico() {
  const g = e.trafico;
  if (!g) return;
  const c = colores();
  const ventana = VENTANAS[g.ventana];
  $('nota-ventana').textContent = ventana.nota;

  // Alertas de la ventana: las más graves primero, hasta 40 líneas.
  const alertas = alertasEnVentana(g.desde, g.hasta)
    .sort((a, b) => (ORDEN_SEVERIDAD.indexOf(a.severidad) - ORDEN_SEVERIDAD.indexOf(b.severidad)) || b.t - a.t)
    .slice(0, 40)
    .map((a) => ({ t: a.t, severidad: a.severidad, nivel: NIVEL[a.severidad], corto: `${NIVEL[a.severidad]} · ${tipoCorto(a)}`, texto: `${tipoCorto(a)} · ${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')}` }));

  const porSegundo = (lista) => lista.map((v) => v / g.paso);
  const porGrupo = Object.fromEntries(Object.entries(g.porGrupo).map(([id, v]) => [id, porSegundo(v)]));
  dibujar('conexiones', opcionesConexiones(c, { tiempos: g.tiempos, porGrupo, paso: g.paso, desde: g.desde, hasta: g.hasta, alertas }));
  dibujar('trafico', opcionesSerieSimple(c, { tiempos: g.tiempos, valores: g.bytes.map((b) => (b * 8) / g.paso), paso: g.paso, desde: g.desde, hasta: g.hasta }, { tipo: 'linea', formato: formatoBits, nombre: 'bits por segundo' }));
  dibujar('fallidas', opcionesSerieSimple(c, { tiempos: g.tiempos, valores: porSegundo(g.fallidas), paso: g.paso, desde: g.desde, hasta: g.hasta }, { tipo: 'barras', formato: tasa, nombre: 'sin respuesta por segundo' }));

  const total = g.conexiones.reduce((a, b) => a + b, 0);
  const fallidas = g.fallidas.reduce((a, b) => a + b, 0);
  const bytesTotal = g.bytes.reduce((a, b) => a + b, 0);
  $('sin-respuesta').textContent = numero(fallidas);
  $('sin-respuesta-detalle').textContent = total ? `${porcentaje(fallidas, total)} de ${numero(total)} conexiones en ${ventana.texto}` : `Ninguna conexión en ${ventana.texto}`;
  $('trafico-ahora-detalle').textContent = `${bytes(bytesTotal)} en ${ventana.texto}`;
  $('conexiones-ahora-detalle').textContent = `Promedio de los últimos 30 segundos. ${numero(total)} conexiones en ${ventana.texto}.`;

  const vacio = $('vacio-conexiones');
  vacio.hidden = total > 0;
  vacio.textContent = g.ultimoTramo == null ? 'Todavía no llegó tráfico del sensor. El sensor lo manda cada 5 segundos.'
    : `No hubo conexiones en ${ventana.texto}. Lo último que vio el sensor fue a las ${fechaHora(g.ultimoTramo * 1000)}.`;

  // Las tablas de los tres gráficos.
  const filas = [];
  for (let i = g.tiempos.length - 1; i >= 0; i -= 1) if (g.conexiones[i] || g.bytes[i]) filas.push(i);
  e.tablas.conexiones = {
    columnas: ['Desde', ...GRUPOS.map((x) => x.nombre), 'Total por segundo'],
    numericas: GRUPOS.length + 1,
    filas: filas.filter((i) => g.conexiones[i]).map((i) => [horaTramo(g.tiempos[i], g.paso), ...GRUPOS.map((x) => tasa(porGrupo[x.id][i])), tasa(g.conexiones[i] / g.paso)]),
  };
  e.tablas.trafico = { columnas: ['Desde', 'Bits por segundo', 'Bytes'], numericas: 2, filas: filas.map((i) => [horaTramo(g.tiempos[i], g.paso), bitsPorSegundo((g.bytes[i] * 8) / g.paso), bytes(g.bytes[i])]) };
  e.tablas.fallidas = { columnas: ['Desde', 'Sin respuesta por segundo', 'Sin respuesta'], numericas: 2, filas: filas.filter((i) => g.fallidas[i]).map((i) => [horaTramo(g.tiempos[i], g.paso), tasa(g.fallidas[i] / g.paso), numero(g.fallidas[i])]) };
  refrescarTablas('conexiones', 'trafico', 'fallidas');
}

function horaTramo(ms, paso) {
  return paso >= 60 ? fechaHora(ms) : horaConSegundos(ms);
}

// ---- Mapa ----

function ipANumero(ip) {
  const partes = ip.split('.').map(Number);
  if (partes.length !== 4 || partes.some((p) => Number.isNaN(p))) return null;
  return ((partes[0] << 24) >>> 0) + (partes[1] << 16) + (partes[2] << 8) + partes[3];
}

function areaDe(ip) {
  const n = ipANumero(ip);
  if (n !== null) {
    for (const area of e.areas) {
      for (const red of area.redes) {
        const [base, bits] = red.split('/');
        const b = ipANumero(base);
        const mascara = bits === '0' ? 0 : (~0 << (32 - Number(bits))) >>> 0;
        if (b !== null && ((n & mascara) >>> 0) === ((b & mascara) >>> 0)) return area.nombre;
      }
    }
  }
  return 'Fuera de la red';
}

function esDifusion(ip) {
  const v6 = ip.toLowerCase();
  if (v6.includes(':')) return v6.startsWith('fe80:') || v6.startsWith('ff') || v6 === '::';
  const partes = ip.split('.').map(Number);
  return ip === '0.0.0.0' || partes[3] === 255 || (partes[0] >= 224 && partes[0] <= 239);
}

export async function actualizarPares() {
  const ventana = VENTANAS[e.ventana];
  const desde = Math.floor(ahoraServidor() / 1000 - ventana.segundos);
  const pedido = e.ventana;
  const pares = await pedir(`/trafico/pares?desde=${desde}`);
  if (pedido !== e.ventana) return;
  e.pares = { ventana: pedido, ...pares };
  pintarMapa();
}

export function pintarMapa() {
  const p = e.pares;
  if (!p) return;
  const c = colores();
  const alertas = alertasEnVentana(p.desde * 1000, p.hasta * 1000);
  const alertaDelPar = new Map();
  for (const a of alertas) {
    const clave = `${a.origenIp}>${a.destinoIp}`;
    const actual = alertaDelPar.get(clave);
    if (!actual || (masGrave(a.severidad, actual.severidad) === a.severidad && a.severidad !== actual.severidad)) alertaDelPar.set(clave, a);
  }
  // Los 40 pares con más conexiones, y todos los que tienen una alerta. Fuera lo que no es una conversación
  // entre equipos: difusión y multidifusión, y las direcciones IPv6 de enlace local (descubrimiento de vecinos).
  const conversaciones = p.pares.filter((par) => !esDifusion(par.origen) && !esDifusion(par.destino));
  const elegidos = conversaciones.filter((par, i) => i < 40 || alertaDelPar.has(`${par.origen}>${par.destino}`));
  const nodos = new Map();
  const nodo = (ip, nombre) => {
    if (!nodos.has(ip)) {
      let area = areaDe(ip);
      let nombreFinal = nombre || ip;
      let forma = 'circle';
      // Las puertas de enlace (la .1 de cada subred) son el firewall que separa las áreas: van en su columna.
      if (/(^fw|firewall)/i.test(nombreFinal) || (area !== 'Fuera de la red' && /\.1$/.test(ip))) {
        forma = 'diamond';
        area = 'Firewall';
        if (!nombre) nombreFinal = 'firewall';
      } else if (/^srv|servidor/i.test(nombreFinal) || /servidor/i.test(area)) {
        forma = 'roundRect';
      }
      nodos.set(ip, { id: ip, ip, nombre: nombreFinal, area, conexiones: 0, forma });
    }
    return nodos.get(ip);
  };
  const aristas = elegidos.map((par) => {
    const origen = nodo(par.origen, par.origenNombre);
    const destino = nodo(par.destino, par.destinoNombre);
    origen.conexiones += par.conexiones;
    destino.conexiones += par.conexiones;
    const alerta = alertaDelPar.get(`${par.origen}>${par.destino}`);
    return {
      origen: par.origen, destino: par.destino, origenNombre: origen.nombre, destinoNombre: destino.nombre,
      conexiones: par.conexiones, fallidas: par.fallidas, bytes: par.bytes, servicios: par.servicios,
      severidad: alerta ? alerta.severidad : null, nivel: alerta ? `Alerta ${NIVEL[alerta.severidad].toLowerCase()}` : null,
      alertaTexto: alerta ? tipoCorto(alerta) : null,
    };
  });
  const ordenAreas = e.areas.map((a) => a.nombre);
  const antesDeServidores = ordenAreas.findIndex((nombre) => /servidor/i.test(nombre));
  ordenAreas.splice(antesDeServidores < 0 ? ordenAreas.length : antesDeServidores, 0, 'Firewall');
  ordenAreas.push('Fuera de la red');
  const areas = ordenAreas
    .map((nombre) => ({ nombre, nodos: [...nodos.values()].filter((n) => n.area === nombre).sort((a, b) => b.conexiones - a.conexiones) }))
    .filter((a) => a.nodos.length);
  const ventana = VENTANAS[p.ventana];
  const vacio = $('vacio-mapa');
  vacio.hidden = aristas.length > 0;
  vacio.textContent = `No hubo tráfico entre equipos en ${ventana.texto}.`;
  const lienzo = $('grafico-mapa');
  dibujar('mapa', opcionesMapa(c, { areas, aristas, ancho: lienzo.clientWidth, alto: lienzo.clientHeight }), () => pintarMapa());
  e.tablas.mapa = {
    columnas: ['Origen', 'Destino', 'Conexiones', 'Sin respuesta', 'Servicios', 'Alerta'],
    numericas: 0,
    filas: aristas.map((a) => [`${a.origenNombre} (${a.origen})`, `${a.destinoNombre} (${a.destino})`, numero(a.conexiones), numero(a.fallidas), a.servicios.join(', ').toUpperCase(), a.severidad ? `${NIVEL[a.severidad]} · ${a.alertaTexto}` : '–']),
  };
  refrescarTablas('mapa');
}

export function pintarLeyendas() {
  vaciar($('leyenda-conexiones'),
    GRUPOS.map((g) => el('span', {}, el('i', { style: `background:var(${g.color})` }), g.nombre)),
    el('span', { style: 'margin-left:12px' }, 'Alertas:'),
    SEVERIDADES.map((s) => el('span', {}, el('i', { class: 'linea', style: `background:var(${s.color})` }), s.nombre)));
  vaciar($('leyenda-mapa'),
    el('span', {}, el('i', { class: 'linea', style: 'background:var(--arista)' }), 'Sin alerta'),
    SEVERIDADES.map((s) => el('span', {}, el('i', { class: 'linea', style: `background:var(${s.color})` }), `Alerta ${s.nombre.toLowerCase()}`)),
    el('span', { style: 'margin-left:12px' }, '○ Equipo · ▢ Servidor · ◇ Firewall'));
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
      el('span', { class: 'feed-hora', title: fechaHora(Date.parse(a.inicio)) }, haceCuanto(Date.parse(a.inicio), ahora)),
      el('span', { class: 'feed-par' }, `${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')} · ${(a.servicio || '').toUpperCase()}`),
      el('span', {}, chipEstado(a.estadoRevision)))))
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
