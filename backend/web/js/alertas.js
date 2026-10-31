// La vista Alertas y el detalle, con lo mismo que la app: los filtros (estado, severidad, servicios, equipos,
// método y responsable) y el horario, revisar en dos pasos con motivo y comentario obligatorios, Deshacer, el
// conflicto cuando otra persona revisó después, y el responsable con las reglas de cada perfil. Además, la
// lista con sus filtros se baja en CSV.

import { el, fechaCsv, fechaHora, horaConSegundos, icono, isoConZona, nuevoId, numero, vaciar } from './util.js';
import {
  $, ESTADOS, METODOS, MOTIVOS, MOTIVOS_POR_RESULTADO, NIVEL, ORDEN_SEVERIDAD, RESULTADOS, ahoraServidor, avisarCambio,
  chipEstado, chipSeveridad, comentarioPropuesto, e, esAdmin, guardarAlerta, mandar, metodosDe, nombreEquipo, nombrePersona,
  pedir, responsableId, servicioCorto, servicioDe, tipoCorto,
} from './comun.js';
import { abrirHoja, avisoInferior } from './hojas.js';
import { bajarArchivo, csvDe, nombreConFecha } from './compartir.js';
import { alternarAyuda, cerrarAyuda } from './ayuda.js';
import {
  RANGOS_ALERTAS, claveRango, esRelativo, fraseRango, horasDe, nombreRango, rangoActual, rangoCorrido, selectorDeHorario,
} from './tiempo.js';

// ---- Sincronización por cursor, como la app ----

export async function sincronizarAlertas() {
  if (!e.sincronizada) {
    let cursor = null;
    for (;;) {
      const pagina = await pedir(cursor ? `/alertas?desde=${encodeURIComponent(cursor)}` : '/alertas');
      for (const a of pagina.alertas) e.alertas.set(a.id, a);
      cursor = pagina.cursorSiguiente;
      if (!pagina.hayMas) break;
    }
    e.cursor = cursor;
    e.sincronizada = true;
    avisarCambio([]);
    return;
  }
  let pagina;
  try {
    pagina = await pedir(`/alertas?desde=${encodeURIComponent(e.cursor)}`);
  } catch (falla) {
    if (falla.estado === 410) { e.sincronizada = false; e.alertas.clear(); }
    throw falla;
  }
  const nuevas = [];
  for (const a of pagina.alertas) {
    if (!e.alertas.has(a.id)) nuevas.push(a);
    e.alertas.set(a.id, a);
  }
  e.cursor = pagina.cursorSiguiente;
  if (pagina.alertas.length) {
    for (const a of nuevas) e.nuevas.add(a.id);
    avisarCambio(nuevas);
  }
}

// ---- Filtros ----

const FILTRO_ESTADO = { todas: 'Todas', sin_revisar: 'Sin revisar', revisadas: 'Revisadas', falsos: 'Falsos positivos' };

function filtrosNuevos() {
  // El horario va aparte de los filtros, arriba a la derecha, como en Grafana (rango: expresiones de tiempo.js).
  return { estado: 'todas', severidades: new Set(), rango: { desde: 'now-30d', hasta: 'now' }, servicios: new Set(), otrosServicios: false, destinos: new Set(), metodos: new Set(), responsable: 'todas' };
}

export const f = filtrosNuevos();
let limite = 50;

/** La lista con las alertas que empezaron en un tramo (una barra de «Alertas en el tiempo»), sin otros filtros. */
export function verAlertasEntre(desde, hasta) {
  Object.assign(f, filtrosNuevos(), { rango: { desde, hasta } });
  limite = 50;
}

/** La lista con las sin revisar de una severidad, desde el principio: lo que cuenta su contador en En vivo y
    lo que abre el aviso de varias altas, como el enlace de la notificación de la app. */
export function verSinRevisar(severidad) {
  Object.assign(f, filtrosNuevos(), { estado: 'sin_revisar', severidades: new Set([severidad]), rango: { desde: 'todo', hasta: 'now' } });
  limite = 50;
}

/** Los servicios con chip propio: los diez con más alertas; el resto entra en "Los demás" (decisión 46). */
function serviciosConChip() {
  const cuenta = new Map();
  for (const a of e.alertas.values()) cuenta.set(a.servicio, (cuenta.get(a.servicio) || 0) + 1);
  return [...cuenta.entries()].sort((x, y) => y[1] - x[1]).slice(0, 10).map(([s]) => s);
}

