// Cada panel del tablero con su menú, como en Grafana: verlo en grande, ver sus datos en una tabla, bajarlos
// en CSV, bajar la imagen del gráfico y copiar el enlace que lo abre en grande. Con el mouse sobre un
// panel, la tecla V lo agranda y la I muestra sus datos; Esc vuelve.

import { el, icono, numero, vaciar } from './util.js';
import { $, cuandoRefresquenTablas, e } from './comun.js';
import { abrirMenu } from './menu.js';
import { colores } from './graficos.js';
import { avisoInferior } from './hojas.js';
import { enlaceCon, escribirUrl, nombreRango } from './tiempo.js';
import { abrirFilaDe } from './filas.js';

// Los gráficos de cada panel.
const GRAFICOS = {
  estado: ['pieza-sensor', 'pieza-analisis', 'pieza-servidor'],
  servicios: ['servicios'],
  volumen: ['volumen'],
  fallidas: ['fallidas'],
  mapa: ['mapa'],
  'por-dia': ['por-dia'],
  'por-tipo': ['por-tipo'],
  equipos: ['equipos'],
  acierto: ['acierto'],
};

let inspeccionado = null;
let bajoMouse = null;
let alAgrandar = () => {};
let irAVista = () => {};

function panelDe(nombre) {
  const tarjeta = document.querySelector(`[data-panel="${nombre}"]`);
  if (!tarjeta || !GRAFICOS[nombre]) return null;
  const titulo = tarjeta.querySelector('.tarjeta-titulo');
  return { nombre, tarjeta, titulo: titulo ? titulo.textContent : nombre, graficos: GRAFICOS[nombre], vista: tarjeta.closest('section[id^="vista-"]').id.replace('vista-', '') };
}

/** El horario de los datos del panel, para el inspector. */
function horarioDe(panel) {
  if (panel.nombre === 'estado') return 'Última hora';
  if (panel.vista === 'resumen') return $('sub-resumen').textContent;
  return nombreRango();
}

// ---- En grande ----

export function verEnGrande(nombre) {
  const panel = panelDe(nombre);
  if (!panel) return;
  salirDeGrande(false);
  irAVista(panel.vista);
  abrirFilaDe(panel.tarjeta);
  panel.tarjeta.closest('section[id^="vista-"]').classList.add('modo-panel');
  panel.tarjeta.classList.add('panel-grande');
  const volver = el('button', { type: 'button', class: 'boton-secundario boton-volver', onclick: () => salirDeGrande() }, icono('volver'), 'Volver al tablero');
  panel.tarjeta.querySelector('.tarjeta-acciones').prepend(volver);
  e.panelGrande = nombre;
  escribirUrl();
  window.scrollTo(0, 0);
  alAgrandar(nombre);
  volver.focus();
}

/** Vuelve al tablero completo; devuelve si había un panel en grande. */
export function salirDeGrande(escribir = true) {
  const nombre = e.panelGrande;
  if (!nombre) return false;
  e.panelGrande = null;
  for (const seccion of document.querySelectorAll('.modo-panel')) seccion.classList.remove('modo-panel');
  const panel = panelDe(nombre);
  if (panel) {
    panel.tarjeta.classList.remove('panel-grande');
    const volver = panel.tarjeta.querySelector('.boton-volver');
    if (volver) volver.remove();
  }
  if (escribir) escribirUrl();
  alAgrandar(nombre);
  if (panel && escribir) panel.tarjeta.scrollIntoView({ block: 'nearest' });
  return true;
}

// ---- Los datos ----

function tablaDe(datos) {
  const desdeNumericas = datos.columnas.length - datos.numericas;
  const clase = (i) => (i >= desdeNumericas ? 'num' : null);
  return el('div', { class: 'inspector-tabla' }, el('table', { class: 'tabla' },
    el('thead', {}, el('tr', {}, datos.columnas.map((col, i) => el('th', { class: clase(i) }, col)))),
    el('tbody', {}, datos.filas.slice(0, 2000).map((fila) => el('tr', {}, fila.map((celda, i) => el('td', { class: clase(i) }, celda)))))));
}

function pintarInspector() {
  const panel = panelDe(inspeccionado);
  const lugar = $('inspector');
  if (!panel) return;
  const datos = e.tablas[panel.nombre];
  const filas = datos ? datos.filas.length : 0;
  const tablaVieja = lugar.querySelector('.inspector-tabla');
  const desplazamiento = tablaVieja ? tablaVieja.scrollTop : 0;
  vaciar(lugar,
    el('div', { class: 'detalle-cabecera' },
      el('div', {},
        el('p', {}, 'Datos del panel'),
        el('h2', { id: 'inspector-titulo' }, panel.titulo),
        el('p', {}, `${horarioDe(panel)} · ${numero(filas)} ${filas === 1 ? 'fila' : 'filas'}`)),
      el('button', { type: 'button', class: 'boton-icono', 'aria-label': 'Cerrar', onclick: () => cerrarInspector() }, icono('cruz'))),
    el('div', { class: 'inspector-acciones' },
      el('button', { type: 'button', class: 'boton-secundario', onclick: () => bajarCsv(panel.nombre) }, icono('descargar'), 'Descargar CSV')),
    filas ? tablaDe(datos) : el('p', { class: 'nota' }, 'No hay datos en este horario.'));
  const tablaNueva = lugar.querySelector('.inspector-tabla');
  if (tablaNueva) tablaNueva.scrollTop = desplazamiento;
}

