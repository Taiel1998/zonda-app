// La vista Ajustes, como los de la app: esta red y este navegador, las personas (el administrador suma gente
// con un código y su QR, cambia perfiles y quita), los equipos con su importancia, la severidad por tipo de
// ataque, el registro de cambios y Telegram. El analista ve todo sin los botones de administrador; igual
// el servidor controla cada cambio (403).

import { el, fechaHora, haceCuanto, hora, numero, vaciar } from './util.js';
import {
  $, IMPORTANCIAS, NIVEL, ORDEN_SEVERIDAD, PERFILES, TIPOS, ahoraServidor, e, esAdmin, mandar, nombrePersona, pedir,
} from './comun.js';
import { abrirHoja, avisoInferior, confirmar } from './hojas.js';

const ORDEN_TIPOS_SEVERIDAD = ['fuerza_bruta', 'escaneo', 'ddos', 'probable_falsa_alarma', 'sin_tipificar', 'servicio_no_autorizado', 'volumen_inusual'];
const MODOS_AVISO = { sonido: 'Con sonido', vibracion: 'Sin sonido', sin_aviso: 'Sin aviso' };
let alSalir = () => {};
let alCambiarTema = () => {};
let esperaTelegram = null;

export function configurarAjustes({ salir, cambiarTema }) {
  alSalir = salir;
  alCambiarTema = cambiarTema;
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
  const pedidos = await Promise.allSettled([actualizarPersonas(), pedir('/equipos'), pedir('/severidades'), pedir('/registro'), pedir('/telegram')]);
  if (pedidos[1].status === 'fulfilled') e.equipos = pedidos[1].value;
  if (pedidos[2].status === 'fulfilled') e.severidades = pedidos[2].value;
  if (pedidos[3].status === 'fulfilled') e.registro = pedidos[3].value;
  if (pedidos[4].status === 'fulfilled') e.telegram = pedidos[4].value;
  if (e.vista === 'ajustes') pintarAjustes();
}

function tarjeta(titulo, sub, ...contenido) {
  return el('article', { class: 'tarjeta' },
    el('div', { class: 'tarjeta-cabecera' }, el('div', {}, el('h2', { class: 'tarjeta-titulo' }, titulo), sub ? el('p', { class: 'tarjeta-sub' }, sub) : null)),
    ...contenido);
}

const notaSoloAdmin = () => (esAdmin() ? null : el('p', { class: 'solo-admin-nota' }, 'Lo cambia un administrador.'));

export function pintarAjustes() {
  const red = e.acceso.red ? e.acceso.red.nombre : 'Red';
  const yo = e.personas.find((p) => p.vos);
  const tema = document.documentElement.dataset.tema;
  vaciar($('ajustes'),
    tarjeta('Esta red', `${red} · ${location.host}`,
      el('ul', { class: 'filas' },
        el('li', {}, el('span', { class: 'titulo-fila' }, 'Este navegador'), el('span', { class: 'detalle-fila' }, `${yo ? nombrePersona(yo) : 'Tablero web'} · ${PERFILES[e.perfil] || ''}${e.acceso.vence ? ` · el acceso vence el ${fechaHora(Date.parse(e.acceso.vence), 0)}` : ''}`),
          el('span', { class: 'acciones-fila' }, el('button', { type: 'button', class: 'boton-secundario boton-chico', onclick: () => alSalir() }, 'Salir'))),
        el('li', {}, el('span', { class: 'titulo-fila' }, 'Servidor de alertas'), el('span', { class: 'detalle-fila' }, `Versión ${e.salud ? e.salud.version : '–'} · HTTPS con el certificado de la red`)),
        el('li', {}, el('span', { class: 'titulo-fila' }, 'Tema'), el('span', { class: 'detalle-fila' }, tema === 'oscuro' ? 'Oscuro' : 'Claro'),
          el('span', { class: 'acciones-fila' }, el('button', { type: 'button', class: 'boton-secundario boton-chico', onclick: () => { alCambiarTema(); pintarAjustes(); } }, tema === 'oscuro' ? 'Pasar a claro' : 'Pasar a oscuro'))))),
    tarjetaPersonas(),
    tarjetaEquipos(),
    tarjetaSeveridades(),
    tarjetaTelegram(),
    tarjetaRegistro());
}

// ---- Personas ----

