// El tablero web de Zonda (decisiones 49, 50 y 53): la vista de escritorio de la red, con lo mismo que la app.
// Usa la misma API y el mismo acceso que un teléfono: se entra con un código de pareo de un solo uso y el
// navegador queda como un dispositivo más, con su perfil y su vencimiento.

import { api, borrarAcceso, guardarAcceso, horaConSegundos, icono, leerAcceso, vaciar, el } from './util.js';
import { $, alCambiarAlertas, cuandoExpire, e } from './comun.js';
import {
  actualizarAhora, actualizarPiezas, actualizarSalud, actualizarTrafico, avisarNuevas, pintarEstado, pintarGraficosPiezas,
  pintarLeyendas, pintarSinRevisar, pintarTrafico, pintarUltimas,
} from './vivo.js';
import { actualizarPares, configurarMapa, pintarMapa, soltarElegido } from './mapa.js';
import { abrirDetalle, cerrarDetalle, pintarAlertas, sincronizarAlertas, verMas } from './alertas.js';
import { cambiarPeriodo, descargarImagen, guardarPdf, pintarResumen } from './resumen.js';
import { actualizarAjustes, actualizarPersonas, configurarAjustes, pintarAjustes } from './ajustes.js';
import { cadaCuanto, cerrarSelector, configurarTiempo, leerUrl, pintarBarra } from './tiempo.js';
import { cerrarInspector, configurarPaneles, salirDeGrande, teclaDePanel, verEnGrande } from './paneles.js';
import { cerrarMenu, configurarMenu } from './menu.js';
import { configurarFilas, filaAbierta } from './filas.js';

const CLAVE_TEMA = 'zonda.tablero.tema';
const tareas = {};
let ticker = null;

// ---- Tema ----

function aplicarTema(tema) {
  document.documentElement.dataset.tema = tema;
  const boton = $('boton-tema');
  const siguiente = tema === 'oscuro' ? 'claro' : 'oscuro';
  boton.setAttribute('aria-label', `Cambiar a modo ${siguiente}`);
  boton.title = `Cambiar a modo ${siguiente}`;
  vaciar(boton, icono(tema === 'oscuro' ? 'sol' : 'luna'));
}

function temaGuardado() {
  try { return localStorage.getItem(CLAVE_TEMA) === 'oscuro' ? 'oscuro' : 'claro'; } catch (_) { return 'claro'; }
}

function cambiarTema() {
  const tema = document.documentElement.dataset.tema === 'oscuro' ? 'claro' : 'oscuro';
  try { localStorage.setItem(CLAVE_TEMA, tema); } catch (_) { /* solo por esta vez */ }
  aplicarTema(tema);
  // Al limpiar un gráfico se pierde el pincel de arrastrar para acercar: tiempo.js lo vuelve a armar.
  for (const grafico of Object.values(e.graficos)) { grafico.clear(); grafico.zondaPincel = false; }
  pintarLeyendas();
  pintarTrafico();
  pintarMapa();
  pintarGraficosPiezas();
  if (e.vista === 'resumen') pintarResumen();
}

/** El único «?» de la tarjeta del estado del sistema: abre y cierra su ayuda. */
function alternarAyudaEstado() {
  const boton = $('boton-ayuda-estado');
  const abierta = boton.getAttribute('aria-expanded') === 'true';
  boton.setAttribute('aria-expanded', String(!abierta));
  $('ayuda-estado').hidden = abierta;
}

// ---- Ingreso ----

/** "Tablero web · Chrome en Windows": así cada navegador se distingue en Personas y en el registro. */
function nombreDeEsteNavegador() {
  const ua = navigator.userAgent;
  const navegador = /Edg\//.test(ua) ? 'Edge' : /OPR\//.test(ua) ? 'Opera' : /Firefox\//.test(ua) ? 'Firefox' : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : 'navegador';
  const sistema = /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iPhone' : /Windows/.test(ua) ? 'Windows' : /Mac OS/.test(ua) ? 'Mac' : /Linux/.test(ua) ? 'Linux' : '';
  return `Tablero web · ${navegador}${sistema ? ` en ${sistema}` : ''}`;
}

function normalizarCodigo(texto) {
  const limpio = texto.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
  return limpio.length > 4 ? `${limpio.slice(0, 4)}-${limpio.slice(4)}` : limpio;
}

