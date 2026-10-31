// La vista Resumen, igual que el Resumen de la app (RF-07): el período (7 días, 30 días o fechas), la línea de
// la red, los totales, el tiempo hasta la primera revisión y las altas revisadas en la primera hora, el gráfico
// por hora, día o semana con su frase, los tipos de ataque, los reales y falsos positivos y la precisión de cada
// modelo, con los mismos cálculos y textos. Tocar una pieza filtra todo el resumen, como en la app. En la compu
// las piezas van de a dos y al final está «Equipos con más alertas», que solo existe acá. Se comparte como en la
// app con una sola imagen, siempre en claro; el PDF es un reporte armado para la hoja A4 (reporte.js).

import { el, icono, numero, vaciar } from './util.js';
import { $, METODOS, TIPOS, ahoraServidor, e, pedir, tiposDe } from './comun.js';
import { abrirHoja, avisoInferior } from './hojas.js';
import { alternarAyuda } from './ayuda.js';
import { abrirMenu } from './menu.js';
import { bajarArchivo, pngDe } from './compartir.js';
import { reportePdf } from './reporte.js';

// Los tipos en el orden de la app: primero lo que detectó la IA y después lo que no.
const TIPOS_IA = ['fuerza_bruta', 'sin_tipificar', 'escaneo', 'ddos', 'probable_falsa_alarma'];
const TIPOS_SIN_IA = ['servicio_no_autorizado', 'volumen_inusual'];
const ORDEN = [...TIPOS_IA, ...TIPOS_SIN_IA];
const ETIQUETA_TIPO = {
  fuerza_bruta: 'Fuerza bruta (SSH, FTP)', sin_tipificar: 'Sin tipo identificado', escaneo: 'Escaneo de puertos', ddos: 'Denegación de servicio',
  probable_falsa_alarma: 'Probable falsa alarma', servicio_no_autorizado: 'Servicio no autorizado', volumen_inusual: 'Volumen inusual',
};
// En la hoja de filtros, como en la app, dos se dicen más largos.
const ETIQUETA_FILTRO = { ...ETIQUETA_TIPO, ddos: 'Denegación de servicio (DDoS)', volumen_inusual: 'Volumen inusual de conexiones' };
const SEVERIDAD_DE_FABRICA = {
  fuerza_bruta: 'alta', sin_tipificar: 'alta', escaneo: 'media', ddos: 'media', servicio_no_autorizado: 'media', volumen_inusual: 'media', probable_falsa_alarma: 'baja',
};
const DESCRIPCION_TIPO = {
  fuerza_bruta: 'Muchos intentos de contraseña contra un servicio.',
  sin_tipificar: 'No se parece a ningún ataque conocido. Puede ser uno nuevo.',
  escaneo: 'Prueba puertos para ver qué servicios hay. Suele ser el paso previo a un ataque.',
  ddos: 'Muchísimo tráfico para saturar un servicio.',
  probable_falsa_alarma: 'Raro para la IA, pero parecido a tráfico normal, como un backup.',
  servicio_no_autorizado: 'Las reglas vieron una conexión a un servicio que no está en la lista de permitidos.',
  volumen_inusual: 'Los umbrales vieron demasiadas conexiones a un mismo servicio en un minuto.',
};
// Lo que dice la frase del gráfico de las alertas de un tipo.
const DE_TIPO = {
  fuerza_bruta: (n) => `${n} de fuerza bruta`,
  sin_tipificar: (n) => `${n} sin tipo identificado`,
  escaneo: (n) => `${n} de escaneo de puertos`,
  ddos: (n) => `${n} de denegación de servicio`,
  probable_falsa_alarma: (n) => (n === 1 ? `${n} probable falsa alarma` : `${n} probables falsas alarmas`),
  servicio_no_autorizado: (n) => `${n} de servicio no autorizado`,
  volumen_inusual: (n) => `${n} de volumen inusual`,
};
const REVISION = {
  revisadas: { filtro: 'Revisadas', frase: 'revisadas' },
  sin_revisar: { filtro: 'Sin revisar', frase: 'sin revisar' },
  reales: { filtro: 'Reales', frase: 'reales' },
  falsos: { filtro: 'Falsos positivos', frase: 'falsos positivos' },
};
const MODELO = {
  ia: { filtro: 'Modelo de IA', frase: 'modelo de IA' },
  umbrales: { filtro: 'Umbrales', frase: 'umbrales estadísticos' },
  reglas: { filtro: 'Reglas', frase: 'reglas estáticas' },
};
// Los nombres de los días como los de la app (los de Android en castellano, sin punto).
const DIA_CORTO = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];
const DIA_LARGO = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const UN_MINUTO = 60e3;
const UNA_HORA = 3600e3;

// El período y los filtros: barras (por su comienzo), tipos, la revisión y el modelo.
const r = { periodo: '7d', fechas: null, barras: new Set(), tipos: new Set(), revision: null, metodo: null };

// ---- Fechas y textos ----

const dos = (n) => String(n).padStart(2, '0');
const ddMM = (ms) => { const d = new Date(ms); return `${dos(d.getDate())}/${dos(d.getMonth() + 1)}`; };
const dM = (ms) => { const d = new Date(ms); return `${d.getDate()}/${d.getMonth() + 1}`; };
const hhmm = (ms) => { const d = new Date(ms); return `${dos(d.getHours())}:${dos(d.getMinutes())}`; };
const diaCorto = (ms) => DIA_CORTO[new Date(ms).getDay()];
const diaLargo = (ms) => DIA_LARGO[new Date(ms).getDay()];
const mayuscula = (texto) => texto.charAt(0).toUpperCase() + texto.slice(1);
const minuscula = (texto) => texto.charAt(0).toLowerCase() + texto.slice(1);
const plural = (n, uno, otros) => (n === 1 ? uno : otros);
/** «73 %», como la app; null sin base. */
const porciento = (parte, total) => (total ? `${Math.round((100 * parte) / total)} %` : null);
/** «a», «a y b», «a, b y c». */
const enumerar = (lista) => (lista.length < 2 ? lista.join('') : `${lista.slice(0, -1).join(', ')} y ${lista[lista.length - 1]}`);