export function inspeccionar(nombre) {
  if (!panelDe(nombre)) return;
  inspeccionado = nombre;
  pintarInspector();
  $('velo-inspector').hidden = false;
  $('inspector').hidden = false;
  $('inspector').focus();
}

/** Cierra los datos de un panel; devuelve si estaban abiertos. */
export function cerrarInspector() {
  if ($('inspector').hidden) return false;
  $('inspector').hidden = true;
  $('velo-inspector').hidden = true;
  const panel = panelDe(inspeccionado);
  inspeccionado = null;
  const boton = panel && panel.tarjeta.querySelector('.boton-panel');
  if (boton) boton.focus();
  return true;
}

// ---- Bajar archivos ----

/** «zonda-volumen-2026-10-31_0615». */
function nombreArchivo(nombre) {
  const f = new Date();
  const dos = (n) => String(n).padStart(2, '0');
  return `zonda-${nombre}-${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}_${dos(f.getHours())}${dos(f.getMinutes())}`;
}

function bajar(archivo, contenido) {
  const url = URL.createObjectURL(contenido);
  const enlace = el('a', { href: url, download: archivo, hidden: true });
  document.body.append(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

/** CSV con punto y coma, como lo abre Excel en castellano; con los valores sin unidades si el panel los da. */
function bajarCsv(nombre) {
  const datos = e.tablas[nombre];
  if (!datos) { avisoInferior('Todavía no hay datos para bajar'); return; }
  const fuente = datos.csv || datos;
  const celda = (valor) => {
    const texto = String(valor ?? '');
    return /[";\r\n]/.test(texto) ? `"${texto.replace(/"/g, '""')}"` : texto;
  };
  const lineas = [fuente.columnas, ...fuente.filas].map((fila) => fila.map(celda).join(';'));
  bajar(`${nombreArchivo(nombre)}.csv`, new Blob(['﻿', lineas.join('\r\n')], { type: 'text/csv;charset=utf-8' }));
}

function bajarImagen(nombre) {
  const grafico = e.graficos[GRAFICOS[nombre][0]];
  if (!grafico) return;
  const url = grafico.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: colores()['--superficie'] });
  const binario = atob(url.split(',')[1]);
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i += 1) bytes[i] = binario.charCodeAt(i);
  bajar(`${nombreArchivo(nombre)}.png`, new Blob([bytes], { type: 'image/png' }));
}

async function copiarEnlace(nombre) {
  try {
    await navigator.clipboard.writeText(enlaceCon({ ver: nombre }));
    avisoInferior('Enlace copiado: abre este panel en grande');
  } catch (_) {
    avisoInferior('No se pudo copiar el enlace');
  }
}

// ---- El menú ----

function abrirMenuDe(boton) {
  const nombre = boton.dataset.menuPanel;
  const grande = e.panelGrande === nombre;
  const items = [
    grande ? { texto: 'Volver al tablero', icono: 'reducir', atajo: 'Esc', accion: () => salirDeGrande() }
      : { texto: 'Ver en grande', icono: 'ampliar', atajo: 'V', accion: () => verEnGrande(nombre) },
    { texto: 'Ver datos', icono: 'tabla', atajo: 'I', accion: () => inspeccionar(nombre) },
    { texto: 'Descargar CSV', icono: 'descargar', accion: () => bajarCsv(nombre) },
  ];
  if (GRAFICOS[nombre].length === 1) items.push({ texto: 'Descargar imagen', icono: 'imagen', accion: () => bajarImagen(nombre) });
  items.push({ separador: true }, { texto: 'Copiar enlace', icono: 'enlace', accion: () => copiarEnlace(nombre) });
  abrirMenu(boton, items);
}

/** V y I con el mouse sobre un panel, como en Grafana. Devuelve si la tecla era para un panel. */
export function teclaDePanel(ev) {
  const escribiendo = ev.target instanceof Element && ev.target.closest('input, textarea, select, [contenteditable="true"]');
  if (ev.ctrlKey || ev.metaKey || ev.altKey || escribiendo) return false;
  const nombre = bajoMouse || e.panelGrande;
  if (!nombre) return false;
  const tecla = ev.key.toLowerCase();
  if (tecla === 'v') {
    if (e.panelGrande === nombre) salirDeGrande(); else verEnGrande(nombre);
    return true;
  }
  if (tecla === 'i') { inspeccionar(nombre); return true; }
  return false;
}

/** alAgrandar(nombre): repintar lo que depende del tamaño; irAVista(vista): pasar a la vista del panel. */
export function configurarPaneles(opciones) {
  alAgrandar = opciones.alAgrandar;
  irAVista = opciones.irAVista;
  for (const boton of document.querySelectorAll('[data-menu-panel]')) {
    boton.append(icono('menu'));
    boton.addEventListener('click', () => abrirMenuDe(boton));
  }
  for (const tarjeta of document.querySelectorAll('[data-panel]')) {
    tarjeta.addEventListener('mouseenter', () => { bajoMouse = tarjeta.dataset.panel; });
    tarjeta.addEventListener('mouseleave', () => { if (bajoMouse === tarjeta.dataset.panel) bajoMouse = null; });
  }
  $('velo-inspector').addEventListener('click', () => cerrarInspector());
  cuandoRefresquenTablas((nombres) => { if (inspeccionado && nombres.includes(inspeccionado) && !$('inspector').hidden) pintarInspector(); });
}
