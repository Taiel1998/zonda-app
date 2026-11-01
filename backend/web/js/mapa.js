// Comunicaciones entre áreas como el «Node graph» de Grafana, que se parece a Neo4j: cada equipo es un
// círculo del color de su área y cada flecha va del origen al destino. Se arrastran los equipos y el fondo;
// se acerca con los botones o con Ctrl y la rueda (en grande, con la rueda sola). Al tocar un equipo o una
// flecha se resalta lo suyo y el detalle aparece al costado; sin nada elegido, el costado resume el mapa,
// como el panel de Neo4j.

import { bytes, el, fechaHora, icono, numero, vaciar } from './util.js';
import { SEVERIDADES, colores, opcionesGrafo } from './graficos.js';
import { $, NIVEL, ORDEN_SEVERIDAD, chipSeveridad, dibujar, e, pedir, refrescarTablas, tipoCorto } from './comun.js';
import { claveRango, esRelativo, fraseRango, rangoActual } from './tiempo.js';
import { alertasEnVentana } from './vivo.js';
import { abrirDetalle } from './alertas.js';

// Lo que el mapa recuerda entre una actualización y la siguiente.
const mapa = {
  posiciones: new Map(), // id del equipo → [x, y], de 0 a 1 del recuadro
  rango: null, // el horario del último acomodo completo
  ancho: 0, // y el ancho del recuadro en ese momento
  elegido: null, // { tipo: 'nodo', id } o { tipo: 'arista', clave: 'origen>destino' }
};

// Lo que queda libre en los bordes para los nombres, que van debajo de cada círculo.
const MARGEN = { lado: 72, arriba: 30, abajo: 66 };

const limitar = (valor, minimo, maximo) => Math.min(maximo, Math.max(minimo, valor));
const porGravedad = (a, b) => (ORDEN_SEVERIDAD.indexOf(a.severidad) - ORDEN_SEVERIDAD.indexOf(b.severidad)) || b.t - a.t;
const claveDe = (a) => `${a.origen}>${a.destino}`;

// ---- Los datos ----

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
  const { desde, hasta } = rangoActual();
  const pedido = claveRango();
  const fin = esRelativo() ? '' : `&hasta=${Math.ceil(hasta / 1000)}`;
  const pares = await pedir(`/trafico/pares?desde=${Math.floor(desde / 1000)}${fin}`);
  if (pedido !== claveRango()) return;
  e.pares = { rango: pedido, ...pares };
  pintarMapa();
}

/** El orden de las áreas: el de areas.json, con el firewall antes de los servidores y lo de afuera al final. */
function ordenDeAreas() {
  const orden = e.areas.map((a) => a.nombre);
  const antesDeServidores = orden.findIndex((nombre) => /servidor/i.test(nombre));
  orden.splice(antesDeServidores < 0 ? orden.length : antesDeServidores, 0, 'Firewall');
  orden.push('Fuera de la red');
  return orden;
}

/** El color de cada área: los cuatro de la paleta para las de areas.json, en su orden (el color sigue al área,
    nunca a cuántos equipos tiene); el firewall en gris oscuro y lo de afuera, en gris. */
function colorDeArea(nombre) {
  const i = e.areas.findIndex((a) => a.nombre === nombre);
  if (i >= 0 && i < 4) return `--serie-${i + 1}`;
  return nombre === 'Firewall' ? '--texto-2' : '--serie-otros';
}

function nuevoEquipo(id, ip, nombre, area) {
  return { id, ip, nombre, area, conexiones: 0, fallidas: 0, alertas: [], severidad: null, nivel: null, alertaTexto: null };
}

