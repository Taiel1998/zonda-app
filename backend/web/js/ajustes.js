// La vista Ajustes, como las páginas de administración de Grafana: un menú de secciones a la izquierda (General,
// Personas, Equipos, Severidades, Notificaciones y Registro de cambios) y, a la derecha, la sección elegida, con
// tablas que se editan en el lugar. Lo que se cambia queda marcado hasta «Guardar cambios», que pide el motivo
// cuando el cambio va al registro (la importancia y la severidad), como en la app. El analista ve todo sin los
// controles de administrador; igual el servidor controla cada cambio (403). Sumar, quitar o cambiar a alguien
// se confirma con la llave de esta computadora (decisión 51); sin ella, eso se hace desde la app.
// Los navegadores que abren el tablero van aparte de las personas y las tablas largas, de a páginas (decisión 58).

import { el, fechaHora, haceCuanto, hora, numero, vaciar } from './util.js';
import {
  $, IMPORTANCIAS, NIVEL, ORDEN_SEVERIDAD, PERFILES, TIPOS, ahoraServidor, e, esAdmin, mandar, nombrePersona, pedir,
} from './comun.js';
import { abrirHoja, avisoInferior, confirmar } from './hojas.js';
import { apagarAvisos, avisoDePrueba, estadoAvisos, permitirAvisos } from './avisos.js';
import { cabecerasConLlave, tieneLlave } from './llave.js';
import { enPagina, piePaginas, primeraPagina } from './paginas.js';

const ORDEN_TIPOS_SEVERIDAD = ['fuerza_bruta', 'escaneo', 'ddos', 'probable_falsa_alarma', 'sin_tipificar', 'servicio_no_autorizado', 'volumen_inusual'];
const MODOS_AVISO = { sonido: 'Con sonido', vibracion: 'Sin sonido', sin_aviso: 'Sin aviso' };
const SECCIONES = [
  { id: 'general', nombre: 'General' },
  { id: 'personas', nombre: 'Personas' },
  { id: 'navegadores', nombre: 'Navegadores' },
  { id: 'equipos', nombre: 'Equipos' },
  { id: 'severidades', nombre: 'Severidades' },
  { id: 'notificaciones', nombre: 'Notificaciones' },
  { id: 'registro', nombre: 'Registro de cambios' },
];
const CLAVE_SECCION = 'zonda.tablero.ajustes';

let alSalir = () => {};
let alElegirTema = () => {};
let temaElegido = () => 'sistema';
let esperaTelegram = null;
let seccion = leerSeccion();
// Lo que se cambió en las tablas y todavía no se guardó: perfiles, permisos de bloquear, importancias y severidades.
const pendientes = { personas: new Map(), bloqueos: new Map(), equipos: new Map(), severidades: new Map() };
// Lo pendiente de cada sección: la de Personas junta los perfiles y los permisos de bloquear.
const DE_LA_SECCION = { personas: ['personas', 'bloqueos'], equipos: ['equipos'], severidades: ['severidades'] };
const sinGuardar = (id) => (DE_LA_SECCION[id] || []).reduce((n, clave) => n + pendientes[clave].size, 0);
// «Bloqueos» (decisión 51): un administrador siempre bloquea; un analista, si un administrador le dio «Bloquea».
const BLOQUEOS = { bloquea: 'Bloquea', pide_aprobacion: 'Pide aprobación' };
const bloqueosDe = (p) => (p.puedeBloquear ? 'bloquea' : 'pide_aprobacion');
const busqueda = { equipos: '', registro: '' };
// Si llegan datos del servidor mientras alguien escribe o elige, la sección se vuelve a armar al soltar el campo.
let repintarAlSoltar = false;

function leerSeccion() {
  try { return localStorage.getItem(CLAVE_SECCION) || 'general'; } catch (_) { return 'general'; }
}

function guardarSeccion(id) {
  try { localStorage.setItem(CLAVE_SECCION, id); } catch (_) { /* sin almacenamiento, vale mientras la pestaña siga abierta */ }
}

const editando = () => {
  const activo = document.activeElement;
  return Boolean(activo && $('ajustes').contains(activo) && ['INPUT', 'SELECT', 'TEXTAREA'].includes(activo.tagName));
};

export function configurarAjustes({ salir, elegirTema, temaElegido: elegido }) {
  alSalir = salir;
  alElegirTema = elegirTema;
  temaElegido = elegido;
  // Recargar o cerrar la pestaña con cambios sin guardar: el navegador pregunta.
  window.addEventListener('beforeunload', (ev) => { if (cambiosSinGuardar()) { ev.preventDefault(); ev.returnValue = ''; } });
  $('ajustes').addEventListener('focusout', () => setTimeout(() => {
    if (repintarAlSoltar && !editando()) { repintarAlSoltar = false; pintarAjustes(); }
  }, 0));
}

/** Las personas de la red: además dicen quién es este navegador y con qué perfil. */
export async function actualizarPersonas() {
  e.personas = await pedir('/personas');
  const yo = e.personas.find((p) => p.vos);
  if (yo) {
    e.miId = yo.id;
    e.perfil = yo.perfil;
  }
}

export async function actualizarAjustes() {
  // Con ?bloqueos=true el registro trae también los bloqueos y los permisos (la app 1.0.8 no los conoce).
  const pedidos = await Promise.allSettled([actualizarPersonas(), pedir('/equipos'), pedir('/severidades'), pedir('/registro?bloqueos=true'), pedir('/telegram')]);
  if (pedidos[1].status === 'fulfilled') e.equipos = pedidos[1].value;
  if (pedidos[2].status === 'fulfilled') e.severidades = pedidos[2].value;
  if (pedidos[3].status === 'fulfilled') e.registro = pedidos[3].value;
  if (pedidos[4].status === 'fulfilled') e.telegram = pedidos[4].value;
  if (e.vista === 'ajustes') pintarAjustes({ desdeElServidor: true });
}