function tarjetaPersonas() {
  const ahora = ahoraServidor();
  const filas = e.personas.map((p) => el('li', {},
    el('span', { class: 'titulo-fila' }, nombrePersona(p), p.vos ? el('span', { class: 'etiqueta', style: 'margin-left:8px' }, 'vos') : null),
    el('span', { class: 'detalle-fila' }, `${p.telefono}${p.ultimoUso ? ` · usó Zonda ${haceCuanto(Date.parse(p.ultimoUso), ahora)}` : ''}`),
    el('span', { class: 'acciones-fila' },
      el('span', { class: `etiqueta${p.perfil === 'administrador' ? ' admin' : ''}` }, PERFILES[p.perfil]),
      esAdmin() && !p.vos ? el('button', { type: 'button', class: 'boton-secundario boton-chico', onclick: () => hojaPersona(p) }, 'Cambiar') : null)));
  return tarjeta('Personas', 'Quiénes usan Zonda en esta red. Los administradores cambian la configuración y suman gente.',
    el('ul', { class: 'filas' }, filas),
    esAdmin() ? el('div', { class: 'acciones-detalle' }, el('button', { type: 'button', class: 'boton-principal', onclick: hojaAgregarPersona }, 'Agregar persona')) : notaSoloAdmin());
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
            const codigo = await mandar('/personas/codigo', 'POST', { nombre: nombre.value.trim(), perfil });
            mostrarCodigo(h, codigo);
          } catch (falla) {
            error.hidden = false;
            error.textContent = falla.message;
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

function hojaPersona(p) {
  const pasarA = p.perfil === 'administrador' ? 'analista' : 'administrador';
  abrirHoja({
    titulo: nombrePersona(p),
    subtitulo: `${PERFILES[p.perfil]} · ${p.telefono}`,
    cuerpo: [],
    acciones: [
      { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
      { texto: 'Quitar del equipo', tipo: 'peligro', alHacer: async (h) => { h.cerrar(); quitarPersona(p); } },
      { texto: pasarA === 'administrador' ? 'Hacer administrador' : 'Pasar a analista', tipo: 'principal', alHacer: async (h) => {
        h.cerrar();
        try {
          await mandar(`/personas/${encodeURIComponent(p.id)}/perfil`, 'PUT', { perfil: pasarA });
          avisoInferior(`${nombrePersona(p)} ahora es ${PERFILES[pasarA].toLowerCase()}.`);
        } catch (falla) { avisoInferior(falla.message, { duracion: 7000 }); }
        actualizarAjustes();
      } },
    ],
  });
}

async function quitarPersona(p) {
  if (!await confirmar({ titulo: `¿Quitar a ${nombrePersona(p)} del equipo?`, texto: 'Su dispositivo deja de recibir alertas y no puede volver a entrar sin un código nuevo.', aceptar: 'Quitar', peligro: true })) return;
  try {
    await mandar(`/personas/${encodeURIComponent(p.id)}`, 'DELETE');
    avisoInferior(`Se quitó a ${nombrePersona(p)} del equipo.`);
  } catch (falla) { avisoInferior(falla.message, { duracion: 7000 }); }
  actualizarAjustes();
}

// ---- Equipos ----

function tarjetaEquipos() {
  const filas = e.equipos.map((q) => el('li', {},
    el('span', { class: 'titulo-fila' }, q.nombre),
    el('span', { class: 'detalle-fila' }, q.ip),
    el('span', { class: 'acciones-fila' },
      el('span', { class: `etiqueta${q.importancia === 'critico' ? ' critico' : ''}` }, IMPORTANCIAS[q.importancia].nombre),
      esAdmin() ? el('button', { type: 'button', class: 'boton-secundario boton-chico', onclick: () => hojaImportancia(q) }, 'Cambiar') : null)));
  return tarjeta('Equipos', 'La importancia de un equipo sube o baja un nivel la severidad de sus alertas.', el('ul', { class: 'filas' }, filas), notaSoloAdmin());
}

function hojaImportancia(q) {
  let elegida = q.importancia;
  const motivo = el('textarea', { maxlength: '500', placeholder: 'Por ejemplo: es el servidor de sueldos.' });
  const error = el('div', { class: 'error-hoja', hidden: true });
  const opciones = Object.entries(IMPORTANCIAS).map(([id, imp]) => el('button', {
    type: 'button', class: 'opcion-grande', 'aria-pressed': String(id === elegida), dataset: { id },
    onclick: () => { elegida = id; for (const b of opciones) b.setAttribute('aria-pressed', String(b.dataset.id === elegida)); },
  }, el('span', { class: 'punto', style: `background:${id === 'critico' ? 'var(--info)' : 'var(--texto-3)'}` }), el('strong', {}, imp.nombre), el('span', { class: 'explicacion' }, imp.explicacion)));
  abrirHoja({
    titulo: `Importancia de ${q.nombre}`,
    subtitulo: q.ip,
    cuerpo: [...opciones, el('label', { class: 'campo' }, el('span', {}, '¿Por qué la cambiás?', el('span', { class: 'asterisco' }, ' *')), motivo, el('small', {}, 'Queda en el registro de cambios con tu nombre y la hora.')), error],
    acciones: [
      { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
      {
        texto: 'Guardar cambios', tipo: 'principal', alHacer: async (h) => {
          if (elegida === q.importancia) { h.cerrar(); return; }
          if (!motivo.value.trim()) { error.hidden = false; error.textContent = 'Escribí por qué la cambiás.'; return; }
          try {
            await mandar(`/equipos/${encodeURIComponent(q.ip)}`, 'PUT', { importancia: elegida, motivo: motivo.value.trim() });
            h.cerrar();
            avisoInferior(`${q.nombre} pasó a ${IMPORTANCIAS[elegida].nombre.toLowerCase()}. Sus alertas cambian de severidad.`);
            actualizarAjustes();
          } catch (falla) { error.hidden = false; error.textContent = falla.message; }
        },
      },
    ],
  });
}

// ---- Severidad por tipo de ataque ----

function tarjetaSeveridades() {
  const tipos = ORDEN_TIPOS_SEVERIDAD.filter((t) => e.severidades[t]);
  const filas = tipos.map((t) => el('li', {},
    el('span', { class: 'titulo-fila' }, TIPOS[t]),
    el('span', { class: 'acciones-fila' }, el('span', { class: `sev ${e.severidades[t]}` }, NIVEL[e.severidades[t]]))));
  return tarjeta('Severidad por tipo de ataque', 'Con qué severidad llega cada tipo. Cambia los avisos y el orden de las alertas.',
    el('ul', { class: 'filas' }, filas),
    esAdmin() ? el('div', { class: 'acciones-detalle' }, el('button', { type: 'button', class: 'boton-secundario', onclick: hojaSeveridades }, 'Cambiar severidades')) : notaSoloAdmin());
}

function hojaSeveridades() {
  const nuevas = { ...e.severidades };
  const motivo = el('textarea', { maxlength: '500', placeholder: 'Por ejemplo: el backup nocturno usa SSH y daba falsas alarmas.' });
  const error = el('div', { class: 'error-hoja', hidden: true });
  const filas = ORDEN_TIPOS_SEVERIDAD.filter((t) => nuevas[t]).map((t) => {
    const botones = ORDEN_SEVERIDAD.map((s) => el('button', {
      type: 'button', class: 'chip-filtro', 'aria-pressed': String(nuevas[t] === s), dataset: { s },
      onclick: () => { nuevas[t] = s; for (const b of botones) b.setAttribute('aria-pressed', String(b.dataset.s === s)); },
    }, el('span', { class: 'punto', style: `background:var(--${s})` }), NIVEL[s]));
    return el('div', { class: 'grupo-opciones' }, el('p', {}, TIPOS[t]), el('div', { class: 'opciones' }, botones));
  });
  abrirHoja({
    titulo: 'Severidad por tipo de ataque',
    cuerpo: [...filas, el('label', { class: 'campo' }, el('span', {}, '¿Por qué la cambiás?', el('span', { class: 'asterisco' }, ' *')), motivo, el('small', {}, 'Queda en el registro de cambios con tu nombre y la hora.')), error],
    acciones: [
      { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
      {
        texto: 'Guardar cambios', tipo: 'principal', alHacer: async (h) => {
          const cambios = Object.fromEntries(Object.entries(nuevas).filter(([t, s]) => e.severidades[t] !== s));
          if (!Object.keys(cambios).length) { h.cerrar(); return; }
          if (!motivo.value.trim()) { error.hidden = false; error.textContent = 'Escribí por qué la cambiás.'; return; }
          try {
            e.severidades = await mandar('/severidades', 'PUT', { severidades: cambios, motivo: motivo.value.trim() });
            h.cerrar();
            avisoInferior('Severidades guardadas. Las alertas se reordenan con la severidad nueva.');
            actualizarAjustes();
          } catch (falla) { error.hidden = false; error.textContent = falla.message; }
        },
      },
    ],
  });
}

// ---- Telegram de este navegador ----

function tarjetaTelegram() {
  const t = e.telegram;
  if (!t) return tarjeta('Telegram', 'Los avisos de este navegador por Telegram.', el('p', { class: 'nota' }, 'Cargando…'));
  if (!t.vinculado) {
    return tarjeta('Telegram', 'Recibí los avisos de alertas en Telegram, en el chat con el bot de Zonda. Solo viajan la severidad, el servicio, la hora y la red.',
      el('div', { class: 'acciones-detalle' }, el('button', { type: 'button', class: 'boton-principal', onclick: conectarTelegram }, 'Conectar Telegram')));
  }
  const filas = ORDEN_SEVERIDAD.map((s) => {
    const botones = Object.entries(MODOS_AVISO).map(([modo, texto]) => el('button', {
      type: 'button', class: 'chip-filtro', 'aria-pressed': String(t.avisos[s] === modo),
      onclick: async () => {
        try { e.telegram.avisos = await mandar('/telegram/avisos', 'PUT', { ...t.avisos, [s]: modo }); pintarAjustes(); } catch (falla) { avisoInferior(falla.message); }
      },
    }, texto));
    return el('div', { class: 'grupo-opciones' }, el('p', {}, `Alertas ${NIVEL[s].toLowerCase()}s`), el('div', { class: 'opciones' }, botones));
  });
  return tarjeta('Telegram', `Vinculado con @${t.bot}${t.vinculadoEn ? ` desde el ${fechaHora(Date.parse(t.vinculadoEn), 0)}` : ''}.`,
    el('div', { class: 'hoja-cuerpo', style: 'margin-top:4px' }, filas),
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
        el('li', {}, 'Tocá «Abrir Telegram»: se abre el chat con el bot de Zonda.'),
        el('li', {}, 'Tocá «Iniciar». El bot reconoce este navegador.'),
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

function fraseDe(c) {
  const ant = c.antes; const des = c.despues;
  if (c.tipo === 'severidad') return `${TIPOS[c.objeto] || c.objeto} pasó de ${(NIVEL[ant] || ant || '').toLowerCase()} a ${(NIVEL[des] || des || '').toLowerCase()}`;
  if (c.tipo === 'importancia') return `${c.nombre || c.objeto} pasó de ${(IMPORTANCIAS[ant] || { nombre: ant }).nombre.toLowerCase()} a ${(IMPORTANCIAS[des] || { nombre: des }).nombre.toLowerCase()}`;
  if (c.tipo === 'persona_nueva') return `Sumó a ${c.nombre || 'una persona'} al equipo como ${(PERFILES[des] || des || '').toLowerCase()}`;
  if (c.tipo === 'perfil') return `${c.nombre || 'Una persona'} pasó a ${(PERFILES[des] || des || '').toLowerCase()}`;
  if (c.tipo === 'persona_quitada') return c.autorId && c.autorId === c.objeto ? 'Quitó su dispositivo del equipo' : `Quitó a ${c.nombre || 'una persona'} del equipo`;
  return c.tipo;
}

function tarjetaRegistro() {
  const ahora = ahoraServidor();
  const filas = e.registro.slice(0, 40).map((c) => el('li', {},
    el('span', { class: 'titulo-fila', style: 'white-space:normal' }, fraseDe(c)),
    el('span', { class: 'detalle-fila' }, `${c.autor} · ${fechaHora(Date.parse(c.fecha), ahora)}`),
    c.motivo ? el('span', { class: 'registro-motivo', style: 'grid-column:1' }, `«${c.motivo}»`) : null));
  return tarjeta('Registro de cambios', 'Quién cambió la configuración, cuándo y por qué. Lo ve todo el equipo y no se puede borrar.',
    filas.length ? el('ul', { class: 'filas' }, filas) : el('p', { class: 'nota' }, 'Todavía no hay cambios.'));
}

export const cantidadPersonas = () => numero(e.personas.length);