/** Los equipos y las flechas del horario, cada uno con sus alertas y la más grave. */
function armarRed(p) {
  const alertas = alertasEnVentana(p.desde * 1000, p.hasta * 1000);
  const conAlerta = new Set(alertas.map((a) => `${a.origenIp}>${a.destinoIp}`));
  // Los 40 pares con más conexiones, y todos los que tienen una alerta. Fuera lo que no es una conversación
  // entre equipos: difusión y multidifusión, y las direcciones IPv6 de enlace local (descubrimiento de vecinos).
  const conversaciones = p.pares.filter((par) => !esDifusion(par.origen) && !esDifusion(par.destino));
  const elegidos = conversaciones.filter((par, i) => i < 40 || conAlerta.has(`${par.origen}>${par.destino}`));
  // Con más de cuatro equipos de afuera de la red, van todos juntos en un solo círculo.
  const externos = new Set();
  for (const par of elegidos) for (const ip of [par.origen, par.destino]) if (areaDe(ip) === 'Fuera de la red') externos.add(ip);
  const juntarAfuera = externos.size > 4;
  const equipos = new Map();
  const equipoDeIp = new Map();
  const equipo = (ip, nombre) => {
    if (!equipoDeIp.has(ip)) {
      const area = areaDe(ip);
      if (juntarAfuera && area === 'Fuera de la red') {
        if (!equipos.has('afuera')) equipos.set('afuera', nuevoEquipo('afuera', 'otras redes', `${numero(externos.size)} equipos`, area));
        equipoDeIp.set(ip, 'afuera');
      } else {
        // Las puertas de enlace (la .1 de cada subred) son el firewall que separa las áreas.
        const esFirewall = /(^fw|firewall)/i.test(nombre || '') || (area !== 'Fuera de la red' && /\.1$/.test(ip));
        equipos.set(ip, nuevoEquipo(ip, ip, nombre || (esFirewall ? 'firewall' : ip), esFirewall ? 'Firewall' : area));
        equipoDeIp.set(ip, ip);
      }
    }
    return equipos.get(equipoDeIp.get(ip));
  };
  // Una flecha por par de círculos (los de afuera juntos suman sus conexiones).
  const flechas = new Map();
  for (const par of elegidos) {
    const origen = equipo(par.origen, par.origenNombre);
    const destino = equipo(par.destino, par.destinoNombre);
    if (origen.id === destino.id) continue;
    for (const x of [origen, destino]) { x.conexiones += par.conexiones; x.fallidas += par.fallidas; }
    const clave = `${origen.id}>${destino.id}`;
    if (!flechas.has(clave)) {
      flechas.set(clave, {
        origen: origen.id, destino: destino.id, origenNombre: origen.nombre, destinoNombre: destino.nombre,
        conexiones: 0, fallidas: 0, bytes: 0, servicios: [], alertas: [], severidad: null, nivel: null, alertaTexto: null,
      });
    }
    const flecha = flechas.get(clave);
    flecha.conexiones += par.conexiones;
    flecha.fallidas += par.fallidas;
    flecha.bytes += par.bytes;
    for (const s of par.servicios) if (!flecha.servicios.includes(s)) flecha.servicios.push(s);
  }
  for (const a of alertas) {
    const origen = equipoDeIp.get(a.origenIp);
    const destino = equipoDeIp.get(a.destinoIp);
    if (origen && destino && flechas.has(`${origen}>${destino}`)) flechas.get(`${origen}>${destino}`).alertas.push(a);
    for (const id of new Set([origen, destino])) if (id) equipos.get(id).alertas.push(a);
  }
  for (const x of [...equipos.values(), ...flechas.values()]) {
    x.alertas.sort(porGravedad);
    const peor = x.alertas[0];
    if (peor) {
      x.severidad = peor.severidad;
      x.nivel = `Alerta ${NIVEL[peor.severidad].toLowerCase()}`;
      x.alertaTexto = tipoCorto(peor);
    }
  }
  for (const flecha of flechas.values()) {
    const servicios = flecha.servicios.slice(0, 2).map((s) => s.toUpperCase()).join(', ');
    flecha.etiqueta = servicios ? `${servicios} · ${numero(flecha.conexiones)}` : numero(flecha.conexiones);
  }
  const lista = [...equipos.values()];
  return { nodos: lista, aristas: [...flechas.values()], areas: ordenDeAreas().filter((area) => lista.some((n) => n.area === area)) };
}

// ---- El dibujo ----