function medianoche(ms) {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function sumarDias(ms, dias) {
  const d = new Date(ms);
  d.setDate(d.getDate() + dias);
  return d.getTime();
}

/** «45 s», «12 min», «2 h 5 min», «1 d 3 h», como la app. */
function duracionCorta(ms) {
  const s = Math.max(0, Math.floor(ms / 1000));
  if (s < 60) return `${s} s`;
  const m = Math.floor((s + 30) / 60);
  if (m < 60) return `${m} min`;
  if (m < 1440) return m % 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${Math.floor(m / 60)} h`;
  const h = Math.floor(m / 60);
  return h % 24 ? `${Math.floor(h / 24)} d ${h % 24} h` : `${Math.floor(h / 24)} d`;
}

// ---- El período, las barras y los cálculos (los de la app) ----

/** 7 días: de las 00:00 de hace seis días hasta hoy a las 23:59; 30 días: de hace 30 días (son 31 días de
    calendario, como en la app); fechas: lo elegido, con el minuto de «hasta» incluido. */
function rango() {
  if (r.fechas) return { desde: r.fechas.desde, hasta: r.fechas.hasta, fin: r.fechas.hasta + UN_MINUTO, tipo: 'fechas' };
  const hoy = medianoche(ahoraServidor());
  const fin = sumarDias(hoy, 1);
  return { desde: sumarDias(hoy, r.periodo === '7d' ? -6 : -30), hasta: fin - UN_MINUTO, fin, tipo: r.periodo };
}

const diasEnteros = ({ desde, fin }) => desde === medianoche(desde) && fin === medianoche(fin);

function diasEntre(desde, hasta) {
  let n = 0;
  for (let d = medianoche(desde); d <= hasta; d = sumarDias(d, 1)) n += 1;
  return n;
}

/** Por hora si el rango es de un día o menos; por día hasta 14 días; si no, por semana, con la primera
    barra que se queda con los días que sobran. */
function barrasDe({ desde, hasta, fin }) {
  if (fin - desde <= 24 * UNA_HORA) {
    const inicio = new Date(desde);
    inicio.setMinutes(0, 0, 0);
    const barras = [];
    for (let t = inicio.getTime(); t < fin; t += UNA_HORA) barras.push({ inicio: t, fin: t + UNA_HORA });
    return { escala: 'hora', barras: barras.map((b, i) => ({ ...b, etiqueta: barras.length > 12 && i % 3 ? '' : String(new Date(b.inicio).getHours()) })) };
  }
  const dias = diasEntre(desde, hasta);
  if (dias <= 14) {
    const hoy = medianoche(ahoraServidor());
    const barras = [];
    for (let d = medianoche(desde); d <= hasta; d = sumarDias(d, 1)) barras.push({ inicio: d, fin: sumarDias(d, 1) });
    return { escala: 'dia', barras: barras.map((b) => ({ ...b, hoy: b.inicio === hoy, etiqueta: barras.length <= 7 ? diaCorto(b.inicio) : String(new Date(b.inicio).getDate()) })) };
  }
  const ultimo = medianoche(hasta);
  const semanas = Math.floor(dias / 7);
  const barras = [{ inicio: medianoche(desde), fin: sumarDias(ultimo, -7 * (semanas - 1) + 1) }];
  for (let k = semanas - 2; k >= 0; k -= 1) barras.push({ inicio: sumarDias(ultimo, -7 * k - 6), fin: sumarDias(ultimo, -7 * k + 1) });
  return { escala: 'semana', barras: barras.map((b) => ({ ...b, etiqueta: dM(b.inicio) })) };
}

/** Cómo se nombra una barra: en la frase con un solo filtro, con varios, en su chip y con la fecha entera. */
function nombreBarra(escala, b, forma) {
  if (escala === 'semana') return `semana del ${dM(b.inicio)}`;
  if (escala === 'hora') {
    const h = new Date(b.inicio).getHours();
    return { uno: `las ${h} h`, varios: `las ${h} h`, chip: `${h} h`, fecha: `las ${h} h del ${diaLargo(b.inicio)} ${ddMM(b.inicio)}` }[forma];
  }
  return { uno: `${mayuscula(diaLargo(b.inicio))} ${ddMM(b.inicio)}`, varios: diaLargo(b.inicio), chip: `${diaCorto(b.inicio)} ${ddMM(b.inicio)}`, fecha: `${diaLargo(b.inicio)} ${ddMM(b.inicio)}` }[forma];
}

/** La severidad de cada tipo en esta red (Ajustes → Severidades). */
const severidadDe = (tipo) => (e.severidades && e.severidades[tipo]) || SEVERIDAD_DE_FABRICA[tipo];
const altasConfiguradas = () => new Set(ORDEN.filter((t) => severidadDe(t) === 'alta'));
const sonLasAltas = () => {
  const altas = altasConfiguradas();
  return altas.size > 0 && r.tipos.size === altas.size && [...altas].every((t) => r.tipos.has(t));
};
const cantidadFiltros = () => r.barras.size + r.tipos.size + (r.revision ? 1 : 0) + (r.metodo ? 1 : 0);

/** La primera revisión, con la hora del dispositivo que la hizo (decisión 21), como la app. */
function primeraRevision(a) {
  const tiempos = (a.historial || []).map((rev) => Date.parse(rev.revisadaEn || rev.fecha)).filter((t) => !Number.isNaN(t));
  return tiempos.length ? Math.min(...tiempos) : null;
}

const enRango = (a, desde, fin) => { const t = Date.parse(a.inicio); return t >= desde && t < fin; };

function pasaFiltro(a, filtro, barras) {
  if (filtro.barras.size && !barras.some((b) => filtro.barras.has(b.inicio) && enRango(a, b.inicio, b.fin))) return false;
  if (filtro.tipos.size && !tiposDe(a).some((t) => filtro.tipos.has(t))) return false;
  const estado = a.estadoRevision;
  if (filtro.revision === 'revisadas' && estado === 'sin_revisar') return false;
  if (filtro.revision === 'sin_revisar' && estado !== 'sin_revisar') return false;
  if (filtro.revision === 'reales' && estado !== 'ataque_real') return false;
  if (filtro.revision === 'falsos' && estado !== 'falso_positivo') return false;
  if (filtro.metodo && !a.veredictos[filtro.metodo]) return false;
  return true;
}

/** Todo lo que muestra el Resumen, en un objeto: lo usan la pantalla y la imagen. Cada cifra sale de las
    alertas del período que pasan los filtros, por la hora en que empezaron. */
function calcular() {
  const rg = rango();
  const { escala, barras } = barrasDe(rg);
  const ahora = ahoraServidor();
  const delPeriodo = [...e.alertas.values()].filter((a) => enRango(a, rg.desde, rg.fin));
  const lista = delPeriodo.filter((a) => pasaFiltro(a, r, barras));
  const revisadas = lista.filter((a) => a.estadoRevision !== 'sin_revisar');
  const tiempos = revisadas.map((a) => { const p = primeraRevision(a); return p === null ? null : p - Date.parse(a.creadaEn); })
    .filter((t) => t !== null && t >= 0).sort((x, y) => x - y);
  const medio = Math.floor(tiempos.length / 2);
  const mediana = !tiempos.length ? null : tiempos.length % 2 ? tiempos[medio] : (tiempos[medio - 1] + tiempos[medio]) / 2;
  const enHora = (a) => { const p = primeraRevision(a); return p !== null && a.estadoRevision !== 'sin_revisar' && p - Date.parse(a.creadaEn) <= UNA_HORA; };
  const altas = lista.filter((a) => a.severidad === 'alta');
  const conteos = barras.map((b) => lista.filter((a) => enRango(a, b.inicio, b.fin)).length);
  // La barra con más alertas (en un empate, la más nueva) y, en ella, el tipo que más se repite.
  let destacado = null;
  let mejor = -1;
  conteos.forEach((n, i) => { if (n > 0 && (mejor < 0 || n >= conteos[mejor])) mejor = i; });
  if (mejor >= 0) {
    const b = barras[mejor];
    const grupos = new Map();
    for (const a of lista.filter((x) => enRango(x, b.inicio, b.fin))) for (const t of tiposDe(a)) grupos.set(t, [...(grupos.get(t) || []), a]);
    const tipo = ORDEN.filter((t) => grupos.has(t)).reduce((elegido, t) => (!elegido || grupos.get(t).length > grupos.get(elegido).length ? t : elegido), null);
    const delTipo = tipo ? grupos.get(tipo) : [];
    destacado = { barra: b, n: conteos[mejor], tipo, delTipo: delTipo.length, mismoDestino: delTipo.length >= 2 && delTipo.every((a) => a.destinoIp === delTipo[0].destinoIp) };
  }
  const equipos = new Map();
  for (const a of lista) {
    const actual = equipos.get(a.destinoIp) || { nombre: a.destinoNombre || a.destinoIp, n: 0 };
    actual.n += 1;
    equipos.set(a.destinoIp, actual);
  }
  return {
    rg, escala, barras, conteos, delPeriodo, lista, destacado,
    total: lista.length,
    revisadas: revisadas.length,
    sinRevisar: lista.length - revisadas.length,
    reales: lista.filter((a) => a.estadoRevision === 'ataque_real').length,
    falsos: lista.filter((a) => a.estadoRevision === 'falso_positivo').length,
    mediana,
    altasParte: altas.filter(enHora).length,
    altasTotal: altas.filter((a) => enHora(a) || ahora - Date.parse(a.creadaEn) > UNA_HORA).length,
    porTipo: new Map(ORDEN.map((t) => [t, lista.filter((a) => tiposDe(a).includes(t)).length])),
    precision: METODOS.map((m) => {
      const marcadas = revisadas.filter((a) => a.veredictos[m.id]);
      return { m, parte: marcadas.filter((a) => a.estadoRevision === 'ataque_real').length, total: marcadas.length };
    }),
    equipos: [...equipos.values()].sort((x, y) => y.n - x.n).slice(0, 8),
  };
}

// ---- Los textos que dependen de los datos ----

/** «del 21/09 al 27/09», «viernes 25/09», «viernes 25/09 · de 00:00 a 05:59» o con día y hora, como la app. */
function textoRango(rg) {
  const unDia = medianoche(rg.desde) === medianoche(rg.hasta);
  const enteros = diasEnteros(rg);
  if (rg.tipo !== 'fechas' || (enteros && !unDia)) return `del ${ddMM(rg.desde)} al ${ddMM(rg.hasta)}`;
  if (unDia && enteros) return `${diaLargo(rg.desde)} ${ddMM(rg.desde)}`;
  if (unDia) return `${diaLargo(rg.desde)} ${ddMM(rg.desde)} · de ${hhmm(rg.desde)} a ${hhmm(rg.hasta)}`;
  return `del ${diaCorto(rg.desde)} ${ddMM(rg.desde)} ${hhmm(rg.desde)} al ${diaCorto(rg.hasta)} ${ddMM(rg.hasta)} ${hhmm(rg.hasta)}`;
}

function lineaDelResumen(x) {
  const partes = [];
  if (e.acceso && e.acceso.red) partes.push(e.acceso.red.nombre);
  partes.push(textoRango(x.rg));
  partes.push(e.ultimaRespuesta ? `actualizado ${hhmm(e.ultimaRespuesta)}` : 'sin sincronizar');
  return partes.join(' · ');
}

/** Lo elegido en cada clase de filtro, en el orden de la app: cuándo, tipo, revisión y modelo. */
function gruposDeFiltros(x, formaBarra) {
  const grupos = [];
  const barras = x.barras.filter((b) => r.barras.has(b.inicio)).map((b) => nombreBarra(x.escala, b, formaBarra));
  if (barras.length) grupos.push(enumerar(barras));
  if (sonLasAltas()) grupos.push('las altas');
  else if (r.tipos.size) grupos.push(enumerar(ORDEN.filter((t) => r.tipos.has(t)).map((t) => minuscula(TIPOS[t]))));
  if (r.revision) grupos.push(REVISION[r.revision].frase);
  if (r.metodo) grupos.push(MODELO[r.metodo].frase);
  return grupos;
}

/** La frase de abajo del gráfico (fraseDelGrafico de la app). Con filtros dice qué se está mostrando; sin
    filtros, cuenta la barra con más alertas. En la imagen no dice «Tocá de nuevo». */
function fraseDelGrafico(x, enImagen = false) {
  const nombrados = r.barras.size + (sonLasAltas() ? 1 : r.tipos.size) + (r.revision ? 1 : 0) + (r.metodo ? 1 : 0);
  const alertas = `${x.total} ${plural(x.total, 'alerta', 'alertas')}`;
  if (nombrados === 1) {
    const [nombre] = gruposDeFiltros(x, 'uno');
    return enImagen ? `Mostrando ${nombre}: ${alertas}.` : `Mostrando ${nombre}: ${alertas}. Tocá de nuevo para sacarlo.`;
  }
  if (nombrados > 1 && nombrados <= 4) return `Mostrando ${alertas}: ${gruposDeFiltros(x, 'varios').join(' · ')}.`;
  if (nombrados > 4) return `Mostrando ${alertas} con ${cantidadFiltros()} filtros.`;
  const d = x.destacado;
  if (!d) return null;
  const delTipo = d.tipo ? DE_TIPO[d.tipo](d.delTipo) : '';
  if (x.escala === 'hora') return `A las ${new Date(d.barra.inicio).getHours()} h hubo más alertas: ${d.n}, con ${delTipo}.`;
  if (x.escala === 'semana') return `La semana del ${dM(d.barra.inicio)} fue la de más actividad: ${d.n} ${plural(d.n, 'alerta', 'alertas')}.`;
  const dia = x.barras.length > 7 ? `${diaLargo(d.barra.inicio)} ${ddMM(d.barra.inicio)}` : diaLargo(d.barra.inicio);
  return `El ${dia} concentra ${d.n} ${plural(d.n, 'alerta', 'alertas')}: ${delTipo}${d.mismoDestino ? ' sobre el mismo servidor' : ''}.`;
}

const tituloGrafico = (escala) => ({ hora: 'Alertas por hora', dia: 'Alertas por día', semana: 'Alertas por semana' })[escala];
const tituloResumen = () => (r.fechas ? 'Resumen' : r.periodo === '7d' ? 'Resumen semanal' : 'Resumen mensual');

// ---- Tocar para filtrar, como en la app ----

function alternar(conjunto, valor) {
  if (conjunto.has(valor)) conjunto.delete(valor); else conjunto.add(valor);
  pintarResumen();
}

function elegirRevision(valor) {
  r.revision = r.revision === valor ? null : valor;
  pintarResumen();
}

function elegirModelo(valor) {
  r.metodo = r.metodo === valor ? null : valor;
  pintarResumen();
}

/** Las altas: si ya están todas las de la red entre los tipos, se sacan; si no, se suman. */
function tocarAltas() {
  const altas = altasConfiguradas();
  const todas = altas.size > 0 && [...altas].every((t) => r.tipos.has(t));
  for (const t of altas) { if (todas) r.tipos.delete(t); else r.tipos.add(t); }
  pintarResumen();
}

function limpiarFiltros() {
  r.barras.clear();
  r.tipos.clear();
  r.revision = null;
  r.metodo = null;
  pintarResumen();
}

/** Cambiar el período o las fechas solo saca las barras elegidas: las otras barras ya no existen. */
function cambiarPeriodo(periodo) {
  r.periodo = periodo;
  r.fechas = null;
  r.barras.clear();
  pintarResumen();
}

// ---- La pantalla ----

function tarjeta(titulo, ayuda, ...contenido) {
  return el('section', { class: 'res-tarjeta' },
    el('h3', { class: 'res-titulo' }, titulo, ayuda ? el('button', {
      type: 'button', class: 'boton-ayuda', 'aria-haspopup': 'dialog', 'aria-expanded': 'false', 'aria-controls': 'ayuda-flotante',
      'aria-label': ayuda.titulo, title: ayuda.titulo, onclick: (ev) => alternarAyuda(ev.currentTarget, ayuda.titulo, ayuda.contenido()),
    }, '?') : null),
    ...contenido);
}

function botonTocable(clase, elegido, alTocar, ...hijos) {
  return el('button', { type: 'button', class: clase, 'aria-pressed': String(Boolean(elegido)), onclick: alTocar }, ...hijos);
}

/** Los cinco indicadores de arriba, iguales, como una fila de paneles «Stat» de Grafana: el nombre, el número y
    un detalle, sin bloques de color. Tocarlos filtra igual que en la app. */
function indicadores(x) {
  const altas = porciento(x.altasParte, x.altasTotal);
  const indicador = (elegido, alTocar, nombre, valor, detalle, punto = null) => botonTocable('res-indicador', elegido, alTocar,
    el('span', { class: 'res-indicador-nombre' }, punto, nombre),
    el('strong', {}, valor),
    el('span', { class: 'res-indicador-detalle' }, detalle));
  return el('div', { class: 'res-indicadores res-ancho' },
    indicador(false, limpiarFiltros, 'Alertas', numero(x.total), r.fechas ? 'en estas fechas' : r.periodo === '7d' ? 'en 7 días' : 'en 30 días'),
    indicador(r.revision === 'revisadas', () => elegirRevision('revisadas'), 'Revisadas', numero(x.revisadas), `${porciento(x.revisadas, x.total) || '0 %'} del total`),
    indicador(r.revision === 'sin_revisar', () => elegirRevision('sin_revisar'), 'Sin revisar', numero(x.sinRevisar), x.sinRevisar ? 'esperan revisión' : 'nada pendiente',
      x.sinRevisar ? el('i', { class: 'punto', style: 'background:var(--media)' }) : null),
    indicador(r.revision === 'revisadas', () => elegirRevision('revisadas'), 'Primera revisión', x.mediana === null ? '—' : duracionCorta(x.mediana),
      x.mediana === null ? 'sin revisiones todavía' : 'mediana del período'),
    indicador(sonLasAltas(), tocarAltas, 'Altas revisadas a tiempo', altas || '—',
      x.altasTotal ? `${x.altasParte} de ${x.altasTotal} en la primera hora` : 'ninguna alta con más de 1 h'));
}

/** Las barras llenan el alto de su tarjeta, que es el de la de al lado. El número va solo arriba de las barras
    con alertas: un cero escrito en cada día vacío no se lee. */
function grafico(x) {
  const maximo = Math.max(1, ...x.conteos);
  const hayElegidas = r.barras.size > 0;
  const muchas = x.barras.length > 12;
  const columnas = x.barras.map((b, i) => {
    const n = x.conteos[i];
    const elegida = r.barras.has(b.inicio);
    return botonTocable(`res-columna${elegida ? ' elegida' : ''}${hayElegidas && !elegida ? ' apagada' : ''}`, elegida, () => alternar(r.barras, b.inicio),
      el('span', { class: 'res-columna-pista' },
        n ? el('span', { class: 'res-columna-n' }, String(n)) : null,
        el('span', { class: `res-barra${b.hoy ? ' hoy' : ''}${n ? '' : ' cero'}`, style: `height:${n ? Math.max(3, (85 * n) / maximo) : 0}%` })),
      el('span', { class: 'res-columna-etiqueta' }, b.etiqueta || ' '));
  });
  const frase = fraseDelGrafico(x);
  return tarjeta(tituloGrafico(x.escala), null,
    el('div', { class: `res-barras${muchas ? ' muchas' : ''}` }, columnas),
    frase ? el('p', { class: 'res-frase' }, frase) : null);
}

/** Una fila con su barra: el nombre, la pista y el número. Sin `alTocar` no se toca (los equipos). Con
    `marcaCero`, un cero deja una rayita al principio de la pista, como en la app. */
function filaDeBarra({ clase, elegida = false, alTocar = null, punto = null, etiqueta, valor, lleno, color = null, marcaCero = false }) {
  const ancho = valor ? `${lleno}%` : marcaCero ? '2px' : '0';
  const hijos = [
    el('span', { class: 'res-fila-nombre' }, punto, el('span', {}, etiqueta)),
    el('span', { class: 'res-pista' }, el('i', { style: `width:${ancho};${color ? `background:${color}` : ''}` })),
    el('span', { class: 'res-fila-valor' }, numero(valor)),
  ];
  const clases = `res-fila ${clase}${elegida ? ' elegida' : ''}${valor ? '' : ' cero'}`;
  return alTocar ? botonTocable(clases, elegida, alTocar, ...hijos) : el('div', { class: clases }, ...hijos);
}

/** Los tipos con alertas, cada uno con su barra; los que no tuvieron van juntos en una línea, debajo de su grupo. */
function tipos(x) {
  const maximo = Math.max(1, ...x.porTipo.values());
  const todoCero = [...x.porTipo.values()].every((n) => n === 0);
  const bloque = (titulo, lista) => {
    const con = lista.filter((t) => x.porTipo.get(t));
    const sin = lista.filter((t) => !x.porTipo.get(t));
    if (!todoCero && !con.length) return null;
    return el('div', { class: 'res-bloque' }, el('p', { class: 'res-bloque-titulo' }, titulo),
      con.map((t) => filaDeBarra({
        clase: 'tipo', elegida: r.tipos.has(t), alTocar: () => alternar(r.tipos, t), punto: el('i', { class: 'punto', style: `background:var(--${severidadDe(t)})` }),
        etiqueta: ETIQUETA_TIPO[t], valor: x.porTipo.get(t), lleno: (95 * x.porTipo.get(t)) / maximo,
      })),
      sin.length ? el('p', { class: 'res-sin-alertas' }, `Sin alertas: ${enumerar(sin.map((t) => minuscula(ETIQUETA_TIPO[t])))}.`) : null);
  };
  return tarjeta('Por tipo de ataque', { titulo: 'Qué es cada tipo', contenido: ayudaTipos },
    bloque('Lo que detectó la IA', TIPOS_IA), bloque('Lo que la IA no detectó', TIPOS_SIN_IA));
}

/** Reales y falsos positivos en una sola barra partida, con su leyenda: tocar una parte filtra, como en la app. */
function realesYFalsos(x) {
  const parte = (clase, n, color, uno, otros, revision) => botonTocable(`res-leyenda-item ${clase}`, r.revision === revision, () => elegirRevision(revision),
    el('i', { class: 'punto', style: `background:${color}` }), el('strong', {}, numero(n)), ` ${plural(n, uno, otros)}`);
  return tarjeta('Reales y falsos positivos', null,
    el('div', { class: 'res-partida', role: 'img', 'aria-label': `${x.reales} reales y ${x.falsos} falsos positivos` },
      x.reales ? el('i', { style: `flex:${x.reales};background:var(--verde)` }) : null,
      x.falsos ? el('i', { style: `flex:${x.falsos};background:var(--media)` }) : null),
    el('div', { class: 'res-partida-leyenda' },
      parte('reales', x.reales, 'var(--verde)', 'real', 'reales', 'reales'),
      parte('falsos', x.falsos, 'var(--media)', 'falso positivo', 'falsos positivos', 'falsos')),
    el('p', { class: 'res-nota' }, x.revisadas
      ? `De las ${numero(x.revisadas)} revisadas. Un falso positivo con motivo sirve para ajustar la detección.`
      : 'Todavía no hay alertas revisadas en el período.'));
}

function precision(x) {
  return tarjeta('Precisión de cada modelo', { titulo: 'Cómo se lee la precisión', contenido: () => ayudaPrecision(x) },
    botonTocable(`res-cobertura${r.revision === 'revisadas' ? ' elegida' : ''}`, r.revision === 'revisadas', () => elegirRevision('revisadas'),
      el('span', {}, 'Revisadas'), el('strong', {}, `${porciento(x.revisadas, x.total) || '0 %'} · ${x.revisadas} de ${x.total}`)),
    el('div', { class: 'res-filas' }, x.precision.map((p) => botonTocable(`res-precision${r.metodo === p.m.id ? ' elegida' : ''}`, r.metodo === p.m.id, () => elegirModelo(p.m.id),
      el('span', { class: 'res-fila-nombre' }, p.m.resumen),
      el('span', { class: 'res-pista fina' }, el('i', { class: p.total < 30 ? 'clara' : null, style: `width:${p.parte ? `${(100 * p.parte) / p.total}%` : '2px'}` })),
      el('strong', {}, porciento(p.parte, p.total) || '—'),
      el('span', { class: 'res-de' }, `${p.parte} de ${p.total}`)))));
}

function equipos(x) {
  const maximo = Math.max(1, ...x.equipos.map((q) => q.n));
  return tarjeta('Equipos con más alertas', null,
    el('p', { class: 'res-nota arriba' }, 'Como destino. No se incluye al compartir.'),
    x.equipos.length ? el('div', { class: 'res-filas' }, x.equipos.map((q) => filaDeBarra({ clase: 'equipo', etiqueta: q.nombre, valor: q.n, lleno: (95 * q.n) / maximo })))
      : el('p', { class: 'res-nota' }, 'Sin alertas en el período.'));
}

function vacio() {
  const textos = {
    '7d': ['Sin alertas en los últimos 7 días', 'El sensor siguió activo toda la semana. Podés compartir el resumen igual: muestra que la red estuvo tranquila.'],
    '30d': ['Sin alertas en los últimos 30 días', 'El sensor siguió activo todo el mes. Podés compartir el resumen igual: muestra que la red estuvo tranquila.'],
    fechas: ['Sin alertas en estas fechas', 'El sensor siguió activo. Podés compartir el resumen igual: muestra que la red estuvo tranquila.'],
  }[r.fechas ? 'fechas' : r.periodo];
  return el('div', { class: 'res-vacio' }, el('span', { class: 'res-vacio-circulo' }, icono('check')), el('h3', {}, textos[0]), el('p', {}, textos[1]));
}

function textoChipFechas() {
  if (!r.fechas) return 'Fechas';
  const { desde, hasta } = r.fechas;
  return medianoche(desde) === medianoche(hasta) ? `${diaCorto(desde)} ${ddMM(desde)}` : `${ddMM(desde)} al ${ddMM(hasta)}`;
}

function pintarBarra() {
  const chip = (texto, elegido, alTocar, delante, detras) => el('button', { type: 'button', class: 'res-chip', 'aria-pressed': String(Boolean(elegido)), onclick: alTocar },
    delante ? icono(delante) : null, texto, detras ? icono(detras) : null);
  const cantidad = cantidadFiltros();
  const conHora = r.fechas && !diasEnteros({ desde: r.fechas.desde, fin: r.fechas.hasta + UN_MINUTO });
  vaciar($('resumen-barra'),
    chip('7 días', !r.fechas && r.periodo === '7d', () => cambiarPeriodo('7d')),
    chip('30 días', !r.fechas && r.periodo === '30d', () => cambiarPeriodo('30d')),
    chip(textoChipFechas(), Boolean(r.fechas), hojaFechas, 'calendario', conHora ? 'reloj' : null),
    chip(cantidad ? `Filtros (${cantidad})` : 'Filtros', cantidad > 0, hojaFiltros, 'filtro'),
    el('button', { type: 'button', class: 'boton-principal res-compartir', 'aria-haspopup': 'menu', 'aria-expanded': 'false', onclick: (ev) => menuCompartir(ev.currentTarget) },
      icono('compartir'), 'Compartir resumen'));
}

/** Las severidades por tipo se piden una vez, para el punto de color de cada tipo. */
let pidiendoSeveridades = false;
function asegurarSeveridades() {
  if (pidiendoSeveridades || (e.severidades && Object.keys(e.severidades).length) || !e.acceso) return;
  pidiendoSeveridades = true;
  pedir('/severidades').then((s) => { e.severidades = s; if (e.vista === 'resumen') pintarResumen(); }).catch(() => {}).finally(() => { pidiendoSeveridades = false; });
}

export function pintarResumen() {
  asegurarSeveridades();
  const x = calcular();
  pintarBarra();
  $('resumen-linea').textContent = lineaDelResumen(x);
  if (!x.delPeriodo.length) {
    vaciar($('resumen-cuerpo'), vacio());
    return;
  }
  vaciar($('resumen-cuerpo'), el('div', { class: 'res-grilla' },
    indicadores(x), grafico(x), tipos(x), realesYFalsos(x), precision(x),
    el('div', { class: 'res-ancho' }, equipos(x))));
}

// ---- Las hojas: fechas, filtros y las dos ayudas ----

function aLocal(ms) {
  const d = new Date(ms);
  return `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}T${dos(d.getHours())}:${dos(d.getMinutes())}`;
}

function hojaFechas() {
  const rg = rango();
  const campoDesde = el('input', { type: 'datetime-local', value: aLocal(rg.desde) });
  const campoHasta = el('input', { type: 'datetime-local', value: aLocal(rg.hasta) });
  const error = el('div', { class: 'error-hoja', hidden: true });
  let hoja = null;
  // Cuántas alertas habría: las de ese rango con los filtros de ahora, menos las barras.
  const cuantas = (desde, hasta) => {
    const filtro = { ...r, barras: new Set() };
    return [...e.alertas.values()].filter((a) => enRango(a, desde, hasta + UN_MINUTO) && pasaFiltro(a, filtro, [])).length;
  };
  const pintar = () => {
    const d = Date.parse(campoDesde.value);
    const a = Date.parse(campoHasta.value);
    const valido = !Number.isNaN(d) && !Number.isNaN(a) && a >= d;
    error.hidden = valido || Number.isNaN(d) || Number.isNaN(a);
    error.textContent = 'La hora de "Hasta" tiene que ser después de la de "Desde".';
    const n = valido ? cuantas(d, a) : 0;
    hoja.pintar({
      titulo: 'Elegí las fechas',
      cuerpo: [el('label', { class: 'campo' }, el('span', {}, 'Desde'), campoDesde), el('label', { class: 'campo' }, el('span', {}, 'Hasta'), campoHasta), error],
      acciones: [
        { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
        { texto: `Mostrar ${n} ${plural(n, 'alerta', 'alertas')}`, tipo: 'principal', deshabilitado: !valido, alHacer: (h) => aplicar(h, d, a) },
      ],
    });
  };
  const aplicar = (h, d, a) => {
    h.cerrar();
    r.barras.clear();
    // Si las fechas son justo las de un atajo, queda elegido el atajo, como en la app.
    const hoy = medianoche(ahoraServidor());
    const finDeHoy = sumarDias(hoy, 1) - UN_MINUTO;
    const atajo = a === finDeHoy && (d === sumarDias(hoy, -6) ? '7d' : d === sumarDias(hoy, -30) ? '30d' : null);
    if (atajo) { r.periodo = atajo; r.fechas = null; } else r.fechas = { desde: d, hasta: a };
    pintarResumen();
  };
  hoja = abrirHoja({ titulo: 'Elegí las fechas', cuerpo: [] });
  for (const campo of [campoDesde, campoHasta]) campo.addEventListener('change', pintar);
  pintar();
}

function hojaFiltros() {
  const x = calcular();
  const borrador = { barras: new Set(r.barras), tipos: new Set(r.tipos), revision: r.revision, metodo: r.metodo };
  let hoja = null;
  const unico = (valores, clave) => Object.entries(valores).map(([id, nombre]) => el('button', {
    type: 'button', class: 'chip-filtro', 'aria-pressed': String((borrador[clave] || 'todas') === id),
    onclick: () => { borrador[clave] = id === 'todas' ? null : id; pintar(); },
  }, nombre));
  const pintar = () => {
    const n = x.delPeriodo.filter((a) => pasaFiltro(a, borrador, x.barras)).length;
    const filaTipo = (t) => el('label', { class: 'res-check' },
      el('input', { type: 'checkbox', checked: borrador.tipos.has(t) || null, onchange: (ev) => { if (ev.target.checked) borrador.tipos.add(t); else borrador.tipos.delete(t); pintar(); } }),
      el('i', { class: 'punto', style: `background:var(--${severidadDe(t)})` }), ETIQUETA_FILTRO[t]);
    hoja.pintar({
      titulo: 'Filtrar el resumen',
      cuerpo: [
        el('div', { class: 'grupo-opciones' }, el('p', {}, 'Cuándo · podés elegir varios'),
          el('div', { class: 'opciones' }, x.barras.map((b) => el('button', {
            type: 'button', class: 'chip-filtro', 'aria-pressed': String(borrador.barras.has(b.inicio)),
            onclick: () => { if (borrador.barras.has(b.inicio)) borrador.barras.delete(b.inicio); else borrador.barras.add(b.inicio); pintar(); },
          }, nombreBarra(x.escala, b, 'chip'))))),
        el('div', { class: 'grupo-opciones' }, el('p', {}, 'Tipo de ataque · podés elegir varios'),
          el('p', { class: 'res-bloque-titulo' }, 'LO QUE DETECTÓ LA IA'), TIPOS_IA.map(filaTipo),
          el('p', { class: 'res-bloque-titulo' }, 'LO QUE LA IA NO DETECTÓ'), TIPOS_SIN_IA.map(filaTipo)),
        el('div', { class: 'grupo-opciones' }, el('p', {}, 'Revisión'),
          el('div', { class: 'opciones' }, unico({ todas: 'Todas', ...Object.fromEntries(Object.entries(REVISION).map(([id, v]) => [id, v.filtro])) }, 'revision'))),
        el('div', { class: 'grupo-opciones' }, el('p', {}, 'Modelo'),
          el('div', { class: 'opciones' }, unico({ todas: 'Todos', ...Object.fromEntries(Object.entries(MODELO).map(([id, v]) => [id, v.filtro])) }, 'metodo'))),
      ],
      acciones: [
        { texto: 'Limpiar', tipo: 'secundario', alHacer: () => { borrador.barras.clear(); borrador.tipos.clear(); borrador.revision = null; borrador.metodo = null; pintar(); } },
        {
          texto: `Mostrar ${n} ${plural(n, 'alerta', 'alertas')}`, tipo: 'principal', alHacer: (h) => {
            Object.assign(r, { barras: borrador.barras, tipos: borrador.tipos, revision: borrador.revision, metodo: borrador.metodo });
            h.cerrar();
            pintarResumen();
          },
        },
      ],
    });
  };
  hoja = abrirHoja({ titulo: 'Filtrar el resumen', cuerpo: [] });
  pintar();
}

function ayudaTipos() {
  const fila = (t) => el('p', { class: 'res-ayuda-tipo' }, el('i', { class: 'punto', style: `background:var(--${severidadDe(t)})` }),
    el('span', {}, el('b', {}, `${TIPOS[t]}.`), ` ${DESCRIPCION_TIPO[t]}`));
  return [
    el('p', { class: 'res-bloque-titulo' }, 'Lo que detectó la IA'),
    el('p', { class: 'res-ayuda-texto' }, 'La IA vio algo raro y otro modelo le pone el tipo de ataque que parece.'),
    TIPOS_IA.map(fila),
    el('hr', { class: 'res-divisor' }),
    el('p', { class: 'res-bloque-titulo' }, 'Lo que la IA no detectó'),
    el('p', { class: 'res-ayuda-texto' }, 'Lo marcaron los otros dos detectores de Zonda.'),
    TIPOS_SIN_IA.map(fila),
    el('hr', { class: 'res-divisor' }),
    el('p', { class: 'res-ayuda-texto' }, 'El punto de color es la severidad que elegiste para cada tipo. Se cambia en Ajustes → Severidades.'),
  ];
}

/** Lo que dice el recuadro azul de la ayuda de la precisión: la primera regla que corresponde. */
function recuadroPrecision(x) {
  if (!cantidadFiltros()) return `Sin filtro: la precisión cuenta todas las alertas revisadas ${r.fechas ? 'de las fechas elegidas' : r.periodo === '7d' ? 'de estos 7 días' : 'de estos 30 días'}.`;
  if (r.metodo) {
    return {
      ia: 'Estás viendo el modelo de IA: cada fila cuenta las alertas que marcó la IA junto con ese modelo.',
      umbrales: 'Estás viendo los umbrales estadísticos: cada fila cuenta las alertas que marcaron los umbrales junto con ese modelo.',
      reglas: 'Estás viendo las reglas estáticas: cada fila cuenta las alertas que marcaron las reglas junto con ese modelo.',
    }[r.metodo];
  }
  if (sonLasAltas() && !r.barras.size && !r.revision) return 'Estás viendo las altas: la precisión cuenta solo las alertas altas revisadas.';
  if (cantidadFiltros() > 1) return 'Estás viendo varios filtros juntos: la precisión cuenta solo las alertas revisadas que pasan todos.';
  if (r.barras.size) {
    const b = x.barras.find((barra) => r.barras.has(barra.inicio));
    if (x.escala === 'hora') return `Estás viendo ${nombreBarra('hora', b, 'fecha')}: la precisión cuenta solo las alertas revisadas de esa hora.`;
    if (x.escala === 'dia') return `Estás viendo el ${nombreBarra('dia', b, 'fecha')}: la precisión cuenta solo las alertas revisadas de ese día.`;
    return `Estás viendo la ${nombreBarra('semana', b, 'fecha')}: la precisión cuenta solo las alertas revisadas de esa semana.`;
  }
  if (r.tipos.size) return `Estás viendo ${minuscula(TIPOS[[...r.tipos][0]])}: la precisión cuenta solo las alertas revisadas de ese tipo.`;
  return {
    revisadas: 'Estás viendo las revisadas: la precisión cuenta las mismas alertas que sin filtro.',
    sin_revisar: 'Estás viendo las sin revisar: todavía no tienen revisión, así que no hay precisión para calcular.',
    reales: 'Estás viendo los ataques reales: todas son reales, así que cada modelo da 100 %.',
    falsos: 'Estás viendo los falsos positivos: ninguna es real, así que cada modelo da 0 %.',
  }[r.revision];
}

function ayudaPrecision(x) {
  const ejemplo = x.precision.find((p) => p.total >= 2);
  const frase = !ejemplo ? '' : ` ${{
    ia: `Por ejemplo, la IA tiene ${ejemplo.parte} de ${ejemplo.total}: marcó ${ejemplo.total} alertas que ya revisaste y ${ejemplo.parte} ${plural(ejemplo.parte, 'era un ataque', 'eran ataques')}.`,
    umbrales: `Por ejemplo, los umbrales tienen ${ejemplo.parte} de ${ejemplo.total}: marcaron ${ejemplo.total} alertas que ya revisaste y ${ejemplo.parte} ${plural(ejemplo.parte, 'era un ataque', 'eran ataques')}.`,
    reglas: `Por ejemplo, las reglas tienen ${ejemplo.parte} de ${ejemplo.total}: marcaron ${ejemplo.total} alertas que ya revisaste y ${ejemplo.parte} ${plural(ejemplo.parte, 'era un ataque', 'eran ataques')}.`,
  }[ejemplo.m.id]}`;
  const bloque = (titulo, texto) => el('div', { class: 'res-ayuda-bloque' }, el('h4', {}, titulo), el('p', {}, texto));
  return [
    bloque('Precisión', `De las alertas que marcó un modelo y ya revisaste, cuántas eran ataques reales.${frase}`),
    bloque('Cobertura de revisión', 'Cuántas alertas del período ya revisaste.'),
    bloque('Los tres modelos', 'La IA compara el tráfico con lo normal de tu red. Los umbrales cuentan conexiones a un mismo servicio en 60 segundos. Las reglas miran si ese servicio está autorizado.'),
    bloque('¿Por qué 30 revisiones?', 'Con pocas, un par de casos mueven mucho el porcentaje. Las barras claras tienen menos de 30 revisiones: todavía no se pueden comparar.'),
    el('p', { class: 'res-recuadro' }, recuadroPrecision(x)),
    el('p', { class: 'res-ayuda-texto' }, 'Se calcula con las alertas que tiene el servidor de esta red.'),
  ];
}

// ---- Compartir: la imagen de la app, siempre en claro, y el PDF, que es el reporte A4 de reporte.js ----

// Los colores del tema claro de la app.
const C = {
  verde: '#0F6E56', verdeClaro: '#E1F5EE', crema: '#F7F5EF', blanco: '#FFFFFF', texto: '#1C1B1F', texto2: '#5F5E5A', borde: '#D3D1C7',
  pista: '#EDEBE4', barra: '#5AB89A', medio: '#EF9F27', medioFondo: '#FAEEDA', medioTexto: '#854F0B', alta: '#E24B4A', media: '#EF9F27', baja: '#639922',
};
const fuente = (peso, tamano) => `${peso} ${tamano}px Inter, system-ui, -apple-system, "Segoe UI", sans-serif`;

function redondeado(g, x, y, ancho, alto, radio) {
  g.beginPath();
  g.roundRect(x, y, ancho, alto, radio);
}

/** Corta un texto en renglones que entren en `ancho`. */
function renglones(g, texto, ancho) {
  const lineas = [];
  let actual = '';
  for (const palabra of texto.split(' ')) {
    const prueba = actual ? `${actual} ${palabra}` : palabra;
    if (actual && g.measureText(prueba).width > ancho) { lineas.push(actual); actual = palabra; } else actual = prueba;
  }
  if (actual) lineas.push(actual);
  return lineas;
}

/** Un texto en un renglón: si no entra, con puntos suspensivos. */
function recortado(g, texto, ancho) {
  if (g.measureText(texto).width <= ancho) return texto;
  let corto = texto;
  while (corto.length > 1 && g.measureText(`${corto}…`).width > ancho) corto = corto.slice(0, -1);
  return `${corto}…`;
}

/** La imagen del resumen (ImagenResumen de la app): 380 de ancho, márgenes de 16 y 12 entre piezas, a tres
    puntos por píxel. Se dibuja en un lienzo alto y se recorta al final. */
function dibujarImagen(x) {
  const ANCHO = 380;
  const ESCALA = 3;
  const M = 16;
  const HUECO = 12;
  const interno = ANCHO - 2 * M;
  const borrador = document.createElement('canvas');
  borrador.width = ANCHO * ESCALA;
  borrador.height = 2600 * ESCALA;
  const g = borrador.getContext('2d');
  g.scale(ESCALA, ESCALA);
  g.fillStyle = C.blanco;
  g.fillRect(0, 0, ANCHO, 2600);
  g.textBaseline = 'alphabetic';
  let y = M;
  const texto = (t, x0, y0, { peso = 400, tamano = 12, color = C.texto, alinear = 'left' } = {}) => {
    g.font = fuente(peso, tamano);
    g.fillStyle = color;
    g.textAlign = alinear;
    g.fillText(t, x0, y0);
    g.textAlign = 'left';
  };
  const marco = (x0, y0, w, h, radio) => { g.strokeStyle = C.verde; g.lineWidth = 2; redondeado(g, x0 + 1, y0 + 1, w - 2, h - 2, radio); g.stroke(); };
  const tarjetaImg = (titulo, alturaContenido, dibujarContenido, separacion = 12) => {
    const alto = 13 + 16 + separacion + alturaContenido + 13;
    g.fillStyle = C.blanco;
    redondeado(g, M + 0.5, y + 0.5, interno - 1, alto - 1, 12);
    g.fill();
    g.strokeStyle = C.borde;
    g.lineWidth = 1;
    g.stroke();
    texto(titulo, M + 13, y + 13 + 12, { peso: 600, tamano: 13, color: C.texto2 });
    dibujarContenido(M + 13, y + 13 + 16 + separacion, interno - 26);
    y += alto + HUECO;
  };

  // La cabecera, la línea y los filtros.
  texto('Zonda', M, y + 17, { peso: 600, tamano: 18, color: C.verde });
  texto(tituloResumen(), ANCHO - M, y + 16, { tamano: 13, color: C.texto2, alinear: 'right' });
  y += 22 + HUECO;
  g.font = fuente(400, 12);
  const linea = renglones(g, lineaDelResumen(x), interno);
  linea.forEach((l, i) => texto(l, M, y + 12 + i * 15, { color: C.texto2 }));
  y += linea.length * 15 + HUECO;
  if (cantidadFiltros()) {
    g.font = fuente(400, 12);
    const filas = renglones(g, `Filtros: ${gruposDeFiltros(x, 'fecha').join(' · ')}`, interno - 16);
    const ancho = Math.min(interno, Math.max(...filas.map((f) => g.measureText(f).width)) + 16);
    g.fillStyle = C.crema;
    redondeado(g, M, y, ancho, filas.length * 15 + 8, 6);
    g.fill();
    filas.forEach((f, i) => texto(f, M + 8, y + 4 + 12 + i * 15));
    y += filas.length * 15 + 8 + HUECO;
  }

  // Los totales: tres tarjetas iguales.
  const anchoTotal = (interno - 20) / 3;
  const cobertura = porciento(x.revisadas, x.total) || '0 %';
  [
    { n: x.total, etiqueta: plural(x.total, 'alerta', 'alertas'), fondo: C.crema, color: C.texto, elegida: false },
    { n: x.revisadas, etiqueta: `${plural(x.revisadas, 'revisada', 'revisadas')} · ${cobertura}`, fondo: C.verdeClaro, color: C.verde, elegida: r.revision === 'revisadas' },
    { n: x.sinRevisar, etiqueta: 'sin revisar', fondo: C.medioFondo, color: C.medioTexto, elegida: r.revision === 'sin_revisar' },
  ].forEach((t, i) => {
    const x0 = M + i * (anchoTotal + 10);
    g.fillStyle = t.fondo;
    redondeado(g, x0, y, anchoTotal, 62, 12);
    g.fill();
    if (t.elegida) marco(x0, y, anchoTotal, 62, 12);
    texto(numero(t.n), x0 + 12, y + 11 + 22, { peso: 600, tamano: 22, color: t.color });
    let tamano = 11;
    g.font = fuente(400, tamano);
    while (tamano > 8 && g.measureText(t.etiqueta).width > anchoTotal - 24) { tamano -= 0.25; g.font = fuente(400, tamano); }
    texto(t.etiqueta, x0 + 12, y + 11 + 27 + 2 + 11, { tamano, color: C.texto2 });
  });
  y += 62 + HUECO;

  // Las métricas: dos tarjetas sin fondo; el nombre puede ir en dos renglones, como en la app.
  const anchoMetrica = (interno - 10) / 2;
  const altas = porciento(x.altasParte, x.altasTotal);
  const metricasImg = [
    { valor: x.mediana === null ? '—' : duracionCorta(x.mediana), nombre: 'hasta la primera revisión', detalle: x.mediana === null ? 'sin revisiones todavía' : 'mediana del período', elegida: r.revision === 'revisadas' },
    { valor: altas || '—', nombre: 'altas revisadas en la primera hora', detalle: x.altasTotal ? `${x.altasParte} de ${x.altasTotal}` : 'ninguna alta con más de 1 h', elegida: sonLasAltas() },
  ];
  g.font = fuente(500, 12);
  for (const m of metricasImg) m.lineas = renglones(g, m.nombre, anchoMetrica - 24);
  const renglonesNombre = Math.max(...metricasImg.map((m) => m.lineas.length));
  const altoMetrica = 10 + 27 + 2 + renglonesNombre * 15 + 2 + 14 + 10;
  metricasImg.forEach((m, i) => {
    const x0 = M + i * (anchoMetrica + 10);
    if (m.elegida) marco(x0, y, anchoMetrica, altoMetrica, 12);
    texto(m.valor, x0 + 12, y + 10 + 22, { peso: 600, tamano: 22, color: C.verde });
    m.lineas.forEach((l, j) => texto(l, x0 + 12, y + 10 + 27 + 2 + 12 + j * 15, { peso: 500, tamano: 12, color: C.texto }));
    texto(m.detalle, x0 + 12, y + 10 + 27 + 2 + renglonesNombre * 15 + 2 + 11, { tamano: 11, color: C.texto2 });
  });
  y += altoMetrica + HUECO;

  // El gráfico, con su frase.
  const frase = fraseDelGrafico(x, true);
  g.font = fuente(400, 12);
  const lineasFrase = frase ? renglones(g, frase, interno - 26) : [];
  const muchas = x.barras.length > 12;
  const separacionBarras = x.barras.length <= 7 ? 8 : x.barras.length <= 12 ? 4 : 2;
  tarjetaImg(tituloGrafico(x.escala), 14 + 4 + 90 + 4 + 14 + (lineasFrase.length ? 8 + lineasFrase.length * 15 : 0), (x0, y0, w) => {
    const maximo = Math.max(1, ...x.conteos);
    const columna = (w - separacionBarras * (x.barras.length - 1)) / x.barras.length;
    const hayElegidas = r.barras.size > 0;
    x.barras.forEach((b, i) => {
      const n = x.conteos[i];
      const cx = x0 + i * (columna + separacionBarras);
      const elegida = r.barras.has(b.inicio);
      g.globalAlpha = hayElegidas && !elegida ? 0.35 : 1;
      const anchoBarra = Math.min(columna, 28);
      const alto = n ? Math.max(2, (90 * n) / maximo) : 2;
      const base = y0 + 14 + 4 + 90;
      texto(String(n), cx + columna / 2, base - alto - 4, { peso: 500, tamano: muchas ? 9 : 11, alinear: 'center' });
      g.fillStyle = b.hoy ? C.verde : C.barra;
      redondeado(g, cx + (columna - anchoBarra) / 2, base - alto, anchoBarra, alto, n ? [3, 3, 0, 0] : 1);
      g.fill();
      texto(b.etiqueta || '', cx + columna / 2, base + 4 + 11, { tamano: muchas ? 9 : 11, color: C.texto2, alinear: 'center' });
      g.globalAlpha = 1;
      if (elegida) marco(cx - 2, y0 - 2, columna + 4, 14 + 4 + 90 + 4 + 14 + 4, 8);
    });
    lineasFrase.forEach((l, i) => texto(l, x0, y0 + 126 + 8 + 12 + i * 15, { color: C.texto2 }));
  }, 10);

  // Los tipos de ataque, en los dos bloques.
  const maximoTipo = Math.max(1, ...x.porTipo.values());
  const todoCero = [...x.porTipo.values()].every((n) => n === 0);
  const bloques = [['LO QUE DETECTÓ LA IA', TIPOS_IA], ['LO QUE LA IA NO DETECTÓ', TIPOS_SIN_IA]].filter(([, lista]) => todoCero || lista.some((t) => x.porTipo.get(t)));
  const altoBloques = bloques.reduce((suma, [, lista], i) => suma + (i ? 21 : 0) + 14 + 6 + lista.length * 22, 0);
  tarjetaImg('Por tipo de ataque', altoBloques, (x0, y0, w) => {
    let yy = y0;
    bloques.forEach(([titulo, lista], i) => {
      if (i) { g.fillStyle = C.borde; g.fillRect(x0, yy + 10, w, 1); yy += 21; }
      g.letterSpacing = '0.5px';
      texto(titulo, x0, yy + 11, { peso: 600, tamano: 11, color: C.texto2 });
      g.letterSpacing = '0px';
      yy += 14 + 6;
      lista.forEach((t) => {
        const n = x.porTipo.get(t);
        g.fillStyle = C[severidadDe(t)];
        g.beginPath();
        g.arc(x0 + 4, yy + 8, 4, 0, Math.PI * 2);
        g.fill();
        g.font = fuente(400, 13);
        texto(recortado(g, ETIQUETA_TIPO[t], 168 - 14), x0 + 14, yy + 12, { tamano: 13, color: n ? C.texto : C.texto2 });
        const pistaX = x0 + 168 + 8;
        const pistaW = w - 168 - 8 - 8 - 28;
        g.fillStyle = C.pista;
        redondeado(g, pistaX, yy + 3, pistaW, 10, 5);
        g.fill();
        if (n) { g.fillStyle = C.verde; redondeado(g, pistaX, yy + 3, (pistaW * 0.95 * n) / maximoTipo, 10, 5); g.fill(); }
        texto(numero(n), x0 + w, yy + 12, { peso: 500, tamano: 13, color: n ? C.texto : C.texto2, alinear: 'right' });
        if (r.tipos.has(t)) marco(x0 - 4, yy - 3, w + 8, 22, 8);
        yy += 22;
      });
    });
  }, 10);

  // Reales y falsos positivos.
  const maximoRF = Math.max(1, x.reales, x.falsos);
  tarjetaImg('Reales y falsos positivos (de las revisadas)', 2 * 16 + 10, (x0, y0, w) => {
    [['Reales', x.reales, C.verde, 'reales'], ['Falsos positivos', x.falsos, C.medio, 'falsos']].forEach(([nombre, n, color, clave], i) => {
      const yy = y0 + i * 26;
      texto(nombre, x0, yy + 12, { tamano: 13 });
      const pistaX = x0 + 150 + 8;
      const pistaW = w - 150 - 8 - 8 - 28;
      g.fillStyle = C.pista;
      redondeado(g, pistaX, yy + 3, pistaW, 10, 5);
      g.fill();
      g.fillStyle = color;
      if (n) redondeado(g, pistaX, yy + 3, (pistaW * 0.95 * n) / maximoRF, 10, 5); else redondeado(g, pistaX, yy + 3, 2, 10, 1);
      g.fill();
      texto(numero(n), x0 + w, yy + 12, { peso: 500, tamano: 13, alinear: 'right' });
      if (r.revision === clave) marco(x0 - 4, yy - 3, w + 8, 22, 8);
    });
  });

  // El pie.
  texto('Generado con Zonda · no incluye IPs ni nombres de equipos', M, y + 11, { tamano: 11, color: C.texto2 });
  const alto = y + 14 + M;
  const lienzo = document.createElement('canvas');
  lienzo.width = ANCHO * ESCALA;
  lienzo.height = Math.ceil(alto * ESCALA);
  const final = lienzo.getContext('2d');
  final.fillStyle = C.blanco;
  final.fillRect(0, 0, lienzo.width, lienzo.height);
  final.drawImage(borrador, 0, 0);
  return { lienzo, ancho: ANCHO, alto };
}

/** «Resumen_25-10_31-10», con el primer y el último día, como la app. */
function nombreDeArchivo(x) {
  return `Resumen_${ddMM(x.rg.desde).replace('/', '-')}_${ddMM(x.rg.hasta).replace('/', '-')}`;
}

async function compartir(como) {
  const x = calcular();
  await document.fonts.ready;
  if (como === 'pdf') {
    const pdf = await reportePdf(x, { filtros: gruposDeFiltros(x, 'fecha'), severidadDe, bloques: [TIPOS_IA, TIPOS_SIN_IA], duracion: duracionCorta });
    bajarArchivo(`${nombreDeArchivo(x)}.pdf`, pdf);
    avisoInferior('PDF del resumen descargado.', { duracion: 3000 });
    return;
  }
  const { lienzo } = dibujarImagen(x);
  const png = pngDe(lienzo);
  if (como === 'copiar') {
    try {
      await navigator.clipboard.write([new window.ClipboardItem({ 'image/png': png })]);
      avisoInferior('Imagen copiada. Pegala donde quieras.', { duracion: 3000 });
    } catch (_) {
      avisoInferior('No se pudo copiar la imagen. Probá con «Descargar imagen».', { duracion: 4000 });
    }
    return;
  }
  bajarArchivo(`${nombreDeArchivo(x)}.png`, png);
  avisoInferior('Imagen del resumen descargada.', { duracion: 3000 });
}

function menuCompartir(boton) {
  abrirMenu(boton, [
    { texto: 'Descargar imagen', icono: 'imagen', accion: () => compartir('png') },
    { texto: 'Copiar imagen', icono: 'copiar', accion: () => compartir('copiar') },
    { texto: 'Descargar PDF (A4)', icono: 'descargar', accion: () => compartir('pdf') },
  ]);
}