async function mostrarIngreso(mensaje) {
  $('tablero').hidden = true;
  $('ingreso').hidden = false;
  const error = $('ingreso-error');
  error.hidden = !mensaje;
  error.textContent = mensaje || '';
  $('codigo').value = '';
  $('codigo').focus();
  try {
    const salud = await api('/salud');
    $('ingreso-red').textContent = `Red: ${salud.red.nombre} · servidor ${salud.version}`;
  } catch (_) {
    $('ingreso-red').textContent = 'El servidor no contesta';
  }
}

async function entrar(evento) {
  evento.preventDefault();
  const codigo = normalizarCodigo($('codigo').value);
  const error = $('ingreso-error');
  if (codigo.replace('-', '').length !== 8) {
    error.hidden = false;
    error.textContent = 'El código tiene 8 letras y números, por ejemplo ABCD-2345.';
    return;
  }
  const boton = $('boton-entrar');
  boton.disabled = true;
  boton.textContent = 'Entrando…';
  try {
    const salida = await api('/pareo', { metodo: 'POST', cuerpo: { codigo, nombreDispositivo: nombreDeEsteNavegador() } });
    e.acceso = { token: salida.token, vence: salida.vence, perfil: salida.perfil, persona: salida.persona, red: salida.red, dispositivoId: salida.dispositivoId };
    guardarAcceso(e.acceso);
    arrancarTablero();
  } catch (falla) {
    error.hidden = false;
    error.textContent = falla.estado === 400 ? 'Ese código no existe. Revisá las letras o pedí uno nuevo.'
      : falla.estado === 410 ? 'Ese código venció o ya se usó. Pedí uno nuevo.'
        : falla.estado === 0 ? 'El servidor no contesta. Revisá que esté andando.'
          : (falla.message || 'No se pudo entrar.');
  } finally {
    boton.disabled = false;
    boton.textContent = 'Entrar';
  }
}

async function salir() {
  try { await api('/dispositivos/actual', { token: e.acceso.token, metodo: 'DELETE' }); } catch (_) { /* igual se olvida el acceso */ }
  terminarSesion();
}

function terminarSesion(mensaje) {
  detenerTicker();
  borrarAcceso();
  e.acceso = null;
  e.alertas.clear();
  e.cursor = null;
  e.sincronizada = false;
  cerrarDetalle();
  mostrarIngreso(mensaje);
}

// ---- Consultas periódicas ----

const TAREAS = {
  salud: { cada: () => 5000, correr: actualizarSalud },
  alertas: { cada: () => 5000, correr: sincronizarAlertas },
  ahora: { cada: () => 5000, correr: actualizarAhora },
  piezas: { cada: () => 30000, correr: actualizarPiezas },
  // El tráfico y el mapa, cada lo que se eligió en la barra del horario (nunca, si está apagada). Como en
  // Grafana, el mapa no se pide mientras su fila está plegada; al desplegarla, se pide en el momento.
  trafico: { cada: () => cadaCuanto(), correr: actualizarTrafico },
  pares: { cada: () => (filaAbierta('comunicaciones') ? Math.max(10000, cadaCuanto()) : Infinity), correr: actualizarPares },
  personas: { cada: () => (e.vista === 'ajustes' ? 15000 : 60000), correr: () => (e.vista === 'ajustes' ? actualizarAjustes() : actualizarPersonas()) },
};

function detenerTicker() {
  if (ticker) clearInterval(ticker);
  ticker = null;
}

function arrancarTicker() {
  detenerTicker();
  ticker = setInterval(tic, 1000);
  tic();
}

function tic() {
  if (!e.acceso) return;
  const ahora = Date.now();
  for (const [nombre, tarea] of Object.entries(TAREAS)) {
    const estado = tareas[nombre] || (tareas[nombre] = { ultima: 0, corriendo: false });
    // La primera vez corre siempre, aunque la actualización automática esté apagada.
    if (estado.corriendo || (estado.ultima && ahora - estado.ultima < tarea.cada())) continue;
    estado.corriendo = true;
    estado.ultima = ahora;
    tarea.correr().catch(() => { /* se muestra en el indicador y se reintenta en la vuelta siguiente */ }).finally(() => { estado.corriendo = false; });
  }
  pintarEnVivo();
  // El dato de cada pieza del estado cuenta los segundos («Última revisión hace 12 s»).
  pintarEstado();
}