export function pintarMapa() {
  const p = e.pares;
  if (!p) return;
  const red = armarRed(p);
  const elegido = mapa.elegido;
  if (elegido && !(elegido.tipo === 'nodo' ? red.nodos.some((n) => n.id === elegido.id) : red.aristas.some((a) => claveDe(a) === elegido.clave))) mapa.elegido = null;
  const vacio = $('vacio-mapa');
  vacio.hidden = red.aristas.length > 0;
  vacio.textContent = `No hubo tráfico entre equipos ${fraseRango()}.`;
  pintarCostado(red);
  anotarTabla(red);
  const lienzo = $('grafico-mapa');
  const ancho = lienzo.clientWidth;
  const alto = lienzo.clientHeight;
  // Con la fila plegada o en otra vista no hay dónde dibujar: se dibuja al volver.
  if (!ancho || !alto) return;
  acomodar(red, ancho, alto, p.rango);
  const grafico = dibujar('mapa', opcionesGrafo(colores(), {
    nodos: red.nodos,
    aristas: red.aristas,
    posiciones: mapa.posiciones,
    colorDe: colorDeArea,
    ancho,
    alto,
    elegido: mapa.elegido,
    encendidos: encendidosDe(red),
  }), () => pintarMapa());
  if (grafico) prepararMapa(grafico);
}

/** Lo que queda encendido con algo elegido: el equipo y sus vecinos, o la flecha y sus dos equipos. */
function encendidosDe(red) {
  const elegido = mapa.elegido;
  if (!elegido) return null;
  if (elegido.tipo === 'arista') return new Set(elegido.clave.split('>'));
  const encendidos = new Set([elegido.id]);
  for (const a of red.aristas) {
    if (a.origen === elegido.id) encendidos.add(a.destino);
    if (a.destino === elegido.id) encendidos.add(a.origen);
  }
  return encendidos;
}

function anotarTabla(red) {
  const porId = new Map(red.nodos.map((n) => [n.id, n]));
  const etiqueta = (id) => {
    const n = porId.get(id);
    return n.nombre === n.ip ? n.ip : `${n.nombre} (${n.ip})`;
  };
  const alerta = (a) => (a.severidad ? `${NIVEL[a.severidad]} · ${a.alertaTexto}` : '');
  e.tablas.mapa = {
    columnas: ['Origen', 'Destino', 'Conexiones', 'Sin respuesta', 'Servicios', 'Alerta'],
    numericas: 0,
    filas: red.aristas.map((a) => [etiqueta(a.origen), etiqueta(a.destino), numero(a.conexiones), numero(a.fallidas), a.servicios.join(', ').toUpperCase(), alerta(a) || '–']),
    csv: {
      columnas: ['Origen', 'IP de origen', 'Destino', 'IP de destino', 'Conexiones', 'Sin respuesta', 'Bytes', 'Servicios', 'Alerta'],
      filas: red.aristas.map((a) => [porId.get(a.origen).nombre, porId.get(a.origen).ip, porId.get(a.destino).nombre, porId.get(a.destino).ip, a.conexiones, a.fallidas, a.bytes, a.servicios.join(' ').toUpperCase(), alerta(a)]),
    },
  };
  refrescarTablas('mapa');
}

// ---- Dónde va cada equipo ----

/** Ubica los equipos nuevos. Vuelve a ubicarlos a todos si cambió casi todo (otro horario) o si el recuadro
    cambió mucho de ancho (de la compu al teléfono): estirado o apretado, el mapa se leería mal. En grande
    cambia el alto, no el ancho, y los equipos quedan donde estaban. */
function acomodar(red, ancho, alto, rango) {
  const nuevos = red.nodos.filter((n) => !mapa.posiciones.has(n.id));
  const otroAncho = ancho / mapa.ancho < 0.7 || ancho / mapa.ancho > 1.6;
  if (!mapa.posiciones.size || otroAncho || (rango !== mapa.rango && nuevos.length > red.nodos.length * 0.4)) {
    mapa.posiciones = new Map();
    mapa.rango = rango;
    mapa.ancho = ancho;
    simular(red, ancho, alto, red.nodos);
  } else if (nuevos.length) {
    simular(red, ancho, alto, nuevos);
  }
}

/**
 * Fruchterman y Reingold con un ancla por área: los equipos se empujan entre sí, cada flecha acerca a sus dos
 * equipos y cada uno tira hacia el lugar de su área (las áreas van de izquierda a derecha, en el orden de
 * areas.json). Al final se separan los que se encimarían con su nombre. Sin azar: los mismos datos dan el
 * mismo mapa. Solo se mueven los `moviles`; los demás quedan donde estaban o donde los dejó la persona.
 */