/** Cuántos cambios faltan guardar, en todas las secciones. */
export const cambiosSinGuardar = () => Object.values(pendientes).reduce((suma, m) => suma + m.size, 0);

/** El numerito de los cambios sin guardar. El lector dice «1 cambio sin guardar» en lugar de pegar el número al
    nombre («Ajustes1»). */
function marcaPendientes(n) {
  return el('span', { class: 'ajustes-pendientes', title: `${numero(n)} sin guardar` },
    el('span', { 'aria-hidden': 'true' }, numero(n)),
    el('span', { class: 'oculto-visual' }, `, ${numero(n)} ${n === 1 ? 'cambio' : 'cambios'} sin guardar`));
}

/** La pestaña «Ajustes», arriba y en la barra de abajo, también dice cuántos cambios faltan guardar. */
function marcarPestania() {
  const n = cambiosSinGuardar();
  for (const boton of document.querySelectorAll('.pestania[data-vista="ajustes"], #barra-inferior [data-vista="ajustes"]')) {
    const marca = boton.querySelector('.ajustes-pendientes');
    if (marca) marca.remove();
    if (n) boton.append(marcaPendientes(n));
  }
}

/** Lo pendiente que otra persona ya dejó igual (o que desapareció) deja de estarlo. */
function limpiarPendientes() {
  for (const [id, perfil] of pendientes.personas) {
    const p = e.personas.find((x) => x.id === id);
    if (!p || p.perfil === perfil) pendientes.personas.delete(id);
  }
  // Un perfil nuevo deja el permiso en «Pide aprobación» (y un administrador siempre bloquea): se elige después.
  for (const [id, bloqueos] of pendientes.bloqueos) {
    const p = e.personas.find((x) => x.id === id);
    if (!p || p.perfil !== 'analista' || pendientes.personas.has(id) || bloqueosDe(p) === bloqueos) pendientes.bloqueos.delete(id);
  }
  for (const [ip, importancia] of pendientes.equipos) {
    const q = e.equipos.find((x) => x.ip === ip);
    if (!q || q.importancia === importancia) pendientes.equipos.delete(ip);
  }
  for (const [tipo, severidad] of pendientes.severidades) {
    if (!e.severidades[tipo] || e.severidades[tipo] === severidad) pendientes.severidades.delete(tipo);
  }
}

// ---- El marco: el menú de secciones y la sección elegida ----

const CONTENIDO = {
  general: seccionGeneral,
  personas: seccionPersonas,
  navegadores: seccionNavegadores,
  equipos: seccionEquipos,
  severidades: seccionSeveridades,
  notificaciones: seccionNotificaciones,
  registro: seccionRegistro,
};

export function pintarAjustes({ desdeElServidor = false } = {}) {
  if (desdeElServidor && editando()) { repintarAlSoltar = true; return; }
  limpiarPendientes();
  marcarPestania();
  if (!CONTENIDO[seccion]) seccion = 'general';
  // El campo o la lista que tenía el foco lo recupera después de armar todo de nuevo.
  const foco = document.activeElement && $('ajustes').contains(document.activeElement) ? document.activeElement.dataset.foco : null;
  const menu = el('nav', { class: 'ajustes-menu', role: 'tablist', 'aria-label': 'Secciones de Ajustes', 'aria-orientation': 'vertical', onkeydown: moverEnMenu },
    SECCIONES.map((s) => {
      const n = sinGuardar(s.id);
      return el('button', {
        type: 'button', role: 'tab', id: `ajustes-${s.id}`, 'aria-selected': String(s.id === seccion), 'aria-controls': 'ajustes-panel',
        tabindex: s.id === seccion ? '0' : '-1', dataset: { seccion: s.id }, onclick: () => elegirSeccion(s.id),
      }, el('span', {}, s.nombre), n ? marcaPendientes(n) : null);
    }));
  vaciar($('ajustes'), menu,
    el('section', { class: 'tarjeta ajustes-panel', id: 'ajustes-panel', role: 'tabpanel', 'aria-labelledby': `ajustes-${seccion}` }, CONTENIDO[seccion]()));
  if (foco) {
    const nuevo = $('ajustes').querySelector(`[data-foco="${foco}"]`);
    if (nuevo) nuevo.focus();
  }
}

function elegirSeccion(id, enfocar = false) {
  seccion = id;
  guardarSeccion(id);
  pintarAjustes();
  if (enfocar) $(`ajustes-${id}`).focus();
}

/** Con las flechas se pasa de una sección a otra, como en cualquier lista de pestañas. */
function moverEnMenu(ev) {
  const adelante = ['ArrowDown', 'ArrowRight'].includes(ev.key);
  if (!adelante && !['ArrowUp', 'ArrowLeft'].includes(ev.key)) return;
  ev.preventDefault();
  const i = SECCIONES.findIndex((s) => s.id === seccion);
  elegirSeccion(SECCIONES[(i + (adelante ? 1 : SECCIONES.length - 1)) % SECCIONES.length].id, true);
}

function cabecera(titulo, sub, accion = null) {
  return el('div', { class: 'seccion-cabecera' },
    el('div', {}, el('h2', { class: 'seccion-titulo' }, titulo), sub ? el('p', { class: 'seccion-sub' }, sub) : null),
    accion ? el('div', { class: 'seccion-acciones' }, accion) : null);
}

function tabla(columnas, filas, id = null) {
  return el('div', { class: 'tabla-envoltura' },
    el('table', { class: 'tabla tabla-ajustes', id },
      el('thead', {}, el('tr', {}, columnas.map((c) => el('th', { scope: 'col' }, c)))),
      el('tbody', {}, filas)));
}

function selector({ etiqueta, opciones, valor, foco, alCambiar }) {
  return el('select', { class: 'select-tabla', 'aria-label': etiqueta, dataset: { foco }, onchange: (ev) => alCambiar(ev.target.value) },
    Object.entries(opciones).map(([id, texto]) => el('option', { value: id, selected: id === valor }, texto)));
}