function pasa(a, horario, conChip) {
  if (f.estado === 'sin_revisar' && a.estadoRevision !== 'sin_revisar') return false;
  if (f.estado === 'revisadas' && a.estadoRevision === 'sin_revisar') return false;
  if (f.estado === 'falsos' && a.estadoRevision !== 'falso_positivo') return false;
  if (f.severidades.size && !f.severidades.has(a.severidad)) return false;
  // Por la hora en que empezó; un rango que termina ahora no tiene final (el reloj del sensor puede ir adelantado).
  const t = Date.parse(a.inicio);
  if (t < horario.desde || (!esRelativo(f.rango) && t >= horario.hasta)) return false;
  if (f.servicios.size || f.otrosServicios) {
    const enChip = conChip.includes(a.servicio);
    if (!(f.servicios.has(a.servicio) || (f.otrosServicios && !enChip))) return false;
  }
  if (f.destinos.size && !f.destinos.has(a.destinoIp)) return false;
  if (f.metodos.size && !metodosDe(a).some((m) => f.metodos.has(m))) return false;
  const quien = responsableId(a);
  if (f.responsable === 'mias' && quien !== e.miId) return false;
  if (f.responsable === 'sin_asignar' && quien) return false;
  if (f.responsable.startsWith('p:') && quien !== f.responsable.slice(2)) return false;
  return true;
}

export function alertasFiltradas() {
  const horario = rangoActual(f.rango);
  const conChip = serviciosConChip();
  return [...e.alertas.values()].filter((a) => pasa(a, horario, conChip)).sort((x, y) => Date.parse(y.inicio) - Date.parse(x.inicio));
}

const cantidadMasFiltros = () => f.servicios.size + (f.otrosServicios ? 1 : 0) + f.destinos.size + f.metodos.size;
const hayFiltros = () => f.estado !== 'todas' || f.severidades.size || claveRango(f.rango) !== 'now-30d|now' || cantidadMasFiltros() || f.responsable !== 'todas';

function textoResponsable() {
  if (f.responsable === 'mias') return 'Asignadas a mí';
  if (f.responsable === 'sin_asignar') return 'Sin asignar';
  if (f.responsable.startsWith('p:')) return `Asignadas a ${nombrePersona(e.personas.find((p) => p.id === f.responsable.slice(2))) || 'otra persona'}`;
  return 'Responsable';
}

function chip(texto, activo, alTocar) {
  return el('button', { type: 'button', class: 'chip-filtro', 'aria-pressed': String(Boolean(activo)), onclick: alTocar }, texto);
}

export function pintarAlertas() {
  const severidades = f.severidades.size ? ORDEN_SEVERIDAD.filter((s) => f.severidades.has(s)).map((s) => NIVEL[s]).join(', ') : 'Severidad';
  vaciar($('filtros-alertas'),
    chip(FILTRO_ESTADO[f.estado], true, hojaEstado),
    chip(severidades, f.severidades.size, hojaSeveridad),
    chip(cantidadMasFiltros() ? `Más filtros (${cantidadMasFiltros()})` : 'Más filtros', cantidadMasFiltros(), hojaMasFiltros),
    chip(textoResponsable(), f.responsable !== 'todas', hojaResponsableFiltro),
    hayFiltros() ? el('button', { type: 'button', class: 'boton-texto', onclick: () => { Object.assign(f, filtrosNuevos()); limite = 50; pintarAlertas(); } }, 'Limpiar filtros') : null);

  pintarBarraAlertas();
  const lista = alertasFiltradas();
  const sinRevisar = lista.filter((a) => a.estadoRevision === 'sin_revisar').length;
  vaciar($('resumen-filtros-alertas'), el('strong', {}, `${numero(lista.length)} ${lista.length === 1 ? 'alerta' : 'alertas'}`), ` ${fraseRango(f.rango)} · ${numero(sinRevisar)} sin revisar · las más nuevas primero`);
  const cuerpo = $('tabla-alertas').tBodies[0];
  vaciar(cuerpo, lista.slice(0, limite).map((a) => {
    const quien = responsableId(a);
    return el('tr', { class: 'clic', tabindex: '0', onclick: () => abrirDetalle(a.id), onkeydown: (ev) => { if (ev.key === 'Enter') abrirDetalle(a.id); } },
      el('td', {}, chipSeveridad(a.severidad)),
      el('td', {}, el('strong', {}, tipoCorto(a))),
      el('td', { class: 'par' }, `${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')}`),
      el('td', { class: 'secundario', 'data-etiqueta': 'Servicio' }, servicioDe(a)),
      el('td', { class: 'secundario', 'data-etiqueta': 'Empezó' }, fechaHora(Date.parse(a.inicio), ahoraServidor())),
      el('td', { class: 'num', 'data-etiqueta': 'Conexiones' }, numero(a.cantidadFlujos)),
      el('td', {}, el('span', { class: 'metodos' }, METODOS.map((m) => el('span', { class: `metodo${a.veredictos[m.id] ? ' si' : ''}`, title: `${m.nombre}: ${a.veredictos[m.id] ? 'anómalo' : 'normal'}` }, m.corto)))),
      el('td', {}, quien ? chipResponsable(a.asignacion.responsable) : el('span', { class: 'secundario' }, 'Sin asignar')),
      el('td', {}, chipEstado(a.estadoRevision)));
  }));
  $('vacio-alertas').hidden = lista.length > 0;
  $('vacio-alertas').textContent = 'Ninguna alerta con estos filtros. Cambiá o limpiá los filtros.';
  $('ver-mas').hidden = lista.length <= limite;
}