function simular(red, ancho, alto, moviles) {
  const caja = { x0: MARGEN.lado, x1: Math.max(MARGEN.lado + 1, ancho - MARGEN.lado), y0: MARGEN.arriba, y1: Math.max(MARGEN.arriba + 1, alto - MARGEN.abajo) };
  const W = caja.x1 - caja.x0;
  const H = caja.y1 - caja.y0;
  const lista = red.nodos.slice().sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const n = lista.length;
  if (!n) return;
  const libre = new Set(moviles.map((m) => m.id));
  const indice = new Map(lista.map((nodo, i) => [nodo.id, i]));
  const anclas = new Map(red.areas.map((area, i) => [area, [caja.x0 + ((i + 0.5) * W) / red.areas.length, caja.y0 + H / 2]]));
  // De dónde arranca cada uno: los que ya estaban, en su lugar; los nuevos, en espiral alrededor de su área.
  const vueltas = new Map();
  const pos = lista.map((nodo) => {
    const previa = mapa.posiciones.get(nodo.id);
    if (previa) return [previa[0] * ancho, previa[1] * alto];
    const [ax, ay] = anclas.get(nodo.area) || [caja.x0 + W / 2, caja.y0 + H / 2];
    const k = vueltas.get(nodo.area) || 0;
    vueltas.set(nodo.area, k + 1);
    const radio = 30 * Math.sqrt(k + 0.5);
    return [ax + radio * Math.cos(k * 2.4), ay + radio * Math.sin(k * 2.4)];
  });
  const pares = [];
  const vistos = new Set();
  for (const a of red.aristas) {
    const i = indice.get(a.origen);
    const j = indice.get(a.destino);
    const clave = i < j ? `${i}|${j}` : `${j}|${i}`;
    if (i === undefined || j === undefined || vistos.has(clave)) continue;
    vistos.add(clave);
    pares.push([i, j]);
  }
  const completo = libre.size === n;
  const distancia = 0.8 * Math.sqrt((W * H) / n);
  const pasos = completo ? 300 : 150;
  const calor = completo ? W / 8 : Math.min(W, H) / 5;
  for (let paso = 0; paso < pasos; paso += 1) {
    const t = calor * (1 - paso / pasos) + 0.5;
    const empuje = lista.map(() => [0, 0]);
    const sumar = (i, dx, dy, d, f) => { empuje[i][0] += (dx / d) * f; empuje[i][1] += (dy / d) * f; };
    for (let i = 0; i < n; i += 1) {
      for (let j = i + 1; j < n; j += 1) {
        let dx = pos[i][0] - pos[j][0];
        let dy = pos[i][1] - pos[j][1];
        let d = Math.hypot(dx, dy);
        // Dos en el mismo lugar se separan siempre hacia el mismo lado.
        if (d < 0.5) { dx = (i % 3) - 1 || 1; dy = (j % 3) - 1 || 1; d = Math.hypot(dx, dy); }
        const f = (distancia * distancia) / d;
        sumar(i, dx, dy, d, f);
        sumar(j, -dx, -dy, d, f);
      }
    }
    for (const [i, j] of pares) {
      const dx = pos[i][0] - pos[j][0];
      const dy = pos[i][1] - pos[j][1];
      const d = Math.hypot(dx, dy) || 0.5;
      const f = (d * d) / distancia;
      sumar(i, -dx, -dy, d, f);
      sumar(j, dx, dy, d, f);
    }
    // El ancla tira más que una flecha: así cada área conserva su lugar aunque hable con las otras.
    lista.forEach((nodo, i) => {
      const ancla = anclas.get(nodo.area);
      if (!ancla) return;
      const dx = pos[i][0] - ancla[0];
      const dy = pos[i][1] - ancla[1];
      const d = Math.hypot(dx, dy);
      if (d >= 0.5) sumar(i, -dx, -dy, d, (2 * d * d) / distancia);
    });
    lista.forEach((nodo, i) => {
      if (!libre.has(nodo.id)) return;
      const d = Math.hypot(empuje[i][0], empuje[i][1]);
      if (d < 0.01) return;
      const m = Math.min(d, t);
      pos[i][0] = limitar(pos[i][0] + (empuje[i][0] / d) * m, caja.x0, caja.x1);
      pos[i][1] = limitar(pos[i][1] + (empuje[i][1] / d) * m, caja.y0, caja.y1);
    });
  }
  if (completo) estirar(pos, caja);
  separar(lista, pos, libre, caja);
  lista.forEach((nodo, i) => { if (libre.has(nodo.id)) mapa.posiciones.set(nodo.id, [pos[i][0] / ancho, pos[i][1] / alto]); });
}