function pintarEnVivo() {
  const indicador = $('en-vivo');
  const texto = $('en-vivo-texto');
  if (!e.ultimaRespuesta) {
    indicador.dataset.estado = e.servidorCaido ? 'caido' : 'lento';
    texto.textContent = e.servidorCaido ? 'El servidor no contesta' : 'Conectando…';
    return;
  }
  const segundos = Math.round((Date.now() - e.ultimaRespuesta) / 1000);
  indicador.dataset.estado = e.servidorCaido || segundos > 60 ? 'caido' : segundos > 15 ? 'lento' : 'ok';
  texto.textContent = e.servidorCaido ? `Sin respuesta desde las ${horaConSegundos(e.ultimaRespuesta)}`
    : segundos <= 2 ? 'En vivo · recién' : `En vivo · hace ${segundos} s`;
  const banda = $('banda-error');
  banda.hidden = !(e.servidorCaido || segundos > 60);
  banda.textContent = 'El servidor de alertas no contesta. El tablero muestra lo último que recibió y sigue intentando.';
}

// ---- Vistas ----

function cambiarVista(vista, { mantenerPanel = false } = {}) {
  if (!mantenerPanel) salirDeGrande();
  cerrarInspector();
  e.vista = vista;
  for (const boton of document.querySelectorAll('.pestania, #barra-inferior button')) boton.setAttribute('aria-selected', String(boton.dataset.vista === vista));
  for (const nombre of ['vivo', 'alertas', 'resumen', 'ajustes']) $(`vista-${nombre}`).hidden = nombre !== vista;
  if (vista === 'alertas') pintarAlertas();
  if (vista === 'resumen') pintarResumen();
  if (vista === 'ajustes') { pintarAjustes(); actualizarAjustes().catch(() => {}); }
  window.scrollTo(0, 0);
  requestAnimationFrame(() => {
    for (const grafico of Object.values(e.graficos)) grafico.resize();
    // Los gráficos de En vivo que no se pudieron armar mientras no se veían.
    if (vista === 'vivo') pintarVivo();
  });
}

/** Los gráficos de En vivo, con el tamaño que tienen ahora (al volver a la vista o al desplegar una fila). */
function pintarVivo() {
  pintarGraficosPiezas();
  pintarTrafico();
  pintarMapa();
}

/** Al desplegar una fila se dibuja lo suyo; la del mapa, además, lo pide en el momento. */
function alCambiarFila(fila, abierta) {
  if (!abierta) return;
  pintarVivo();
  if (fila === 'comunicaciones') {
    tareas.pares = { ultima: Date.now(), corriendo: false };
    actualizarPares().catch(() => {});
  }
}

const CON_HORARIO = ['servicios', 'volumen', 'fallidas', 'mapa'];

/** Cambió el horario (o se tocó Actualizar): se piden ya el tráfico y el mapa, y los relojes arrancan de nuevo. */
function actualizarHorario() {
  for (const nombre of CON_HORARIO) $(`grafico-${nombre}`).classList.add('cargando');
  Promise.allSettled([actualizarTrafico(), actualizarPares()]).finally(() => {
    for (const nombre of CON_HORARIO) $(`grafico-${nombre}`).classList.remove('cargando');
  });
  tareas.trafico = { ultima: Date.now(), corriendo: false };
  tareas.pares = { ultima: Date.now(), corriendo: false };
}

function actualizarTodo() {
  actualizarHorario();
  actualizarPiezas().catch(() => {});
  actualizarSalud().catch(() => {});
}

/** Esc cierra lo de más arriba: el menú, el selector del horario, los datos de un panel, el detalle de una
    alerta, lo elegido en el mapa y por último el panel en grande. */
function alApretarTecla(ev) {
  if (ev.key === 'Escape') {
    if (document.querySelector('.hoja-velo')) return;
    if (cerrarMenu(true) || cerrarSelector(true) || cerrarInspector()) { ev.preventDefault(); return; }
    if (e.detalleAbierto) { cerrarDetalle(); return; }
    if (soltarElegido() || salirDeGrande()) ev.preventDefault();
    return;
  }
  if (!document.querySelector('.hoja-velo') && $('detalle').hidden && teclaDePanel(ev)) ev.preventDefault();
}