export function verMas() { limite += 50; pintarAlertas(); }

/** Toda la lista con los filtros y el horario de arriba, no solo lo que se ve; los métodos y la revisión, en palabras. */
function bajarCsvAlertas() {
  const lista = alertasFiltradas();
  if (!lista.length) { avisoInferior('No hay alertas con estos filtros para bajar'); return; }
  const columnas = ['Severidad', 'Tipo', 'Origen', 'IP de origen', 'Destino', 'IP de destino', 'Servicio', 'Puerto', 'Empezó', 'Terminó',
    'Conexiones', ...METODOS.map((m) => m.nombre), 'Responsable', 'Estado', 'Revisó', 'Motivo', 'Comentario'];
  const filas = lista.map((a) => {
    const revisada = a.estadoRevision !== 'sin_revisar';
    const ultima = revisada ? (a.historial || []).reduce((x, r) => (!x || Date.parse(r.revisadaEn || r.fecha) >= Date.parse(x.revisadaEn || x.fecha) ? r : x), null) : null;
    return [
      NIVEL[a.severidad], tipoCorto(a), nombreEquipo(a, 'origen'), a.origenIp, nombreEquipo(a, 'destino'), a.destinoIp,
      servicioCorto(a.servicio), `${(a.protocolo || 'tcp').toUpperCase()}/${a.puerto}`, fechaCsv(Date.parse(a.inicio)), fechaCsv(Date.parse(a.fin)),
      a.cantidadFlujos, ...METODOS.map((m) => (a.veredictos[m.id] ? 'Marcó' : 'No marcó')),
      responsableId(a) ? a.asignacion.responsable : 'Sin asignar', ESTADOS[a.estadoRevision] || a.estadoRevision,
      ultima ? ultima.persona || ultima.autor : '', ultima ? MOTIVOS[ultima.motivo] || ultima.motivo : '', ultima ? ultima.comentario : '',
    ];
  });
  bajarArchivo(`${nombreConFecha('alertas')}.csv`, csvDe(columnas, filas));
}

// ---- El horario de la lista, arriba a la derecha, con el mismo selector de En vivo ----

function pintarBarraAlertas() {
  $('alertas-rango-texto').textContent = nombreRango(f.rango);
  $('alertas-rango').title = horasDe(f.rango) || 'Desde la primera alerta';
  $('alertas-atras').disabled = f.rango.desde === 'todo';
  $('alertas-adelante').disabled = esRelativo(f.rango);
}

function ponerRangoAlertas(rango) {
  if (!rango || claveRango(rango) === claveRango(f.rango)) return;
  f.rango = rango;
  limite = 50;
  pintarAlertas();
}

/** El selector del horario de la lista: los rangos de las alertas, que se guardan más que el tráfico. */
export function configurarAlertas() {
  for (const lugar of document.querySelectorAll('#barra-tiempo-alertas [data-icono], #alertas-csv [data-icono]')) lugar.prepend(icono(lugar.dataset.icono));
  $('alertas-csv').addEventListener('click', bajarCsvAlertas);
  selectorDeHorario({
    selector: $('selector-alertas'),
    boton: $('alertas-rango'),
    rapidos: RANGOS_ALERTAS,
    clave: 'zonda.tablero.rangos-alertas',
    nota: 'La lista muestra las alertas que empezaron en ese horario.',
    actual: () => f.rango,
    alElegir: ponerRangoAlertas,
  });
  $('alertas-atras').addEventListener('click', () => ponerRangoAlertas(rangoCorrido(f.rango, -1)));
  $('alertas-adelante').addEventListener('click', () => ponerRangoAlertas(rangoCorrido(f.rango, 1)));
  pintarBarraAlertas();
}