/** Con pocos equipos el dibujo queda chico en el medio: se estira para ocupar el recuadro (hasta tres veces
    a lo ancho y a lo alto), centrado. */
function estirar(pos, caja) {
  for (const [eje, minimo, maximo] of [[0, caja.x0, caja.x1], [1, caja.y0, caja.y1]]) {
    const valores = pos.map((p) => p[eje]);
    const desde = Math.min(...valores);
    const hasta = Math.max(...valores);
    const factor = Math.min(3, (maximo - minimo) / Math.max(1, hasta - desde));
    for (const p of pos) p[eje] = limitar((minimo + maximo) / 2 + (p[eje] - (desde + hasta) / 2) * factor, minimo, maximo);
  }
}

/** Que no se encimen los nombres: cada equipo ocupa su círculo y las dos líneas que van debajo. */
function separar(lista, pos, libre, caja) {
  const medidas = lista.map((nodo) => ({ w: Math.max(64, 7.2 * nodo.nombre.length + 14, 6.6 * nodo.ip.length + 14), h: 74 }));
  for (let vuelta = 0; vuelta < 60; vuelta += 1) {
    let movio = false;
    for (let i = 0; i < lista.length; i += 1) {
      for (let j = i + 1; j < lista.length; j += 1) {
        const parteI = libre.has(lista[i].id) ? (libre.has(lista[j].id) ? 0.5 : 1) : 0;
        const parteJ = libre.has(lista[j].id) ? 1 - parteI : 0;
        if (!parteI && !parteJ) continue;
        const dx = pos[j][0] - pos[i][0];
        const dy = pos[j][1] - pos[i][1];
        const sx = (medidas[i].w + medidas[j].w) / 2 - Math.abs(dx);
        const sy = (medidas[i].h + medidas[j].h) / 2 - Math.abs(dy);
        if (sx <= 0 || sy <= 0) continue;
        // Se separan por donde menos se enciman.
        const eje = sx < sy ? 0 : 1;
        const s = (eje === 0 ? (dx >= 0 ? sx : -sx) : (dy >= 0 ? sy : -sy));
        const [minimo, maximo] = eje === 0 ? [caja.x0, caja.x1] : [caja.y0, caja.y1];
        pos[i][eje] = limitar(pos[i][eje] - s * parteI, minimo, maximo);
        pos[j][eje] = limitar(pos[j][eje] + s * parteJ, minimo, maximo);
        movio = true;
      }
    }
    if (!movio) break;
  }
}

// ---- Tocar, arrastrar y acercar ----

function elegir(cual) {
  const actual = mapa.elegido;
  const mismo = cual && actual && cual.tipo === actual.tipo && (cual.id || cual.clave) === (actual.id || actual.clave);
  mapa.elegido = mismo ? null : cual;
  pintarMapa();
}

/** Esc suelta lo elegido en el mapa; devuelve si había algo elegido a la vista. */
export function soltarElegido() {
  if (!mapa.elegido || !$('grafico-mapa').clientWidth) return false;
  elegir(null);
  return true;
}

/** Los clics y los arrastres del mapa; se arman una sola vez por gráfico. Un arrastre no cuenta como clic. Un
    solo oyente por evento, que mira qué se tocó: elegir redibuja el mapa, y ECharts, al redibujar dentro de
    un evento, deja de filtrar los oyentes que quedan (un clic en un equipo llegaba también al de las flechas). */
