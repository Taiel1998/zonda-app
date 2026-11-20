// El panel de redes de una consultora (decisión 64): desde un mismo navegador, varias empresas, como la pantalla
// Redes de la app. Cada red se parea con su propio código de un uso, estando en su red o conectado a su VPN, y
// queda como un navegador más de esa red. El navegador le pregunta a cada servidor directo, con su token: nada
// pasa por un servidor central. Lo sirve `zonda panel` en la computadora de la consultora (app/panel.py).

import { api, el, horaConSegundos, icono, numero, vaciar } from './util.js';

const CLAVE_REDES = 'zonda.panel.redes';
const CLAVE_TEMA = 'zonda.panel.tema';
const CADA = 15000;
const ESPERA = 8000;
// Cuánto espera el panel a que el tablero que abrió le pida el acceso.
const ESPERA_TABLERO = 60000;

const $ = (id) => document.getElementById(id);

// Lo que dijo cada servidor la última vez, por dirección: { salud, estado, falla, en }.
const estados = new Map();
// La red cuyo «Quitar» espera confirmación.
let confirmando = null;

// ---- Los accesos, en el almacenamiento de este navegador ----

/** Las redes pareadas, en el orden en que se sumaron. Cada una: { url, token, vence, perfil, persona, red,
    dispositivoId, agregada }. La clave es la dirección: dos empresas pueden tener la misma identidad de red. */
function leerRedes() {
  try {
    const redes = JSON.parse(localStorage.getItem(CLAVE_REDES) || '[]');
    return Array.isArray(redes) ? redes.filter((r) => r && typeof r.url === 'string' && typeof r.token === 'string') : [];
  } catch (_) { return []; }
}

function guardarRedes(redes) {
  try { localStorage.setItem(CLAVE_REDES, JSON.stringify(redes)); } catch (_) { avisar('Este navegador no deja guardar los accesos: se pierden al cerrar la pestaña.', 'error'); }
}

let redes = leerRedes();

// ---- Tema, como el tablero: el del sistema, o el que se eligió con el botón ----

const oscuroDelSistema = window.matchMedia('(prefers-color-scheme: dark)');

function temaGuardado() {
  try { return localStorage.getItem(CLAVE_TEMA); } catch (_) { return null; }
}

function aplicarTema(tema) {
  document.documentElement.dataset.tema = tema;
  const boton = $('boton-tema');
  const siguiente = tema === 'oscuro' ? 'claro' : 'oscuro';
  boton.setAttribute('aria-label', `Cambiar a modo ${siguiente}`);
  boton.title = `Cambiar a modo ${siguiente}`;
  vaciar(boton, icono(tema === 'oscuro' ? 'sol' : 'luna'));
}

const temaQueToca = () => {
  const guardado = temaGuardado();
  if (guardado === 'claro' || guardado === 'oscuro') return guardado;
  return oscuroDelSistema.matches ? 'oscuro' : 'claro';
};

function cambiarTema() {
  const tema = document.documentElement.dataset.tema === 'oscuro' ? 'claro' : 'oscuro';
  try { localStorage.setItem(CLAVE_TEMA, tema); } catch (_) { /* solo por esta vez */ }
  aplicarTema(tema);
}

// ---- Avisos ----

let relojAviso = null;

function avisar(texto, tipo = 'ok') {
  const aviso = $('aviso');
  aviso.className = `aviso-inferior ${tipo}`;
  vaciar(aviso, el('span', { class: 'aviso-texto' }, el('span', { class: 'aviso-icono', 'aria-hidden': 'true' }, tipo === 'ok' ? '✓' : '!'), texto));
  aviso.hidden = false;
  clearTimeout(relojAviso);
  relojAviso = setTimeout(() => { aviso.hidden = true; }, 6000);
}

// ---- Agregar una red ----

/** «Tablero web · Panel de redes · Chrome en Windows»: así la empresa ve en Administración → Navegadores que este
    acceso es de un panel de redes, y desde qué navegador. */