function chipResponsable(nombre) {
  return el('span', { class: 'responsable-chip' }, el('span', { class: 'inicial' }, (nombre || '?').slice(0, 1).toUpperCase()), nombre);
}

// Las hojas de cada filtro: se elige y se aplica, como en la app.

function hojaDeOpciones({ titulo, subtitulo, grupos, alAplicar }) {
  abrirHoja({
    titulo,
    subtitulo,
    cuerpo: grupos.map((g) => el('div', { class: 'grupo-opciones' }, g.titulo ? el('p', {}, g.titulo) : null, el('div', { class: 'opciones' }, g.opciones))),
    acciones: [
      { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
      { texto: 'Aplicar', tipo: 'principal', alHacer: (h) => { alAplicar(); h.cerrar(); limite = 50; pintarAlertas(); } },
    ],
  });
}

function opcionUnica(valores, actual, alElegir) {
  const botones = Object.entries(valores).map(([id, nombre]) => el('button', {
    type: 'button', class: 'chip-filtro', 'aria-pressed': String(id === actual()),
    onclick: () => { alElegir(id); for (const b of botones) b.setAttribute('aria-pressed', String(b.dataset.id === id)); },
    dataset: { id },
  }, nombre));
  return botones;
}

function opcionMultiple(valores, conjunto) {
  return valores.map(({ id, nombre, punto }) => el('button', {
    type: 'button', class: 'chip-filtro', 'aria-pressed': String(conjunto.has(id)),
    onclick: (ev) => { if (conjunto.has(id)) conjunto.delete(id); else conjunto.add(id); ev.currentTarget.setAttribute('aria-pressed', String(conjunto.has(id))); },
  }, punto ? el('span', { class: 'punto', style: `background:${punto}` }) : null, nombre));
}

function hojaEstado() {
  let elegido = f.estado;
  hojaDeOpciones({ titulo: 'Estado de revisión', grupos: [{ opciones: opcionUnica(FILTRO_ESTADO, () => elegido, (id) => { elegido = id; }) }], alAplicar: () => { f.estado = elegido; } });
}

function hojaSeveridad() {
  const elegidas = new Set(f.severidades);
  hojaDeOpciones({
    titulo: 'Severidad',
    subtitulo: 'Podés elegir varias. Sin ninguna, se ven todas.',
    grupos: [{ opciones: opcionMultiple(ORDEN_SEVERIDAD.map((s) => ({ id: s, nombre: NIVEL[s], punto: `var(--${s})` })), elegidas) }],
    alAplicar: () => { f.severidades = elegidas; },
  });
}

function hojaMasFiltros() {
  const servicios = new Set(f.servicios);
  const destinos = new Set(f.destinos);
  const metodos = new Set(f.metodos);
  const conChip = serviciosConChip();
  const otros = new Set(f.otrosServicios ? ['__otros'] : []);
  const cuentaDestinos = new Map();
  for (const a of e.alertas.values()) {
    const actual = cuentaDestinos.get(a.destinoIp) || { nombre: nombreEquipo(a, 'destino'), n: 0 };
    actual.n += 1;
    cuentaDestinos.set(a.destinoIp, actual);
  }
  const destinosOrdenados = [...cuentaDestinos.entries()].sort((x, y) => y[1].n - x[1].n).slice(0, 12);
  hojaDeOpciones({
    titulo: 'Más filtros',
    subtitulo: 'Podés elegir varios en cada grupo. Tocá uno elegido para sacarlo.',
    grupos: [
      { titulo: 'Servicio', opciones: [...opcionMultiple(conChip.map((s) => ({ id: s, nombre: servicioCorto(s) })), servicios), ...(e.alertas.size && new Set([...e.alertas.values()].map((a) => a.servicio)).size > conChip.length ? opcionMultiple([{ id: '__otros', nombre: 'Los demás' }], otros) : [])] },
      { titulo: 'Equipo destino', opciones: opcionMultiple(destinosOrdenados.map(([ip, d]) => ({ id: ip, nombre: d.nombre === ip ? ip : `${d.nombre}` })), destinos) },
      { titulo: 'Método que la detectó', opciones: opcionMultiple(METODOS.map((m) => ({ id: m.id, nombre: m.nombre })), metodos) },
    ],
    alAplicar: () => { f.servicios = servicios; f.destinos = destinos; f.metodos = metodos; f.otrosServicios = otros.has('__otros'); },
  });
}

function hojaResponsableFiltro() {
  let elegido = f.responsable;
  const valores = { todas: 'Todas', mias: 'Asignadas a mí', sin_asignar: 'Sin asignar' };
  for (const p of e.personas) if (p.id !== e.miId) valores[`p:${p.id}`] = `Asignadas a ${nombrePersona(p)}`;
  hojaDeOpciones({ titulo: 'Responsable', grupos: [{ opciones: opcionUnica(valores, () => elegido, (id) => { elegido = id; }) }], alAplicar: () => { f.responsable = elegido; } });
}

// ---- Detalle de una alerta ----

/** Cuánto se pasó un método de lo normal, con palabras y en la misma escala para la IA y los umbrales,
    como la app (decisión 37): desde el doble del límite dice cuántas veces; antes, «un poco más». */
function cuantoMas(anomalo, valor, limite) {
  if (!anomalo) return 'Dentro de lo normal';
  const veces = valor / limite;
  return limite > 0 && veces >= 2 ? `${Math.round(veces)} veces más de lo normal` : 'Un poco más de lo normal';
}

/** Lo que dice el detalle de cada método, sin el umbral ni los números con ceros (como la app). */
function lecturas(v) {
  return {
    ia: cuantoMas(v.ia, v.errorReconstruccion, v.umbralIa),
    // Sin marcarla, «Dentro de lo normal» aunque el servicio sea nuevo: el texto no contradice la etiqueta.
    umbrales: !v.umbrales ? 'Dentro de lo normal' : v.servicioSinLineaBase ? 'Servicio nuevo en la red' : cuantoMas(true, v.contexto60s, v.umbralServicio),
    reglas: v.parEnCatalogo ? 'Servicio autorizado' : 'Servicio no autorizado',
  };
}

/** El único «?» del detalle, con los textos de la app: las conexiones y los tres métodos, en un globito. */
function alternarAyudaDetalle(ev) {
  alternarAyuda(ev.currentTarget, 'Qué significa cada dato', [
    el('p', {}, el('b', {}, 'Conexiones en 60 s:'), ' cuántas veces el origen se conectó a este servicio en un minuto.'),
    el('p', {}, el('b', {}, 'Conexiones en total:'), ' todas las de estos dos equipos, hasta que pasan 15 minutos sin ninguna.'),
    el('p', {}, el('b', {}, 'Modelo de IA:'), ' aprendió el tráfico normal de tu red y marca lo que se aleja de eso.'),
    el('p', {}, el('b', {}, 'Umbrales:'), ' marcan si hay más conexiones a un servicio por minuto que en un día normal.'),
    el('p', {}, el('b', {}, 'Reglas:'), ' marcan los servicios que no están en la lista de autorizados.'),
    el('p', {}, 'Alcanza con que uno marque para que haya alerta.'),
  ]);
}

export function abrirDetalle(id, enfocar = true) {
  const a = e.alertas.get(id);
  if (!a) return;
  // El «?» se vuelve a armar: su globito quedaría suelto.
  cerrarAyuda();
  e.detalleAbierto = id;
  e.nuevas.delete(id);
  const panel = $('detalle');
  const v = a.veredictos;
  const ahora = ahoraServidor();
  const explicacion = lecturas(v);
  const historial = a.historial || [];
  const quien = responsableId(a);
  const accionesResponsable = [];
  if (esAdmin()) {
    accionesResponsable.push(el('button', { type: 'button', class: 'boton-secundario boton-chico', onclick: () => hojaAsignar(a) }, quien ? 'Cambiar' : 'Asignar'));
  } else if (!quien) {
    accionesResponsable.push(el('button', { type: 'button', class: 'boton-secundario boton-chico', onclick: () => asignar(a, e.miId) }, 'Me la asigno'));
  } else if (quien === e.miId) {
    accionesResponsable.push(el('button', { type: 'button', class: 'boton-secundario boton-chico', onclick: () => asignar(a, null) }, 'Soltarla'));
  }
  vaciar(panel,
    el('div', { class: 'detalle-cabecera' },
      el('div', {},
        el('div', { style: 'display:flex;gap:8px;align-items:center;flex-wrap:wrap' }, chipSeveridad(a.severidad), chipEstado(a.estadoRevision)),
        el('div', { class: 'titulo-con-ayuda' },
          el('h2', { id: 'detalle-titulo' }, tipoCorto(a)),
          el('button', {
            type: 'button', class: 'boton-ayuda', 'aria-haspopup': 'dialog', 'aria-expanded': 'false', 'aria-controls': 'ayuda-flotante',
            'aria-label': 'Qué significa cada dato', title: 'Qué significa cada dato', onclick: alternarAyudaDetalle,
          }, '?')),
        el('p', {}, a.tipoAtaque && a.confianzaTipo != null ? `Lo tipificó la segunda etapa con una confianza del ${numero(a.confianzaTipo * 100, 0)} %.` : 'Sin tipo de la segunda etapa: lo vieron las reglas o los umbrales.')),
      el('button', { type: 'button', class: 'boton-icono', 'aria-label': 'Cerrar el detalle', onclick: cerrarDetalle }, icono('cruz'))),
    el('div', { class: 'recorrido' },
      el('div', {}, el('strong', {}, nombreEquipo(a, 'origen')), el('span', {}, a.origenIp)),
      el('span', { class: 'flecha' }, icono('flecha')),
      el('div', { style: 'text-align:right' }, el('strong', {}, nombreEquipo(a, 'destino')), el('span', {}, a.destinoIp))),
    el('div', { class: 'acciones-detalle' },
      el('button', { type: 'button', class: 'boton-principal', onclick: () => revisar(a) }, a.estadoRevision === 'sin_revisar' ? 'Revisar' : 'Cambiar la revisión')),
    el('section', { class: 'detalle-seccion' },
      el('h3', {}, 'Responsable'),
      el('div', { style: 'display:flex;align-items:center;gap:10px;justify-content:space-between' },
        quien ? chipResponsable(a.asignacion.responsable) : el('span', { class: 'nota' }, 'Sin asignar'),
        el('div', { style: 'display:flex;gap:6px' }, accionesResponsable))),
    el('section', { class: 'detalle-seccion' },
      el('dl', { class: 'datos' },
        el('div', {}, el('dt', {}, 'Servicio'), el('dd', {}, servicioDe(a))),
        el('div', {}, el('dt', {}, 'Conexiones en total'), el('dd', {}, numero(a.cantidadFlujos))),
        el('div', {}, el('dt', {}, 'Empezó'), el('dd', {}, fechaHoraSeg(Date.parse(a.inicio), ahora))),
        el('div', {}, el('dt', {}, 'Terminó'), el('dd', {}, fechaHoraSeg(Date.parse(a.fin), ahora))),
        el('div', {}, el('dt', {}, 'Conexiones en 60 s'), el('dd', {}, numero(v.contexto60s))),
        el('div', {}, el('dt', {}, 'Aviso de Telegram'), el('dd', {}, a.avisoTelegram ? `Salió a las ${horaConSegundos(Date.parse(a.avisoTelegram))}` : 'No salió')),
        // Como en la app: un solo puerto no dice nada; varios, que el origen probó puertos.
        a.puertosDistintos > 1 ? el('div', {}, el('dt', {}, 'Puertos distintos'), el('dd', {}, numero(a.puertosDistintos))) : null)),
    el('section', { class: 'detalle-seccion' },
      el('h3', {}, 'Predicción por método'),
      METODOS.map((m) => el('div', { class: `veredicto ${v[m.id] ? 'anomalo' : 'normal'}` },
        el('span', { class: 'veredicto-icono' }, icono(m.icono)),
        el('span', { class: 'veredicto-nombre' }, m.resumen),
        el('span', { class: 'veredicto-resultado' }, v[m.id] ? 'Anómalo' : 'Normal'),
        el('span', { class: 'veredicto-explicacion' }, explicacion[m.id])))),
    el('section', { class: 'detalle-seccion' },
      el('h3', {}, 'Historial'),
      historial.length || (a.asignaciones || []).length ? el('ul', { class: 'historial' }, lineasHistorial(a).map((linea) => el('li', {},
        el('div', {}, el('strong', {}, linea.titulo), linea.extra ? ` · ${linea.extra}` : ''),
        linea.comentario ? el('div', { class: 'comentario' }, linea.comentario) : null,
        el('div', { class: 'meta' }, `${linea.quien} · ${fechaHora(linea.cuando, ahora)}`))))
        : el('p', { class: 'nota' }, 'Todavía nadie la revisó.')));
  $('velo').hidden = false;
  panel.hidden = false;
  if (enfocar) panel.querySelector('.boton-icono').focus();
}

function fechaHoraSeg(ms, ahora) {
  const mismoDia = new Date(ms).toDateString() === new Date(ahora).toDateString();
  return mismoDia ? horaConSegundos(ms) : `${fechaHora(ms, ahora).split(' ')[0]} ${horaConSegundos(ms)}`;
}

/** Las revisiones y los cambios de responsable, de lo más nuevo a lo más viejo. */
function lineasHistorial(a) {
  const lineas = (a.historial || []).map((r) => ({
    cuando: Date.parse(r.revisadaEn || r.fecha),
    titulo: ESTADOS[r.resultado] || r.resultado,
    extra: MOTIVOS[r.motivo] || r.motivo,
    comentario: r.comentario,
    quien: r.persona || r.autor,
  }));
  for (const asignacion of a.asignaciones || []) {
    lineas.push({
      cuando: Date.parse(asignacion.fecha),
      titulo: asignacion.responsable ? `Asignada a ${asignacion.responsable}` : 'Quedó sin asignar',
      quien: asignacion.por,
    });
  }
  return lineas.sort((x, y) => y.cuando - x.cuando);
}

export function cerrarDetalle() {
  cerrarAyuda();
  e.detalleAbierto = null;
  $('detalle').hidden = true;
  $('velo').hidden = true;
}

// ---- Revisar: dos pasos, Deshacer y el conflicto ----

function revisar(a) {
  let resultado = null;
  let motivo = null;
  let comentario = '';
  let comentarioTocado = false;
  const hoja = abrirHoja({ titulo: '', cuerpo: [] });

  function paso1() {
    hoja.pintar({
      paso: 'Paso 1 de 2',
      titulo: 'Revisar',
      cuerpo: Object.entries(RESULTADOS).map(([id, r]) => el('button', {
        type: 'button', class: 'opcion-grande', 'aria-pressed': String(resultado === id),
        onclick: () => { if (resultado !== id) { motivo = null; if (!comentarioTocado) comentario = ''; } resultado = id; paso2(); },
      }, el('span', { class: 'punto', style: `background:${r.color}` }), el('strong', {}, r.nombre), el('span', { class: 'explicacion' }, r.explicacion))),
      acciones: [{ texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() }],
    });
  }

  function paso2() {
    const texto = el('textarea', { maxlength: '1000', 'aria-required': 'true' });
    texto.value = comentario;
    const confirmarBoton = () => hoja.pintar(contenidoPaso2());
    texto.addEventListener('input', () => {
      comentario = texto.value;
      comentarioTocado = true;
      const boton = [...document.querySelectorAll('.hoja-acciones .boton-principal')].pop();
      if (boton) boton.disabled = !(motivo && comentario.trim());
    });
    function contenidoPaso2() {
      const motivos = MOTIVOS_POR_RESULTADO[resultado].map((id) => el('button', {
        type: 'button', class: 'chip-filtro', 'aria-pressed': String(motivo === id),
        onclick: () => {
          motivo = id;
          if (!comentarioTocado || !comentario.trim()) { comentario = comentarioPropuesto(id, a); comentarioTocado = false; }
          texto.value = comentario;
          confirmarBoton();
        },
      }, MOTIVOS[id]));
      return {
        paso: 'Paso 2 de 2',
        titulo: RESULTADOS[resultado].pregunta,
        cuerpo: [
          el('div', { class: 'grupo-opciones' }, el('p', {}, 'Motivo', el('span', { class: 'asterisco' }, ' *')), el('div', { class: 'opciones' }, motivos)),
          el('label', { class: 'campo' }, el('span', {}, 'Comentario', el('span', { class: 'asterisco' }, ' *')), texto, el('small', {}, 'Queda en el historial de la alerta.')),
          el('p', { class: 'leyenda-obligatorio', style: 'margin:0' }, el('span', { class: 'asterisco' }, '*'), ' Obligatorio'),
        ],
        acciones: [
          { texto: 'Volver', tipo: 'secundario', alHacer: () => paso1() },
          { texto: 'Confirmar revisión', tipo: 'principal', deshabilitado: !(motivo && comentario.trim()), alHacer: (h) => { h.cerrar(); enviarRevision(a, { resultado, motivo, comentario: comentario.trim() }); } },
        ],
      };
    }
    hoja.pintar(contenidoPaso2());
  }

  paso1();
}

/** Como la app: el aviso "Revisaste…" con Deshacer y, si nadie la deshace en 5 segundos, se manda. */
function enviarRevision(a, eleccion) {
  const entrada = { accionId: nuevoId(), ...eleccion, versionVista: a.version, forzar: false, revisadaEn: isoConZona() };
  let deshecha = false;
  avisoInferior(`Revisaste ${tipoCorto(a)} como ${RESULTADOS[eleccion.resultado].nombre.toLowerCase()}.`, {
    accion: 'Deshacer',
    alAccion: () => { deshecha = true; avisoInferior('Se deshizo la revisión.', { duracion: 3000 }); },
    duracion: 5000,
    alTerminar: () => { if (!deshecha) mandarRevision(a, entrada); },
  });
}

async function mandarRevision(a, entrada) {
  try {
    guardarAlerta(await mandar(`/alertas/${encodeURIComponent(a.id)}/revision`, 'POST', entrada));
  } catch (falla) {
    if (falla.estado === 409 && falla.datos && falla.datos.revisionAjena) {
      conflicto(a, entrada, falla.datos);
      return;
    }
    avisoInferior(`No se pudo guardar la revisión: ${falla.message}`, { accion: 'Reintentar', alAccion: () => mandarRevision(a, entrada), duracion: 10000 });
  }
}

function conflicto(a, entrada, datos) {
  const ajena = datos.revisionAjena;
  const quien = ajena.persona || ajena.autor || 'Otra persona';
  if (datos.alerta) guardarAlerta(datos.alerta);
  abrirHoja({
    titulo: 'Revisada desde otro dispositivo',
    cuerpo: [
      el('p', { style: 'margin:0' }, `${quien} la revisó como ${(ESTADOS[ajena.resultado] || '').toLowerCase()} a las ${horaConSegundos(Date.parse(ajena.revisadaEn || ajena.fecha))} con el motivo «${MOTIVOS[ajena.motivo] || ajena.motivo}», después de que abriste esta alerta.`),
      el('p', { style: 'margin:0;color:var(--texto-2)' }, `Tu revisión (${RESULTADOS[entrada.resultado].nombre.toLowerCase()}, ${(MOTIVOS[entrada.motivo] || '').toLowerCase()}) todavía no se aplicó. ¿Querés aplicarla igual?`),
    ],
    acciones: [
      { texto: `Mantener la de ${quien}`, tipo: 'secundario', alHacer: (h) => h.cerrar() },
      { texto: 'Aplicar la mía', tipo: 'principal', alHacer: (h) => { h.cerrar(); mandarRevision(a, { ...entrada, forzar: true }); } },
    ],
  });
}

// ---- Responsable ----

function hojaAsignar(a) {
  let elegido = responsableId(a);
  const valores = Object.fromEntries(e.personas.map((p) => [p.id, `${nombrePersona(p)}${p.vos ? ' (vos)' : ''}`]));
  abrirHoja({
    titulo: 'Asignar a',
    subtitulo: 'Le llega un aviso por Telegram y la ve en Estado.',
    cuerpo: [el('div', { class: 'opciones' }, opcionUnica(valores, () => elegido, (id) => { elegido = id; }))],
    acciones: [
      responsableId(a) ? { texto: 'Sacar la asignación', tipo: 'texto', alHacer: (h) => { h.cerrar(); asignar(a, null); } } : null,
      { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
      { texto: 'Asignar', tipo: 'principal', alHacer: (h) => { h.cerrar(); if (elegido && elegido !== responsableId(a)) asignar(a, elegido); } },
    ].filter(Boolean),
  });
}

async function asignar(a, responsable) {
  try {
    guardarAlerta(await mandar(`/alertas/${encodeURIComponent(a.id)}/asignacion`, 'PUT', { accionId: nuevoId(), responsableId: responsable }));
    avisoInferior(responsable ? 'Alerta asignada.' : 'Alerta sin asignar.', { duracion: 3000 });
  } catch (falla) {
    avisoInferior(falla.message || 'No se pudo cambiar el responsable.', { duracion: 6000 });
  }
}