function prepararMapa(grafico) {
  if (grafico.zondaMapa) return;
  grafico.zondaMapa = true;
  const zr = grafico.getZr();
  let inicio = null;
  let agarrado = null;
  let movio = false;
  zr.on('mousedown', (ev) => { inicio = [ev.offsetX, ev.offsetY]; movio = false; });
  grafico.on('mousedown', (p) => { agarrado = p.dataType === 'node' && p.data && p.data.datos ? p.data.id : null; });
  zr.on('mouseup', (ev) => {
    if (inicio) movio = Math.hypot(ev.offsetX - inicio[0], ev.offsetY - inicio[1]) > 4;
    if (agarrado && movio) guardarArrastre(grafico, agarrado, inicio, [ev.offsetX, ev.offsetY]);
    agarrado = null;
  });
  grafico.on('click', (p) => {
    if (movio || !p.data || !p.data.datos) return;
    if (p.dataType === 'node') elegir({ tipo: 'nodo', id: p.data.id });
    else if (p.dataType === 'edge') elegir({ tipo: 'arista', clave: claveDe(p.data.datos) });
  });
  zr.on('click', (ev) => { if (!ev.target && !movio && mapa.elegido) elegir(null); });
}

/** Donde la persona suelta un equipo, ahí queda: en la próxima actualización no vuelve a su lugar. */
function guardarArrastre(grafico, id, de, a) {
  const previa = mapa.posiciones.get(id);
  const p0 = grafico.convertFromPixel({ seriesId: 'grafo' }, de);
  const p1 = grafico.convertFromPixel({ seriesId: 'grafo' }, a);
  const lienzo = $('grafico-mapa');
  if (!previa || !p0 || !p1 || !lienzo.clientWidth) return;
  mapa.posiciones.set(id, [
    limitar(previa[0] + (p1[0] - p0[0]) / lienzo.clientWidth, 0.02, 0.98),
    limitar(previa[1] + (p1[1] - p0[1]) / lienzo.clientHeight, 0.02, 0.98),
  ]);
}

function acercar(factor) {
  const grafico = e.graficos.mapa;
  if (!grafico) return;
  const serie = (grafico.getOption().series || []).find((s) => s.id === 'grafo');
  grafico.setOption({ series: [{ id: 'grafo', zoom: limitar(((serie && serie.zoom) || 1) * factor, 0.5, 3) }] });
}

/** Vuelve a ubicar a todos los equipos y centra el mapa, sin zoom. */
function reordenar() {
  mapa.posiciones = new Map();
  mapa.rango = null;
  mapa.ancho = 0;
  if (e.graficos.mapa) e.graficos.mapa.setOption({ series: [{ id: 'grafo', zoom: 1, center: null }] });
  pintarMapa();
}

/** Los botones del zoom y la rueda: sin Ctrl, la rueda mueve la página; con Ctrl, acerca el mapa. En grande,
    la rueda sola acerca, como en Neo4j. */
export function configurarMapa() {
  for (const boton of document.querySelectorAll('.mapa-zoom [data-icono]')) boton.append(icono(boton.dataset.icono));
  $('mapa-acercar').addEventListener('click', () => acercar(1.25));
  $('mapa-alejar').addEventListener('click', () => acercar(0.8));
  $('mapa-reordenar').addEventListener('click', reordenar);
  const rueda = (ev) => {
    if (e.panelGrande === 'mapa') return;
    if (ev.ctrlKey || ev.metaKey) ev.preventDefault();
    else ev.stopPropagation();
  };
  for (const tipo of ['wheel', 'mousewheel']) $('grafico-mapa').addEventListener(tipo, rueda, { capture: true, passive: false });
}

// ---- El costado: el resumen del mapa o el detalle de lo elegido ----

function pintarCostado(red) {
  const elegido = mapa.elegido;
  let contenido;
  if (!elegido) contenido = costadoResumen(red);
  else if (elegido.tipo === 'nodo') contenido = costadoEquipo(red, red.nodos.find((n) => n.id === elegido.id));
  else contenido = costadoFlecha(red.aristas.find((a) => claveDe(a) === elegido.clave));
  vaciar($('mapa-detalle'), ...contenido);
}