function nombreDeEsteNavegador() {
  const ua = navigator.userAgent;
  const navegador = /Edg\//.test(ua) ? 'Edge' : /OPR\//.test(ua) ? 'Opera' : /Firefox\//.test(ua) ? 'Firefox' : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : 'navegador';
  const sistema = /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iPhone' : /Windows/.test(ua) ? 'Windows' : /Mac OS/.test(ua) ? 'Mac' : /Linux/.test(ua) ? 'Linux' : '';
  return `Tablero web · Panel de redes · ${navegador}${sistema ? ` en ${sistema}` : ''}`;
}

function normalizarCodigo(texto) {
  const limpio = texto.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
  return limpio.length > 4 ? `${limpio.slice(0, 4)}-${limpio.slice(4)}` : limpio;
}

/** La dirección del servidor, sin la barra final; sin esquema, https. null si no es una dirección. */
function normalizarDireccion(texto) {
  let limpio = texto.trim();
  if (!limpio) return null;
  if (!/^https?:\/\//i.test(limpio)) limpio = `https://${limpio}`;
  try {
    const url = new URL(limpio);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
    return `${url.origin}${url.pathname.replace(/\/+$/, '')}`;
  } catch (_) { return null; }
}

/** Lo que lleva el QR de pareo, «zonda:https://empresa.duckdns.org#K7M2-QX9P;vence=…», pegado en la dirección:
    llena los dos campos. */
function alPegarDireccion() {
  const campo = $('campo-direccion');
  const qr = /^zonda:([^#\s]+)#([A-Za-z0-9-]{8,9})/.exec(campo.value.trim());
  if (!qr) return;
  campo.value = qr[1];
  $('campo-codigo').value = normalizarCodigo(qr[2]);
}

function abrirFormulario(direccion = '') {
  const form = $('form-agregar');
  form.hidden = false;
  $('agregar-error').hidden = true;
  $('campo-direccion').value = direccion;
  $('campo-codigo').value = '';
  $('boton-agregar').hidden = true;
  // Sin ninguna red, el formulario es lo único que hay: no se cancela.
  $('boton-cancelar').hidden = !redes.length;
  (direccion ? $('campo-codigo') : $('campo-direccion')).focus();
}

function cerrarFormulario() {
  $('form-agregar').hidden = true;
  $('boton-agregar').hidden = false;
}

function mostrarError(texto) {
  const error = $('agregar-error');
  error.hidden = false;
  error.textContent = texto;
}

/** Como en la app: primero verifica que el servidor responda (GET /salud, sin token), para no gastar el código a
    ciegas; después lo canjea (POST /pareo) y guarda el token en este navegador. */
async function parear(evento) {
  evento.preventDefault();
  alPegarDireccion();
  const url = normalizarDireccion($('campo-direccion').value);
  const codigo = normalizarCodigo($('campo-codigo').value);
  if (!url) { mostrarError('Escribí la dirección del servidor de esa red, por ejemplo https://empresa.duckdns.org.'); return; }
  if (codigo.replace('-', '').length !== 8) { mostrarError('El código tiene 8 letras y números, por ejemplo ABCD-2345.'); return; }
  const boton = $('boton-parear');
  boton.disabled = true;
  boton.textContent = 'Agregando…';
  try {
    await api(`${url}/salud`, { espera: ESPERA });
    const salida = await api(`${url}/pareo`, { metodo: 'POST', cuerpo: { codigo, nombreDispositivo: nombreDeEsteNavegador(), tipo: 'navegador' } });
    const nueva = {
      url,
      token: salida.token,
      vence: salida.vence,
      perfil: salida.perfil,
      persona: salida.persona,
      red: salida.red,
      dispositivoId: salida.dispositivoId,
      agregada: new Date().toISOString(),
    };
    // Volver a parear una red que ya estaba (después de que le quitaron el acceso) la deja en su lugar.
    const antes = redes.findIndex((r) => r.url === url);
    if (antes >= 0) redes[antes] = nueva; else redes.push(nueva);
    guardarRedes(redes);
    cerrarFormulario();
    avisar(`${salida.red.nombre} quedó en el panel.`);
    pintar();
    consultar(nueva);
  } catch (falla) {
    mostrarError(falla.estado === 400 ? 'Ese código no existe. Revisá las letras o pedí uno nuevo.'
      : falla.estado === 410 ? 'Ese código venció o ya se usó. Pedí uno nuevo.'
        : falla.estado === 0 ? 'No se pudo conectar con ese servidor. Verificá la dirección, que estés en su red o conectado a su VPN, y que la empresa haya habilitado este panel.'
          : (falla.message || 'No se pudo agregar la red.'));
  } finally {
    boton.disabled = false;
    boton.textContent = 'Agregar';
  }
}

// ---- Lo que dice cada servidor ----

/** El estado del sistema (GET /salud con token) y las alertas sin revisar (GET /estado) de una red. */
async function consultar(red) {
  try {
    const [salud, estado] = await Promise.all([
      api(`${red.url}/salud`, { token: red.token, espera: ESPERA }),
      api(`${red.url}/estado`, { token: red.token, espera: ESPERA }),
    ]);
    estados.set(red.url, { salud, estado, falla: null, en: Date.now() });
    // El servidor sabe el nombre de la red y cuándo vence el acceso: si cambiaron, quedan al día.
    if (salud.red && (salud.red.nombre !== red.red.nombre || salud.tokenVence !== red.vence)) {
      red.red = salud.red;
      red.vence = salud.tokenVence || red.vence;
      guardarRedes(redes);
    }
  } catch (falla) {
    const anterior = estados.get(red.url);
    estados.set(red.url, { ...(anterior || {}), falla: falla.estado === 401 ? 'sin_acceso' : 'no_responde', en: Date.now() });
  }
  pintarRed(red);
  pintarActualizado();
}

const consultarTodas = () => Promise.all(redes.map(consultar));

// ---- Las tarjetas ----

// Como la tarjeta de la app y el tablero (decisión 48): verde si funciona, ámbar si el sensor ve parcialmente y
// roja si no funciona o no hay datos; el semáforo no lleva palabras (las dice para el lector de pantalla).
const LUZ = { funciona: 'funciona', parcial: 'parcial', detenido: 'detenido', sin_datos: 'detenido', no_responde: 'detenido' };
const COLOR_PUNTO = { funciona: 'var(--baja)', parcial: 'var(--media)', detenido: 'var(--alta)' };
const NOMBRE_ESTADO = { funciona: 'funciona', parcial: 'viendo parcialmente', detenido: 'detenido', sin_datos: 'sin datos', no_responde: 'no responde' };
const SEVERIDADES = [
  { id: 'alta', nombre: 'Altas', color: '--alta' },
  { id: 'media', nombre: 'Medias', color: '--media' },
  { id: 'baja', nombre: 'Bajas', color: '--baja' },
];

/** Cuánto pasó, en una sola unidad, como el tablero: 12 s, 24 min, 3 h, 2 días. */
function cuanto(ms) {
  const segundos = Math.max(0, Math.floor(ms / 1000));
  if (segundos < 60) return `${segundos} s`;
  if (segundos < 3600) return `${Math.floor(segundos / 60)} min`;
  if (segundos < 86400) return `${Math.floor(segundos / 3600)} h`;
  if (segundos < 2 * 86400) return '1 día';
  return `${Math.floor(segundos / 86400)} días`;
}

/** Las cuatro piezas con el mismo dato corto que el tablero; el firewall, solo si la red tiene uno (decisión 51). */
function piezasDe(info) {
  const respondio = info && !info.falla && info.salud;
  const sistema = respondio ? info.salud.sistema : null;
  const ahora = respondio ? Date.parse(info.salud.hora) : Date.now();
  const sensor = sistema ? sistema.sensor : 'sin_datos';
  const datoSensor = !sistema || sensor === 'sin_datos' ? 'Sin datos'
    : sensor === 'detenido' ? 'No escucha la red'
      : sensor === 'parcial' && sistema.perdidaCaptura != null ? `Pierde el ${numero(sistema.perdidaCaptura, 1)} % del tráfico`
        : sistema.ultimoFlujo ? `Último tráfico hace ${cuanto(ahora - Date.parse(sistema.ultimoFlujo))}` : 'Escuchando la red';
  const piezas = [
    { id: 'sensor', nombre: 'Sensor de red', estado: sensor, dato: datoSensor },
    {
      id: 'analisis',
      nombre: 'Análisis en vivo',
      estado: sistema ? sistema.analisis : 'sin_datos',
      dato: sistema && sistema.ultimaCorrida ? `Último análisis hace ${cuanto(ahora - Date.parse(sistema.ultimaCorrida))}` : 'Sin datos',
    },
    { id: 'servidor', nombre: 'Servidor de alertas', estado: respondio ? 'funciona' : 'no_responde', dato: respondio ? `Respondió a las ${horaConSegundos(info.en)}` : 'No responde' },
  ];
  const firewall = respondio && info.salud.firewall;
  if (firewall && firewall.habilitado) {
    piezas.push({
      id: 'firewall',
      nombre: 'Firewall',
      estado: firewall.conectado ? 'funciona' : 'no_responde',
      dato: firewall.ultimaConsulta ? `Última consulta hace ${cuanto(ahora - Date.parse(firewall.ultimaConsulta))}` : 'Sin datos',
    });
  }
  return piezas;
}

function pieza(p) {
  const luz = LUZ[p.estado] || 'detenido';
  return el('div', { class: 'red-pieza' },
    el('span', { class: 'pieza-nombre' }, el('i', { class: 'punto', style: `background:${COLOR_PUNTO[luz]}` }), el('span', {}, p.nombre)),
    el('span', { class: 'semaforo', role: 'img', 'aria-label': `${p.nombre}: ${NOMBRE_ESTADO[p.estado] || 'sin datos'}`, dataset: { estado: luz } },
      el('i', { class: 'luz roja' }), el('i', { class: 'luz verde' })),
    el('span', { class: 'red-pieza-dato' }, p.dato));
}

/** «2 altas sin revisar», «Tranquila» (sin altas ni medias), «No responde» o «Sin acceso», como la lista de la app. */
function resumenDe(info) {
  if (!info) return el('span', { class: 'etiqueta-rango' }, 'Consultando…');
  if (info.falla === 'sin_acceso') return el('span', { class: 'sev alta' }, 'Sin acceso');
  if (info.falla === 'no_responde') return el('span', { class: 'sev alta' }, 'No responde');
  const sin = info.estado.sinRevisar;
  if (sin.alta) return el('span', { class: 'sev alta' }, `${numero(sin.alta)} ${sin.alta === 1 ? 'alta' : 'altas'} sin revisar`);
  if (sin.media) return el('span', { class: 'sev media' }, `${numero(sin.media)} ${sin.media === 1 ? 'media' : 'medias'} sin revisar`);
  return el('span', { class: 'sev baja' }, 'Tranquila');
}

function contadores(info) {
  const sin = info && !info.falla && info.estado ? info.estado.sinRevisar : null;
  return el('div', { class: 'contadores red-contadores', role: 'group', 'aria-label': 'Alertas sin revisar' },
    SEVERIDADES.map((s) => el('div', { class: 'contador red-contador' },
      el('span', { class: 'contador-titulo' }, el('span', { class: 'punto', style: `background:var(${s.color})` }), s.nombre),
      el('span', { class: 'contador-numero' }, sin ? numero(sin[s.id]) : '–'))));
}

function aviso(info, red) {
  if (!info || !info.falla) return null;
  if (info.falla === 'sin_acceso') {
    return el('p', { class: 'red-aviso' }, 'La empresa le quitó el acceso a este navegador o venció. Para volver a verla hace falta un código nuevo de esa red.');
  }
  return el('p', { class: 'red-aviso' }, `El servidor de ${red.red.nombre} no responde. Verificá que estés en su red o conectado a su VPN.`);
}

function acciones(red, info) {
  if (confirmando === red.url) {
    return el('div', { class: 'red-acciones red-confirmar' },
      el('span', {}, `¿Quitar ${red.red.nombre} de este panel? Este navegador pierde el acceso a esa red.`),
      el('span', { class: 'red-botones' },
        el('button', { type: 'button', class: 'boton-secundario', onclick: () => { confirmando = null; pintarRed(red); } }, 'Cancelar'),
        el('button', { type: 'button', class: 'boton-peligro', onclick: () => quitar(red) }, 'Quitar')));
  }
  const sinAcceso = info && info.falla === 'sin_acceso';
  return el('div', { class: 'red-acciones' },
    sinAcceso
      ? el('button', { type: 'button', class: 'boton-principal chico', onclick: () => abrirFormulario(red.url) }, 'Volver a parear')
      : el('button', { type: 'button', class: 'boton-principal chico', onclick: () => abrirTablero(red), title: `Abrir el tablero de ${red.red.nombre} en una pestaña nueva` }, 'Abrir tablero'),
    el('button', { type: 'button', class: 'boton-texto peligro', onclick: () => { confirmando = red.url; pintarRed(red); } }, 'Quitar de este panel'));
}

function tarjeta(red) {
  const info = estados.get(red.url);
  const sin = info && !info.falla && info.estado ? info.estado.sinRevisar : null;
  const atencion = info && (info.falla || (sin && sin.alta));
  const sinAcceso = Boolean(info && info.falla === 'sin_acceso');
  const host = (() => { try { return new URL(red.url).host; } catch (_) { return red.url; } })();
  const quien = [red.persona, red.perfil === 'administrador' ? 'administrador' : 'analista'].filter(Boolean).join(' · ');
  return el('article', { class: `tarjeta red-tarjeta${atencion ? ' atencion' : ''}`, id: idDe(red), 'aria-labelledby': `${idDe(red)}-nombre` },
    el('div', { class: 'red-cabecera' },
      el('div', { class: 'red-identidad' },
        el('h2', { class: 'red-nombre', id: `${idDe(red)}-nombre` }, red.red.nombre),
        el('p', { class: 'red-detalle' }, host),
        el('p', { class: 'red-detalle' }, quien)),
      resumenDe(info)),
    // Sin acceso no hay nada que mostrar de la red: solo el aviso y cómo volver a parear.
    sinAcceso ? null : el('div', { class: 'red-piezas' }, piezasDe(info).map(pieza)),
    sinAcceso ? null : contadores(info),
    aviso(info, red),
    el('p', { class: 'red-actualizado' }, info ? `Actualizado a las ${horaConSegundos(info.en)}` : 'Consultando…'),
    acciones(red, info));
}

const idDe = (red) => `red-${redes.indexOf(red)}`;

function pintarRed(red) {
  const actual = $(idDe(red));
  if (actual) actual.replaceWith(tarjeta(red)); else pintar();
}

function pintar() {
  const lista = $('redes-lista');
  if (!redes.length) {
    vaciar(lista, el('div', { class: 'tarjeta red-vacia' },
      el('h2', { class: 'tarjeta-titulo' }, 'Todavía no hay redes en este panel'),
      el('p', { class: 'redes-nota' }, 'Agregá la primera con la dirección de su servidor y un código de pareo de esa red.')));
    abrirFormulario();
  } else {
    vaciar(lista, redes.map(tarjeta));
  }
  pintarActualizado();
}

function pintarActualizado() {
  const indicador = $('redes-actualizado');
  const infos = redes.map((r) => estados.get(r.url)).filter(Boolean);
  if (!redes.length) { indicador.dataset.estado = 'pausa'; $('redes-actualizado-texto').textContent = 'Sin redes'; return; }
  if (!infos.length) { $('redes-actualizado-texto').textContent = 'Conectando…'; return; }
  // Un servidor que contesta «sin acceso» responde: lo que falta es el acceso de este navegador.
  const responden = infos.filter((i) => i.falla !== 'no_responde').length;
  indicador.dataset.estado = responden === redes.length ? 'ok' : responden ? 'lento' : 'caido';
  $('redes-actualizado-texto').textContent = responden === redes.length
    ? `${redes.length === 1 ? 'La red responde' : `Las ${redes.length} redes responden`}`
    : `${responden} de ${redes.length} ${redes.length === 1 ? 'red responde' : 'redes responden'}`;
}

// ---- Abrir el tablero de una red ----

/** Abre el tablero de la red en una pestaña nueva y, cuando lo pide, le pasa el acceso de esa red con postMessage,
    solo a la dirección de su servidor: el token no va en la dirección ni lo recibe otra página. Si ese navegador
    ya tenía su propio acceso a la red, el tablero usa el suyo y no lo pide. */
function abrirTablero(red) {
  const origen = new URL(red.url).origin;
  const ventana = window.open(`${red.url}/panel/#panel`, '_blank');
  if (!ventana) { avisar('El navegador no dejó abrir la pestaña nueva. Permití las ventanas emergentes de este panel.', 'error'); return; }
  const alPedir = (evento) => {
    if (evento.source !== ventana || evento.origin !== origen || !evento.data || evento.data.tipo !== 'zonda-pide-acceso') return;
    window.removeEventListener('message', alPedir);
    ventana.postMessage({
      tipo: 'zonda-acceso',
      acceso: { token: red.token, vence: red.vence, perfil: red.perfil, persona: red.persona, red: red.red, dispositivoId: red.dispositivoId },
    }, origen);
  };
  window.addEventListener('message', alPedir);
  setTimeout(() => window.removeEventListener('message', alPedir), ESPERA_TABLERO);
}

// ---- Quitar una red ----

/** Como «Quitar este dispositivo» de la app: revoca el token en el servidor (DELETE /dispositivos/actual) y lo borra
    de este navegador. Si el servidor no responde, la quita igual y lo dice: el acceso sigue hasta que la empresa lo
    quite o venza. Si es el último administrador y queda más gente, el servidor no lo deja (409). */
async function quitar(red) {
  let revocado = true;
  try {
    await api(`${red.url}/dispositivos/actual`, { token: red.token, metodo: 'DELETE', espera: ESPERA });
  } catch (falla) {
    if (falla.estado === 409) {
      confirmando = null;
      pintarRed(red);
      avisar(`${falla.message}. Antes, otra persona tiene que quedar como administrador de ${red.red.nombre}.`, 'error');
      return;
    }
    revocado = falla.estado === 401;
  }
  confirmando = null;
  redes = redes.filter((r) => r !== red);
  estados.delete(red.url);
  guardarRedes(redes);
  pintar();
  avisar(revocado ? `${red.red.nombre} ya no está en el panel.`
    : `${red.red.nombre} ya no está en el panel. Su servidor no respondió: el acceso sigue hasta que la empresa lo quite o venza.`, revocado ? 'ok' : 'error');
}

// ---- Arranque ----

function iniciar() {
  aplicarTema(temaQueToca());
  oscuroDelSistema.addEventListener('change', () => { if (!temaGuardado()) aplicarTema(temaQueToca()); });
  $('boton-tema').addEventListener('click', cambiarTema);
  $('boton-agregar').addEventListener('click', () => abrirFormulario());
  $('boton-cancelar').addEventListener('click', cerrarFormulario);
  $('form-agregar').addEventListener('submit', parear);
  $('campo-direccion').addEventListener('change', alPegarDireccion);
  $('campo-codigo').addEventListener('input', (ev) => {
    const valor = normalizarCodigo(ev.target.value);
    if (valor !== ev.target.value) ev.target.value = valor;
  });
  // Otra pestaña del panel agregó o quitó una red.
  window.addEventListener('storage', (ev) => { if (ev.key === CLAVE_REDES) { redes = leerRedes(); pintar(); consultarTodas(); } });
  pintar();
  consultarTodas();
  setInterval(() => { if (!document.hidden) consultarTodas(); }, CADA);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) consultarTodas(); });
}

iniciar();