/** Cuando cambian las alertas (sincronización, una revisión o un responsable), se vuelve a pintar lo que las muestra. */
function alCambiar(nuevas) {
  pintarUltimas();
  if (e.trafico) pintarTrafico();
  if (e.pares) pintarMapa();
  if (e.vista === 'alertas') pintarAlertas();
  if (e.vista === 'resumen') pintarResumen();
  if (e.detalleAbierto && e.alertas.has(e.detalleAbierto) && !$('detalle').hidden) abrirDetalle(e.detalleAbierto, false);
  if (nuevas.length) avisarNuevas(nuevas);
}

async function cargarAreas() {
  try {
    const respuesta = await fetch('areas.json', { cache: 'no-store' });
    const datos = await respuesta.json();
    e.areas = Array.isArray(datos.areas) ? datos.areas.filter((a) => a && a.nombre && Array.isArray(a.redes)) : [];
  } catch (_) {
    e.areas = [];
  }
}

async function arrancarTablero() {
  $('ingreso').hidden = true;
  $('tablero').hidden = false;
  e.perfil = e.acceso.perfil;
  e.miId = e.acceso.dispositivoId || null;
  vaciar($('red-actual'), e.acceso.red ? e.acceso.red.nombre : 'Red', el('small', {}, `${e.acceso.persona || 'Tablero web'} · ${e.perfil === 'administrador' ? 'administrador' : 'analista'}`));
  pintarLeyendas();
  pintarSinRevisar();
  pintarEstado();
  await cargarAreas();
  actualizarPersonas().catch(() => {});
  arrancarTicker();
}

function iniciar() {
  aplicarTema(temaGuardado());
  cuandoExpire(terminarSesion);
  alCambiarAlertas(alCambiar);
  configurarAjustes({ salir, cambiarTema });
  const panelDeLaUrl = leerUrl();
  configurarMenu();
  configurarFilas({ alCambiar: alCambiarFila });
  configurarMapa();
  configurarTiempo({ alCambiarRango: actualizarHorario, actualizarAhora: actualizarTodo });
  configurarPaneles({
    // El mapa y el estado del sistema se arman según el tamaño del panel; los otros gráficos se ajustan solos.
    alAgrandar: (nombre) => {
      if (nombre === 'mapa') pintarMapa();
      if (nombre === 'estado') pintarGraficosPiezas();
    },
    irAVista: (vista) => { if (e.vista !== vista) cambiarVista(vista, { mantenerPanel: true }); },
  });
  $('form-ingreso').addEventListener('submit', entrar);
  $('codigo').addEventListener('input', (ev) => {
    const valor = normalizarCodigo(ev.target.value);
    if (valor !== ev.target.value) ev.target.value = valor;
  });
  $('boton-tema').addEventListener('click', cambiarTema);
  $('boton-ayuda-estado').addEventListener('click', alternarAyudaEstado);
  $('boton-salir').addEventListener('click', salir);
  for (const boton of document.querySelectorAll('.pestania, #barra-inferior button')) boton.addEventListener('click', () => cambiarVista(boton.dataset.vista));
  for (const boton of $('periodos').querySelectorAll('button')) boton.addEventListener('click', () => cambiarPeriodo(boton.dataset.dias));
  $('ver-mas').addEventListener('click', verMas);
  $('descargar-imagen').addEventListener('click', descargarImagen);
  $('descargar-pdf').addEventListener('click', guardarPdf);
  $('velo').addEventListener('click', cerrarDetalle);
  document.addEventListener('keydown', alApretarTecla);
  document.addEventListener('visibilitychange', () => {
    if (!e.acceso) return;
    if (document.hidden) detenerTicker(); else arrancarTicker();
  });
  setInterval(() => { if (e.acceso && e.alertas.size) pintarUltimas(); }, 30000);
  // El nombre de un rango que termina ahora no cambia, pero sí sus horas (en el globito del botón).
  setInterval(() => { if (e.acceso) pintarBarra(); }, 30000);

  e.acceso = leerAcceso();
  if (e.acceso) {
    arrancarTablero();
    if (panelDeLaUrl) verEnGrande(panelDeLaUrl);
  } else {
    mostrarIngreso();
  }
}

iniciar();