/** Marca un cambio en una tabla; volver al valor de antes lo desmarca. */
function marcar(id, clave, valor, original) {
  if (valor === original) pendientes[id].delete(clave);
  else pendientes[id].set(clave, valor);
  pintarAjustes();
}

const GUARDAR = { personas: guardarPersonas, equipos: guardarImportancias, severidades: guardarSeveridades };

function barraCambios(id) {
  const n = sinGuardar(id);
  if (!n) return null;
  return el('div', { class: 'barra-cambios' },
    el('span', {}, `${numero(n)} ${n === 1 ? 'cambio sin guardar' : 'cambios sin guardar'}`),
    el('button', { type: 'button', class: 'boton-secundario boton-chico', onclick: () => { for (const clave of DE_LA_SECCION[id]) pendientes[clave].clear(); pintarAjustes(); } }, 'Descartar'),
    el('button', { type: 'button', class: 'boton-principal boton-chico', onclick: GUARDAR[id] }, 'Guardar cambios'));
}

const notaSoloAdmin = () => (esAdmin() ? null : el('p', { class: 'solo-admin-nota' }, 'Lo cambia un administrador.'));

const sinTildes = (texto) => String(texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

/** El motivo del cambio, obligatorio: queda en el registro con el nombre y la hora (decisión 40). */
function hojaMotivo({ titulo, lineas, ejemplo, alGuardar }) {
  const uno = lineas.length === 1;
  const motivo = el('textarea', { maxlength: '500', placeholder: ejemplo });
  const error = el('div', { class: 'error-hoja', hidden: true });
  abrirHoja({
    titulo,
    cuerpo: [
      el('ul', { class: 'lista-cambios' }, lineas.map((linea) => el('li', {}, linea))),
      el('label', { class: 'campo' }, el('span', {}, uno ? '¿Por qué lo cambiás?' : '¿Por qué los cambiás?', el('span', { class: 'asterisco' }, ' *')), motivo,
        el('small', {}, 'Queda en el registro de cambios con tu nombre y la hora.')),
      error,
    ],
    acciones: [
      { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
      {
        texto: 'Guardar', tipo: 'principal', alHacer: async (h) => {
          if (!motivo.value.trim()) { error.hidden = false; error.textContent = uno ? 'Escribí por qué lo cambiás.' : 'Escribí por qué los cambiás.'; return; }
          try {
            await alGuardar(motivo.value.trim());
            h.cerrar();
          } catch (falla) {
            error.hidden = false;
            error.textContent = falla.message;
          }
          actualizarAjustes().catch(() => {});
        },
      },
    ],
  });
}

// ---- General ----

function seccionGeneral() {
  const red = e.acceso.red ? e.acceso.red.nombre : 'Red';
  const yo = e.personas.find((p) => p.vos);
  const vence = e.acceso.vence ? ` · el acceso vence el ${fechaHora(Date.parse(e.acceso.vence), 0)}` : '';
  return [
    cabecera('General', 'Esta red, este navegador y cómo se ve el tablero.'),
    el('dl', { class: 'ajustes-datos' },
      dato('Red', `${red} · ${location.host}`),
      dato('Este navegador', `${yo ? nombrePersona(yo) : 'Tablero web'} · ${PERFILES[e.perfil] || ''}${vence}`,
        el('button', { type: 'button', class: 'boton-secundario boton-chico', onclick: () => alSalir() }, 'Salir')),
      dato('Servidor de alertas', `Versión ${e.salud ? e.salud.version : '–'} · ${location.protocol === 'https:' ? 'HTTPS con el certificado de la red' : 'sin cifrar (HTTP)'}`),
      dato('Tema', null, selector({
        etiqueta: 'Tema', opciones: { sistema: 'Como el sistema', claro: 'Claro', oscuro: 'Oscuro' }, valor: temaElegido(), foco: 'tema',
        alCambiar: (valor) => { alElegirTema(valor); pintarAjustes(); },
      }))),
  ];
}

function dato(nombre, valor, control = null) {
  return el('div', { class: 'ajustes-dato' }, el('dt', {}, nombre), el('dd', {}, el('span', {}, valor || ''), control));
}

// ---- Personas ----

function seccionPersonas() {
  const admin = esAdmin();
  // Sumar, quitar o cambiar a alguien desde acá se confirma con la llave de esta computadora.
  const cambia = admin && tieneLlave();
  const ahora = ahoraServidor();
  const filas = e.personas.filter((p) => p.tipo !== 'navegador').map((p) => {
    const editable = cambia && !p.vos;
    const perfil = editable
      ? selector({
        etiqueta: `Perfil de ${nombrePersona(p)}`, opciones: PERFILES, valor: pendientes.personas.get(p.id) || p.perfil, foco: `perfil-${p.id}`,
        alCambiar: (valor) => marcar('personas', p.id, valor, p.perfil),
      })
      : el('span', { class: `etiqueta${p.perfil === 'administrador' ? ' admin' : ''}` }, PERFILES[p.perfil]);
    return el('tr', { class: pendientes.personas.has(p.id) || pendientes.bloqueos.has(p.id) ? 'cambiada' : null },
      el('td', {}, el('strong', {}, nombrePersona(p)), p.vos ? [' ', el('span', { class: 'etiqueta vos' }, 'vos')] : null),
      el('td', { class: 'secundario envolver', 'data-etiqueta': 'Dispositivo' }, p.telefono),
      el('td', { 'data-etiqueta': 'Perfil' }, perfil),
      el('td', { 'data-etiqueta': 'Bloqueos' }, celdaBloqueos(p, editable)),
      el('td', { class: 'secundario', 'data-etiqueta': 'Último uso' }, p.ultimoUso ? haceCuanto(Date.parse(p.ultimoUso), ahora) : '–'),
      el('td', { class: 'accion' }, editable ? el('button', { type: 'button', class: 'boton-texto peligro', onclick: () => quitarPersona(p) }, 'Quitar') : null));
  });
  return [
    cabecera('Personas', 'Quiénes usan Zonda en esta red. Los administradores cambian la configuración y agregan personas.',
      cambia ? el('button', { type: 'button', class: 'boton-principal boton-chico', onclick: hojaAgregarPersona }, 'Agregar persona') : null),
    tabla(['Nombre', 'Dispositivo', 'Perfil', 'Bloqueos', 'Último uso', ''], filas),
    el('p', { class: 'nota ajustes-leyenda' }, 'Bloquea: corta el tráfico de un equipo en el acto. Pide aprobación: lo aprueba alguien que puede bloquear.'),
    admin && !cambia ? el('p', { class: 'solo-admin-nota' }, 'Sin la llave de esta computadora, las personas se agregan y se cambian desde la app.') : null,
    cambia ? barraCambios('personas') : (admin ? null : notaSoloAdmin()),
  ];
}

/** El permiso de bloquear en la tabla. Con un perfil cambiado sin guardar se ve cómo queda: un administrador
    bloquea y un analista nuevo empieza en «Pide aprobación». */
function celdaBloqueos(p, editable) {
  const perfil = pendientes.personas.get(p.id) || p.perfil;
  if (perfil === 'administrador') return el('span', {}, BLOQUEOS.bloquea);
  if (perfil !== p.perfil) return el('span', { class: 'secundario' }, BLOQUEOS.pide_aprobacion);
  if (!editable) return el('span', { class: p.puedeBloquear ? null : 'secundario' }, BLOQUEOS[bloqueosDe(p)]);
  return selector({
    etiqueta: `Bloqueos de ${nombrePersona(p)}`, opciones: BLOQUEOS, valor: pendientes.bloqueos.get(p.id) || bloqueosDe(p), foco: `bloqueos-${p.id}`,
    alCambiar: (valor) => marcar('bloqueos', p.id, valor, bloqueosDe(p)),
  });
}

/** Guarda los perfiles y después los permisos de bloquear, cada cambio con la llave (Windows pide la huella, la
    cara o el PIN). Si uno falla, los que siguen quedan sin guardar. */
async function guardarPersonas() {
  const de = (mapa) => [...mapa.entries()].map(([id, valor]) => ({ p: e.personas.find((x) => x.id === id), valor })).filter((c) => c.p);
  const perfiles = de(pendientes.personas);
  const permisos = de(pendientes.bloqueos);
  const lineas = [
    ...perfiles.map((c) => `${nombrePersona(c.p)} pasa a ${PERFILES[c.valor].toLowerCase()}`),
    ...permisos.map((c) => `${nombrePersona(c.p)} ${c.valor === 'bloquea' ? 'bloquea en el acto' : 'pide aprobación para bloquear'}`),
  ];
  let titulo = '¿Guardar los cambios?';
  if (!permisos.length) titulo = perfiles.length === 1 ? '¿Cambiar el perfil?' : '¿Cambiar los perfiles?';
  else if (!perfiles.length && permisos.length === 1) titulo = '¿Cambiar el permiso de bloquear?';
  if (!await confirmar({ titulo, texto: `${lineas.join('. ')}.`, aceptar: 'Guardar' })) return;
  try {
    for (const { p, valor } of perfiles) {
      const cabeceras = await cabecerasConLlave({ tipo: 'perfil', personaId: p.id, perfil: valor });
      await pedir(`/personas/${encodeURIComponent(p.id)}/perfil`, { metodo: 'PUT', cuerpo: { perfil: valor }, cabeceras });
      pendientes.personas.delete(p.id);
    }
    for (const { p, valor } of permisos) {
      const puedeBloquear = valor === 'bloquea';
      const cabeceras = await cabecerasConLlave({ tipo: 'permiso_bloqueo', personaId: p.id, puedeBloquear });
      await pedir(`/personas/${encodeURIComponent(p.id)}/bloqueos`, { metodo: 'PUT', cuerpo: { puedeBloquear }, cabeceras });
      pendientes.bloqueos.delete(p.id);
    }
    if (permisos.length === 1 && !perfiles.length) {
      const { p, valor } = permisos[0];
      avisoInferior(valor === 'bloquea' ? `${nombrePersona(p)} ya puede bloquear.` : `${nombrePersona(p)} ahora pide aprobación para bloquear.`);
    } else if (!permisos.length) {
      avisoInferior(perfiles.length === 1 ? 'Perfil guardado.' : 'Perfiles guardados.');
    } else {
      avisoInferior('Cambios guardados.');
    }
  } catch (falla) {
    avisoInferior(falla.cancelada ? 'No se confirmó con la llave: quedan cambios sin guardar.' : falla.message, { duracion: 7000 });
  }
  actualizarAjustes().catch(() => {});
}

function hojaAgregarPersona() {
  const nombre = el('input', { type: 'text', maxlength: '40', placeholder: 'Por ejemplo: Laura', 'aria-required': 'true' });
  let perfil = 'analista';
  const perfiles = Object.entries(PERFILES).map(([id, texto]) => el('button', {
    type: 'button', class: 'chip-filtro', 'aria-pressed': String(id === perfil), dataset: { id },
    onclick: () => { perfil = id; for (const b of perfiles) b.setAttribute('aria-pressed', String(b.dataset.id === perfil)); },
  }, texto));
  const error = el('div', { class: 'error-hoja', hidden: true });
  abrirHoja({
    titulo: 'Agregar persona',
    subtitulo: 'Genera un código de un solo uso y su QR para que la persona conecte su teléfono.',
    cuerpo: [
      el('label', { class: 'campo' }, el('span', {}, 'Nombre', el('span', { class: 'asterisco' }, ' *')), nombre),
      el('div', { class: 'grupo-opciones' }, el('p', {}, 'Perfil'), el('div', { class: 'opciones' }, perfiles)),
      el('p', { class: 'leyenda-obligatorio', style: 'margin:0' }, el('span', { class: 'asterisco' }, '*'), ' Obligatorio'),
      error,
    ],
    acciones: [
      { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
      {
        texto: 'Generar código', tipo: 'principal', alHacer: async (h) => {
          if (!nombre.value.trim()) { error.hidden = false; error.textContent = 'Escribí el nombre de la persona.'; return; }
          try {
            const cuerpo = { nombre: nombre.value.trim(), perfil };
            const cabeceras = await cabecerasConLlave({ tipo: 'sumar', ...cuerpo });
            mostrarCodigo(h, await pedir('/personas/codigo', { metodo: 'POST', cuerpo, cabeceras }));
          } catch (falla) {
            error.hidden = false;
            error.textContent = falla.cancelada ? 'No se confirmó con la llave: no se generó el código.' : falla.message;
          }
        },
      },
    ],
  });
}

function mostrarCodigo(hoja, codigo) {
  hoja.pintar({
    titulo: `Código para ${codigo.nombre}`,
    subtitulo: `${PERFILES[codigo.perfil]} · válido hasta las ${hora(Date.parse(codigo.vence))} · un solo uso`,
    cuerpo: [el('div', { class: 'qr-persona' },
      codigo.qrSvg ? el('img', { src: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(codigo.qrSvg)}`, alt: `Código QR de ${codigo.nombre}` }) : null,
      el('span', { class: 'codigo' }, codigo.codigo),
      el('span', { class: 'nota' }, 'En la app de Zonda: agregar una red y escanear el QR, o escribir la dirección y el código.'))],
    acciones: [{ texto: 'Listo', tipo: 'principal', alHacer: (h) => { h.cerrar(); actualizarAjustes(); } }],
  });
}

async function quitarPersona(p) {
  if (!await confirmar({ titulo: `¿Quitar a ${nombrePersona(p)} del equipo?`, texto: 'Su dispositivo deja de recibir alertas y no puede volver a entrar sin un código nuevo.', aceptar: 'Quitar', peligro: true })) return;
  try {
    const cabeceras = await cabecerasConLlave({ tipo: 'quitar', personaId: p.id });
    await pedir(`/personas/${encodeURIComponent(p.id)}`, { metodo: 'DELETE', cabeceras });
    pendientes.personas.delete(p.id);
    pendientes.bloqueos.delete(p.id);
    avisoInferior(`Se quitó a ${nombrePersona(p)} del equipo.`);
  } catch (falla) { avisoInferior(falla.cancelada ? 'No se confirmó con la llave: no se quitó a nadie.' : falla.message, { duracion: 7000 }); }
  actualizarAjustes();
}

// ---- Navegadores: las computadoras que abren el tablero (decisión 58) ----

/** «Chrome en Windows»: el nombre sin «Tablero web ·», que llevan todos. Los primeros navegadores se llamaban
    solo «Tablero web». */
function nombreNavegador(p) {
  const nombre = (p.telefono || '').replace(/^Tablero web\s*·\s*/, '').trim();
  return nombre && nombre !== 'Tablero web' ? nombre : 'Navegador';
}

/** Un navegador no es una persona: entra con un código, como un teléfono, pero no recibe avisos ni se le
    asignan alertas. Acá se ve cuáles tienen acceso y se les quita, con la llave de esta computadora. */
function seccionNavegadores() {
  const admin = esAdmin();
  const cambia = admin && tieneLlave();
  const ahora = ahoraServidor();
  const navegadores = e.personas.filter((p) => p.tipo === 'navegador');
  const filas = navegadores.length
    ? navegadores.map((p) => el('tr', {},
      el('td', {}, el('strong', {}, nombreNavegador(p)), p.vos ? [' ', el('span', { class: 'etiqueta vos' }, 'vos')] : null),
      el('td', { 'data-etiqueta': 'Perfil' }, el('span', { class: `etiqueta${p.perfil === 'administrador' ? ' admin' : ''}` }, PERFILES[p.perfil])),
      el('td', { class: 'secundario', 'data-etiqueta': 'Último uso' }, p.ultimoUso ? haceCuanto(Date.parse(p.ultimoUso), ahora) : '–'),
      el('td', { class: 'accion' }, cambia && !p.vos ? el('button', { type: 'button', class: 'boton-texto peligro', onclick: () => quitarNavegador(p) }, 'Quitar acceso') : null)))
    : el('tr', {}, el('td', { colspan: '4', class: 'secundario' }, 'Todavía ningún navegador abrió el tablero.'));
  return [
    cabecera('Navegadores', 'Las computadoras que abren este tablero. Cada una entra con su propio código, como un teléfono.'),
    tabla(['Navegador', 'Perfil', 'Último uso', ''], filas),
    admin && !cambia ? el('p', { class: 'solo-admin-nota' }, 'Sin la llave de esta computadora, el acceso de un navegador se quita desde la app.') : null,
    admin ? null : notaSoloAdmin(),
  ];
}

async function quitarNavegador(p) {
  const nombre = nombreNavegador(p);
  if (!await confirmar({ titulo: `¿Quitarle el acceso a ${nombre}?`, texto: 'Ese navegador deja de ver el tablero y no puede volver a entrar sin un código nuevo.', aceptar: 'Quitar acceso', peligro: true })) return;
  try {
    const cabeceras = await cabecerasConLlave({ tipo: 'quitar', personaId: p.id });
    await pedir(`/personas/${encodeURIComponent(p.id)}`, { metodo: 'DELETE', cabeceras });
    avisoInferior(`${nombre} ya no tiene acceso al tablero.`);
  } catch (falla) { avisoInferior(falla.cancelada ? 'No se confirmó con la llave: el navegador sigue con acceso.' : falla.message, { duracion: 7000 }); }
  actualizarAjustes();
}

// ---- Equipos ----

const NOMBRES_IMPORTANCIA = Object.fromEntries(Object.entries(IMPORTANCIAS).map(([id, imp]) => [id, imp.nombre]));

function seccionEquipos() {
  return [
    cabecera('Equipos', 'La importancia de un equipo sube o baja un nivel la severidad de sus alertas.'),
    el('input', {
      type: 'search', class: 'buscar-tabla', placeholder: 'Buscar equipo o IP', value: busqueda.equipos, 'aria-label': 'Buscar equipo o IP', dataset: { foco: 'buscar-equipos' },
      oninput: (ev) => { busqueda.equipos = ev.target.value; primeraPagina('equipos'); pintarFilasEquipos(); },
    }),
    tabla(['Equipo', 'IP', 'Importancia'], filasEquipos(equiposFiltrados()), 'tabla-equipos'),
    el('div', { id: 'equipos-paginas' }, piePaginas(equiposFiltrados(), 'equipos', pintarFilasEquipos)),
    el('p', { class: 'nota ajustes-leyenda' }, `${IMPORTANCIAS.critico.nombre}: ${IMPORTANCIAS.critico.explicacion.toLowerCase()} ${IMPORTANCIAS.poco_importante.nombre}: ${IMPORTANCIAS.poco_importante.explicacion.toLowerCase()}`),
    esAdmin() ? barraCambios('equipos') : notaSoloAdmin(),
  ];
}

function equiposFiltrados() {
  const buscado = sinTildes(busqueda.equipos.trim());
  return e.equipos.filter((q) => !buscado || sinTildes(q.nombre).includes(buscado) || q.ip.includes(buscado));
}

/** La búsqueda y las páginas rehacen solo las filas y el pie: el campo no pierde lo escrito ni el foco. */
function pintarFilasEquipos() {
  const lista = equiposFiltrados();
  vaciar($('tabla-equipos').tBodies[0], filasEquipos(lista));
  vaciar($('equipos-paginas'), piePaginas(lista, 'equipos', pintarFilasEquipos));
}

function filasEquipos(lista) {
  if (!lista.length) return el('tr', {}, el('td', { colspan: '3', class: 'secundario' }, `Ningún equipo con «${busqueda.equipos.trim()}».`));
  return enPagina(lista, 'equipos').map((q) => el('tr', { class: pendientes.equipos.has(q.ip) ? 'cambiada' : null },
    el('td', {}, el('strong', {}, q.nombre)),
    el('td', { class: 'secundario', 'data-etiqueta': 'IP' }, q.ip),
    el('td', { 'data-etiqueta': 'Importancia' }, esAdmin()
      ? selector({
        etiqueta: `Importancia de ${q.nombre}`, opciones: NOMBRES_IMPORTANCIA, valor: pendientes.equipos.get(q.ip) || q.importancia, foco: `equipo-${q.ip}`,
        alCambiar: (valor) => marcar('equipos', q.ip, valor, q.importancia),
      })
      : el('span', { class: `etiqueta${q.importancia === 'critico' ? ' critico' : ''}` }, IMPORTANCIAS[q.importancia].nombre))));
}

function guardarImportancias() {
  const cambios = [...pendientes.equipos.entries()].map(([ip, importancia]) => ({ q: e.equipos.find((x) => x.ip === ip), importancia })).filter((c) => c.q);
  hojaMotivo({
    titulo: cambios.length === 1 ? 'Guardar 1 cambio' : `Guardar ${numero(cambios.length)} cambios`,
    lineas: cambios.map((c) => `${c.q.nombre}: ${IMPORTANCIAS[c.q.importancia].nombre.toLowerCase()} → ${IMPORTANCIAS[c.importancia].nombre.toLowerCase()}`),
    ejemplo: 'Por ejemplo: es el servidor de sueldos.',
    alGuardar: async (motivo) => {
      for (const { q, importancia } of cambios) {
        await mandar(`/equipos/${encodeURIComponent(q.ip)}`, 'PUT', { importancia, motivo });
        pendientes.equipos.delete(q.ip);
      }
      avisoInferior(cambios.length === 1 ? `${cambios[0].q.nombre} cambió de importancia. Sus alertas cambian de severidad.` : 'Importancias guardadas. Las alertas de esos equipos cambian de severidad.');
    },
  });
}

// ---- Severidad por tipo de ataque ----

function seccionSeveridades() {
  const admin = esAdmin();
  const filas = ORDEN_TIPOS_SEVERIDAD.filter((t) => e.severidades[t]).map((t) => {
    const valor = pendientes.severidades.get(t) || e.severidades[t];
    return el('tr', { class: pendientes.severidades.has(t) ? 'cambiada' : null },
      el('td', {}, el('strong', {}, TIPOS[t])),
      el('td', { 'data-etiqueta': 'Severidad' }, admin
        ? el('span', { class: 'con-punto' }, el('i', { class: 'punto', style: `background:var(--${valor})` }), selector({
          etiqueta: `Severidad de ${TIPOS[t]}`, opciones: Object.fromEntries(ORDEN_SEVERIDAD.map((s) => [s, NIVEL[s]])), valor, foco: `severidad-${t}`,
          alCambiar: (nuevo) => marcar('severidades', t, nuevo, e.severidades[t]),
        }))
        : el('span', { class: `sev ${valor}` }, NIVEL[valor])));
  });
  return [
    cabecera('Severidades', 'Con qué severidad llega cada tipo de ataque. Cambia los avisos y el orden de las alertas.'),
    tabla(['Tipo de ataque', 'Severidad'], filas),
    admin ? barraCambios('severidades') : notaSoloAdmin(),
  ];
}

function guardarSeveridades() {
  const cambios = Object.fromEntries(pendientes.severidades);
  hojaMotivo({
    titulo: pendientes.severidades.size === 1 ? 'Guardar 1 cambio' : `Guardar ${numero(pendientes.severidades.size)} cambios`,
    lineas: Object.entries(cambios).map(([t, s]) => `${TIPOS[t]}: ${NIVEL[e.severidades[t]].toLowerCase()} → ${NIVEL[s].toLowerCase()}`),
    ejemplo: 'Por ejemplo: el backup nocturno usa SSH y daba falsas alarmas.',
    alGuardar: async (motivo) => {
      e.severidades = await mandar('/severidades', 'PUT', { severidades: cambios, motivo });
      pendientes.severidades.clear();
      avisoInferior('Severidades guardadas. Las alertas se reordenan con la severidad nueva.');
    },
  });
}

// ---- Notificaciones: en este navegador (avisos.js) y por Telegram ----

function seccionNotificaciones() {
  return [
    cabecera('Notificaciones', 'Cómo te avisa Zonda de las alertas: en este navegador y por Telegram.'),
    el('div', { class: 'ajustes-bloque' }, notificacionesNavegador()),
    el('div', { class: 'ajustes-bloque' }, notificacionesTelegram()),
  ];
}

function bloque(titulo, sub, ...contenido) {
  return [el('h3', { class: 'bloque-titulo' }, titulo), sub ? el('p', { class: 'seccion-sub' }, sub) : null, ...contenido];
}

function notificacionesNavegador() {
  const titulo = 'En este navegador';
  const estado = estadoAvisos();
  if (estado === 'imposibles') return bloque(titulo, 'Este navegador no puede mostrar notificaciones.');
  if (estado === 'bloqueados') return bloque(titulo, 'Sin permiso, el tablero no puede mostrar notificaciones. Sigue funcionando; podés permitirlas desde el candado de la barra de direcciones.');
  if (estado === 'apagados') {
    return bloque(titulo, 'El navegador muestra una notificación cuando llega una alerta alta y el tablero no está a la vista. Te va a pedir confirmar el permiso.',
      el('div', { class: 'acciones-detalle' }, el('button', {
        type: 'button', class: 'boton-principal',
        onclick: async () => {
          if (await permitirAvisos() === 'activados') avisoInferior('Las notificaciones están permitidas.');
          pintarAjustes();
        },
      }, 'Permitir notificaciones')));
  }
  return bloque(titulo, 'Las notificaciones están permitidas: llegan las alertas altas cuando el tablero no está a la vista.',
    el('div', { class: 'acciones-detalle' },
      el('button', { type: 'button', class: 'boton-secundario', onclick: () => { if (!avisoDePrueba()) avisoInferior('Este navegador no mostró la notificación.'); } }, 'Mandar una de prueba'),
      el('button', { type: 'button', class: 'boton-texto', onclick: () => { apagarAvisos(); pintarAjustes(); } }, 'Desactivar')));
}

function notificacionesTelegram() {
  const t = e.telegram;
  if (!t) return bloque('Telegram', 'Cargando…');
  if (!t.vinculado) {
    return bloque('Telegram', 'Recibí los avisos de alertas en Telegram, en el chat con el bot de Zonda. Solo viajan la severidad, el servicio, la hora y la red.',
      el('div', { class: 'acciones-detalle' }, el('button', { type: 'button', class: 'boton-principal', onclick: conectarTelegram }, 'Conectar Telegram')));
  }
  const filas = ORDEN_SEVERIDAD.map((s) => el('tr', {},
    el('td', {}, el('span', { class: `sev ${s}` }, `Alertas ${NIVEL[s].toLowerCase()}s`)),
    el('td', { 'data-etiqueta': 'Aviso' }, selector({
      etiqueta: `Aviso de las alertas ${NIVEL[s].toLowerCase()}s`, opciones: MODOS_AVISO, valor: t.avisos[s], foco: `telegram-${s}`,
      alCambiar: async (modo) => {
        try { e.telegram.avisos = await mandar('/telegram/avisos', 'PUT', { ...t.avisos, [s]: modo }); } catch (falla) { avisoInferior(falla.message); }
        pintarAjustes();
      },
    }))));
  return bloque('Telegram', `Vinculado con @${t.bot}${t.vinculadoEn ? ` desde el ${fechaHora(Date.parse(t.vinculadoEn), 0)}` : ''}.`,
    tabla(['Severidad', 'Aviso'], filas),
    el('div', { class: 'acciones-detalle' },
      el('button', { type: 'button', class: 'boton-secundario', onclick: async () => { try { await mandar('/telegram/prueba', 'POST'); avisoInferior('Aviso de prueba enviado.'); } catch (falla) { avisoInferior(falla.message); } } }, 'Mandar un aviso de prueba'),
      el('button', { type: 'button', class: 'boton-texto', onclick: desvincularTelegram }, 'Desvincular')));
}

async function conectarTelegram() {
  let vinculo;
  try {
    vinculo = await mandar('/telegram/vinculo', 'POST', {});
  } catch (falla) {
    avisoInferior(falla.estado === 503 ? 'Este servidor no tiene el bot de Telegram configurado.' : falla.message, { duracion: 6000 });
    return;
  }
  const hoja = abrirHoja({
    titulo: 'Conectar Telegram',
    subtitulo: `El enlace vence a las ${hora(Date.parse(vinculo.vence))}.`,
    cuerpo: [
      el('ol', { style: 'margin:0;padding-left:20px;display:grid;gap:6px' },
        el('li', {}, 'Apretá «Abrir Telegram»: se abre el chat con el bot de Zonda.'),
        el('li', {}, 'Apretá «Iniciar». El bot reconoce este navegador.'),
        el('li', {}, 'Volvé acá: se actualiza solo.')),
    ],
    acciones: [
      { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
      { texto: 'Abrir Telegram', tipo: 'principal', alHacer: () => window.open(vinculo.enlaceBot, '_blank', 'noopener') },
    ],
    alCerrar: () => { clearInterval(esperaTelegram); esperaTelegram = null; },
  });
  clearInterval(esperaTelegram);
  esperaTelegram = setInterval(async () => {
    try {
      e.telegram = await pedir('/telegram');
      if (e.telegram.vinculado) { hoja.cerrar('vinculado'); avisoInferior('Telegram quedó vinculado.'); pintarAjustes(); }
    } catch (_) { /* se vuelve a preguntar */ }
  }, 3000);
}

async function desvincularTelegram() {
  if (!await confirmar({ titulo: '¿Desvincular Telegram?', texto: 'Este navegador deja de mandar avisos a ese chat.', aceptar: 'Desvincular', peligro: true })) return;
  try { await mandar('/telegram/vinculo', 'DELETE'); e.telegram = await pedir('/telegram'); pintarAjustes(); } catch (falla) { avisoInferior(falla.message); }
}

// ---- Registro de cambios ----

// Un navegador del tablero no es una persona (decisión 58): «Sumó el navegador Chrome en Windows», no «Sumó a
// Tablero web · Chrome en Windows al equipo». Los primeros navegadores se llamaban solo «Tablero web».
const esNavegador = (nombre) => /^Tablero web/.test(nombre || '');
function elNavegador(nombre) {
  const resto = (nombre || '').replace(/^Tablero web\s*·?\s*/, '').trim();
  return resto ? `el navegador ${resto}` : 'un navegador';
}

function fraseDe(c) {
  const ant = c.antes; const des = c.despues;
  if (esNavegador(c.nombre) && ['persona_nueva', 'perfil', 'persona_quitada'].includes(c.tipo)) return fraseDeNavegador(c);
  if (c.tipo === 'severidad') return `${TIPOS[c.objeto] || c.objeto} pasó de ${(NIVEL[ant] || ant || '').toLowerCase()} a ${(NIVEL[des] || des || '').toLowerCase()}`;
  if (c.tipo === 'importancia') return `${c.nombre || c.objeto} pasó de ${(IMPORTANCIAS[ant] || { nombre: ant }).nombre.toLowerCase()} a ${(IMPORTANCIAS[des] || { nombre: des }).nombre.toLowerCase()}`;
  if (c.tipo === 'persona_nueva') return `Sumó a ${c.nombre || 'una persona'} al equipo como ${(PERFILES[des] || des || '').toLowerCase()}`;
  if (c.tipo === 'perfil') return `${c.nombre || 'Una persona'} pasó a ${(PERFILES[des] || des || '').toLowerCase()}`;
  if (c.tipo === 'persona_quitada') return c.autorId && c.autorId === c.objeto ? 'Quitó su dispositivo del equipo' : `Quitó a ${c.nombre || 'una persona'} del equipo`;
  // Decisión 51, como en la app: «Bloqueó pc-rrhh por 1 h», «Cambió el bloqueo de pc-rrhh a 24 h», «Desbloqueó
  // pc-rrhh». Un cambio de duración trae la de antes; «hasta desbloquear» viaja como texto, no como segundos.
  if (c.tipo === 'bloqueo') {
    const equipo = c.nombre || c.objeto;
    const hasta = des === 'hasta_desbloquear';
    if (ant == null) return hasta ? `Bloqueó ${equipo} hasta desbloquear` : `Bloqueó ${equipo} por ${duracionBloqueo(des)}`;
    return hasta ? `Dejó ${equipo} bloqueado hasta desbloquear` : `Cambió el bloqueo de ${equipo} a ${duracionBloqueo(des)}`;
  }
  if (c.tipo === 'desbloqueo') return `Desbloqueó ${c.nombre || c.objeto}`;
  if (c.tipo === 'permiso_bloqueo') return `${des === 'bloquea' ? 'Le dio' : 'Le sacó el'} permiso de bloquear a ${c.nombre || 'una persona'}`;
  return c.tipo;
}

function fraseDeNavegador(c) {
  const perfil = (PERFILES[c.despues] || c.despues || '').toLowerCase();
  const nav = elNavegador(c.nombre);
  if (c.tipo === 'persona_nueva') return `Sumó ${nav} como ${perfil}`;
  if (c.tipo === 'perfil') return `${nav[0].toUpperCase()}${nav.slice(1)} pasó a ${perfil}`;
  return c.autorId && c.autorId === c.objeto ? 'Salió del tablero' : `Le quitó el acceso a ${nav}`;
}

function duracionBloqueo(texto) {
  const segundos = Number(texto);
  return segundos % 3600 === 0 ? `${segundos / 3600} h` : `${Math.ceil(segundos / 60)} min`;
}

function seccionRegistro() {
  const lista = registroFiltrado();
  return [
    cabecera('Registro de cambios', 'Quién cambió la configuración, cuándo y por qué. Lo ve todo el equipo y no se puede borrar.'),
    el('input', {
      type: 'search', class: 'buscar-tabla', placeholder: 'Buscar en el registro', value: busqueda.registro, 'aria-label': 'Buscar en el registro', dataset: { foco: 'buscar-registro' },
      oninput: (ev) => { busqueda.registro = ev.target.value; primeraPagina('registro'); pintarFilasRegistro(); },
    }),
    tabla(['Cuándo', 'Quién', 'Qué cambió', 'Motivo'], filasRegistro(lista), 'tabla-registro'),
    el('div', { id: 'registro-paginas' }, piePaginas(lista, 'registro', pintarFilasRegistro)),
  ];
}

function registroFiltrado() {
  const buscado = sinTildes(busqueda.registro.trim());
  return e.registro.filter((c) => !buscado || sinTildes(`${fraseDe(c)} ${c.autor} ${c.motivo || ''}`).includes(buscado));
}

function filasRegistro(lista) {
  const ahora = ahoraServidor();
  if (!lista.length) {
    return el('tr', {}, el('td', { colspan: '4', class: 'secundario' }, busqueda.registro.trim() ? `Nada con «${busqueda.registro.trim()}».` : 'Todavía no hay cambios.'));
  }
  return enPagina(lista, 'registro').map((c) => el('tr', {},
    el('td', { class: 'secundario' }, fechaHora(Date.parse(c.fecha), ahora)),
    el('td', { 'data-etiqueta': 'Quién' }, c.autor),
    el('td', { class: 'envolver' }, fraseDe(c)),
    el('td', { class: 'envolver secundario', 'data-etiqueta': 'Motivo' }, c.motivo ? `«${c.motivo}»` : '–')));
}

/** La búsqueda y las páginas rehacen solo las filas y el pie: el campo no pierde lo escrito ni el foco. */
function pintarFilasRegistro() {
  const lista = registroFiltrado();
  vaciar($('tabla-registro').tBodies[0], filasRegistro(lista));
  vaciar($('registro-paginas'), piePaginas(lista, 'registro', pintarFilasRegistro));
}