function costadoResumen(red) {
  const cuantos = (area) => red.nodos.filter((n) => n.area === area).length;
  return [
    el('h4', {}, 'Equipos'),
    el('ul', { class: 'mapa-llaves' },
      red.areas.map((area) => el('li', {}, el('i', { class: 'punto', style: `background:var(${colorDeArea(area)})` }), el('span', {}, area), el('b', {}, numero(cuantos(area))))),
      el('li', {}, el('i', { class: 'anillo' }), el('span', {}, 'Con alertas'), el('b', {}, numero(red.nodos.filter((n) => n.severidad).length)))),
    el('h4', {}, 'Flechas'),
    el('ul', { class: 'mapa-llaves' },
      el('li', {}, el('i', { class: 'linea', style: 'background:var(--arista)' }), el('span', {}, 'Sin alerta')),
      SEVERIDADES.map((s) => el('li', {}, el('i', { class: `linea${s.id === 'baja' ? ' punteada' : ''}`, style: `background:var(${s.color})` }), el('span', {}, `Alerta ${s.nombre.toLowerCase()}`)))),
    el('p', { class: 'nota' }, 'Elegí un equipo o una flecha para ver el detalle.'),
  ];
}

function cabecera(llave, titulo, sub) {
  return el('div', { class: 'mapa-cabecera' },
    el('div', {}, el('h4', {}, llave, titulo), el('p', {}, sub)),
    el('button', { type: 'button', class: 'boton-icono', 'aria-label': 'Cerrar el detalle', title: 'Cerrar', onclick: () => elegir(null) }, icono('cruz')));
}

const dato = (nombre, valor) => el('div', {}, el('dt', {}, nombre), el('dd', {}, valor));

function costadoEquipo(red, n) {
  if (!n) return [];
  const vecinos = [
    ...red.aristas.filter((a) => a.origen === n.id).map((a) => ({ id: a.destino, nombre: a.destinoNombre, sale: true, a })),
    ...red.aristas.filter((a) => a.destino === n.id).map((a) => ({ id: a.origen, nombre: a.origenNombre, sale: false, a })),
  ].sort((x, y) => y.a.conexiones - x.a.conexiones);
  return [
    cabecera(el('i', { class: 'punto', style: `background:var(${colorDeArea(n.area)})` }), n.nombre, n.ip === n.nombre ? n.area : `${n.ip} · ${n.area}`),
    el('dl', { class: 'mapa-cifras' }, dato('Conexiones', numero(n.conexiones)), dato('Sin respuesta', numero(n.fallidas))),
    el('h4', {}, `Habla con ${numero(vecinos.length)} ${vecinos.length === 1 ? 'equipo' : 'equipos'}`),
    el('ul', { class: 'mapa-lista' }, vecinos.slice(0, 8).map((v) => el('li', {}, el('button', {
      type: 'button',
      title: v.sale ? `${n.nombre} → ${v.nombre}` : `${v.nombre} → ${n.nombre}`,
      onclick: () => elegir({ tipo: 'nodo', id: v.id }),
    }, el('span', { class: 'mapa-sentido', 'aria-hidden': 'true' }, v.sale ? '→' : '←'), el('span', { class: 'mapa-nombre' }, v.nombre), el('span', { class: 'mapa-num' }, v.a.etiqueta))))),
    vecinos.length > 8 ? el('p', { class: 'nota' }, `y ${numero(vecinos.length - 8)} más`) : null,
    listaAlertas(n.alertas),
  ];
}

function costadoFlecha(a) {
  if (!a) return [];
  return [
    cabecera(null, `${a.origenNombre} → ${a.destinoNombre}`, a.servicios.map((s) => s.toUpperCase()).join(' · ') || 'Sin servicio identificado'),
    el('dl', { class: 'mapa-cifras' }, dato('Conexiones', numero(a.conexiones)), dato('Sin respuesta', numero(a.fallidas)), dato('Datos', bytes(a.bytes))),
    listaAlertas(a.alertas),
  ];
}

function listaAlertas(alertas) {
  if (!alertas.length) return el('p', { class: 'nota' }, `Sin alertas ${fraseRango()}.`);
  return [
    el('h4', {}, alertas.length === 1 ? '1 alerta' : `${numero(alertas.length)} alertas`),
    el('ul', { class: 'mapa-lista' }, alertas.slice(0, 6).map((a) => el('li', {}, el('button', { type: 'button', onclick: () => abrirDetalle(a.id) },
      chipSeveridad(a.severidad), el('span', { class: 'mapa-nombre' }, tipoCorto(a)), el('span', { class: 'mapa-num' }, fechaHora(a.t)))))),
    alertas.length > 6 ? el('p', { class: 'nota' }, `y ${numero(alertas.length - 6)} más`) : null,
  ];
}
