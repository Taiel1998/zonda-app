// La vista Resumen, calculada como el Resumen de la app (RF-07): 7 o 30 días o las fechas que se elijan,
// cifras que se tocan para filtrar, por día y por tipo, cobertura, tiempo hasta la primera revisión,
// altas revisadas en la primera hora y precisión de cada método. Se descarga como imagen o como PDF,
// sin IPs ni nombres de equipos.

import { dia, diaLargo, el, fechaHora, numero, porcentaje, vaciar } from './util.js';
import { SEVERIDADES, colores, opcionesBarrasH, opcionesPorDia } from './graficos.js';
import { $, METODOS, ORDEN_TIPOS, TIPOS, ahoraServidor, dibujar, e, refrescarTablas, tiposDe } from './comun.js';
import { abrirHoja, avisoInferior } from './hojas.js';

const r = { dias: 7, fechas: null, tipos: new Set(), revision: null, resultado: null };
const UNA_HORA = 3600e3;

function rango() {
  if (r.fechas) return { desde: r.fechas.desde, hasta: r.fechas.hasta, titulo: 'Resumen' };
  const hoy = new Date(ahoraServidor());
  hoy.setHours(0, 0, 0, 0);
  const desde = hoy.getTime() - (r.dias - 1) * 86400e3;
  return { desde, hasta: ahoraServidor(), titulo: r.dias === 7 ? 'Resumen semanal' : 'Resumen mensual' };
}

function delPeriodo() {
  const { desde, hasta } = rango();
  return [...e.alertas.values()].filter((a) => { const t = Date.parse(a.inicio); return t >= desde && t <= hasta; });
}

function conFiltros(lista, { conTipos = true } = {}) {
  return lista.filter((a) => {
    if (conTipos && r.tipos.size && !tiposDe(a).some((t) => r.tipos.has(t))) return false;
    if (r.revision === 'revisadas' && a.estadoRevision === 'sin_revisar') return false;
    if (r.revision === 'sin_revisar' && a.estadoRevision !== 'sin_revisar') return false;
    if (r.resultado && a.estadoRevision !== r.resultado) return false;
    return true;
  });
}

/** La primera revisión, con la hora del dispositivo que la hizo (decisión 21), como la app. */
function primeraRevision(a) {
  const tiempos = (a.historial || []).map((rev) => Date.parse(rev.revisadaEn || rev.fecha)).filter((t) => !Number.isNaN(t));
  return tiempos.length ? Math.min(...tiempos) : null;
}

function revisadaEnLaPrimeraHora(a) {
  const primera = primeraRevision(a);
  return primera !== null && a.estadoRevision !== 'sin_revisar' && primera - Date.parse(a.creadaEn) <= UNA_HORA;
}

function mediana(valores) {
  if (!valores.length) return null;
  const v = [...valores].sort((x, y) => x - y);
  const m = Math.floor(v.length / 2);
  return v.length % 2 ? v[m] : (v[m - 1] + v[m]) / 2;
}

function duracionLarga(ms) {
  const minutos = Math.round(ms / 60000);
  if (minutos < 60) return `${minutos} min`;
  const horas = Math.floor(minutos / 60);
  return minutos % 60 ? `${horas} h ${minutos % 60} min` : `${horas} h`;
}

/** Todo lo que muestra el Resumen, en un objeto: lo usan la pantalla, la imagen y el PDF. */
function calcular() {
  const periodo = delPeriodo();
  const lista = conFiltros(periodo);
  const ahora = ahoraServidor();
  const revisadas = lista.filter((a) => a.estadoRevision !== 'sin_revisar');
  const reales = revisadas.filter((a) => a.estadoRevision === 'ataque_real').length;
  const tiempos = revisadas.map((a) => { const p = primeraRevision(a); return p === null ? null : p - Date.parse(a.creadaEn); }).filter((t) => t !== null && t >= 0);
  const altas = lista.filter((a) => a.severidad === 'alta');
  const altasEnHora = altas.filter(revisadaEnLaPrimeraHora).length;
  const altasConsideradas = altas.filter((a) => revisadaEnLaPrimeraHora(a) || ahora - Date.parse(a.creadaEn) > UNA_HORA).length;
  const { desde, hasta, titulo } = rango();
  const dias = [];
  for (let d = new Date(desde); d.getTime() <= hasta; d.setDate(d.getDate() + 1)) { const x = new Date(d); x.setHours(12, 0, 0, 0); dias.push(x.getTime()); }
  const claveDia = (ms) => { const d = new Date(ms); d.setHours(12, 0, 0, 0); return d.getTime(); };
  const porSeveridad = Object.fromEntries(SEVERIDADES.map((s) => [s.id, new Array(dias.length).fill(0)]));
  for (const a of lista) { const i = dias.indexOf(claveDia(Date.parse(a.inicio))); if (i >= 0) porSeveridad[a.severidad][i] += 1; }
  const porTipo = new Map();
  // Por tipo, con todos los filtros menos el de tipo: así las barras de los otros tipos siguen para sumarlas.
  for (const a of conFiltros(periodo, { conTipos: false })) for (const t of tiposDe(a)) porTipo.set(t, (porTipo.get(t) || 0) + 1);
  const precision = METODOS.map((m) => {
    const marcadas = revisadas.filter((a) => a.veredictos[m.id]);
    const buenas = marcadas.filter((a) => a.estadoRevision === 'ataque_real').length;
    return { m, marcadas: marcadas.length, buenas };
  });
  return {
    titulo, desde, hasta, lista, total: lista.length, revisadas: revisadas.length, sinRevisar: lista.length - revisadas.length,
    reales, falsos: revisadas.length - reales, mediana: mediana(tiempos), altasEnHora, altasConsideradas,
    dias, porSeveridad, porTipo, precision,
  };
}

function metrica(valor, texto, activo, alTocar) {
  if (!alTocar) return el('div', { class: 'metrica' }, el('strong', {}, valor), el('span', {}, texto));
  return el('button', { type: 'button', class: 'metrica', 'aria-pressed': String(Boolean(activo)), onclick: alTocar }, el('strong', {}, valor), el('span', {}, texto));
}

function alternarFiltro(clave, valor) {
  r[clave] = r[clave] === valor ? null : valor;
  pintarResumen();
}

export function pintarResumen() {
  const c = colores();
  const x = calcular();
  for (const boton of $('periodos').querySelectorAll('button')) {
    const valor = boton.dataset.dias;
    boton.setAttribute('aria-pressed', String(valor === 'fechas' ? Boolean(r.fechas) : !r.fechas && Number(valor) === r.dias));
  }
  $('titulo-resumen').textContent = x.titulo;
  $('sub-resumen').textContent = `Del ${diaLargo(x.desde)} al ${diaLargo(x.hasta)} · ${numero(x.total)} ${x.total === 1 ? 'alerta' : 'alertas'}`;

  const activos = [];
  for (const t of r.tipos) activos.push({ texto: TIPOS[t], quitar: () => { r.tipos.delete(t); pintarResumen(); } });
  if (r.revision) activos.push({ texto: r.revision === 'revisadas' ? 'Revisadas' : 'Sin revisar', quitar: () => alternarFiltro('revision', r.revision) });
  if (r.resultado) activos.push({ texto: r.resultado === 'ataque_real' ? 'Reales' : 'Falsos positivos', quitar: () => alternarFiltro('resultado', r.resultado) });
  vaciar($('filtros-resumen'), activos.map((f) => el('button', { type: 'button', class: 'chip-filtro', 'aria-pressed': 'true', title: `Quitar el filtro ${f.texto}`, onclick: f.quitar }, `${f.texto} ✕`)));

  vaciar($('metricas-resumen'),
    metrica(numero(x.total), 'alertas en el período'),
    metrica(numero(x.revisadas), 'revisadas', r.revision === 'revisadas', () => alternarFiltro('revision', 'revisadas')),
    metrica(numero(x.sinRevisar), 'sin revisar', r.revision === 'sin_revisar', () => alternarFiltro('revision', 'sin_revisar')),
    metrica(`${numero(x.reales)} · ${numero(x.falsos)}`, 'reales · falsos positivos (de las revisadas)', Boolean(r.resultado), () => alternarFiltro('resultado', r.resultado === 'ataque_real' ? 'falso_positivo' : r.resultado === 'falso_positivo' ? null : 'ataque_real')));

  vaciar($('metricas-revision'),
    metrica(porcentaje(x.revisadas, x.total), `cobertura de revisión · ${numero(x.revisadas)} de ${numero(x.total)}`),
    metrica(x.mediana === null ? '—' : duracionLarga(x.mediana), x.mediana === null ? 'sin revisiones todavía' : 'hasta la primera revisión · mediana del período'),
    metrica(x.altasConsideradas ? `${numero(x.altasEnHora)} de ${numero(x.altasConsideradas)}` : '—', x.altasConsideradas ? 'altas revisadas en la primera hora' : 'ninguna alta con más de 1 h'),
    metrica(numero(x.lista.filter((a) => a.estadoRevision === 'sin_revisar' && a.severidad === 'alta').length), 'altas sin revisar'));

  dibujar('por-dia', opcionesPorDia(c, { dias: x.dias, porSeveridad: x.porSeveridad, etiquetas: x.dias.map((d) => diaLargo(d)) }));
  e.tablas['por-dia'] = {
    columnas: ['Día', 'Alta', 'Media', 'Baja', 'Total'], numericas: 4,
    filas: x.dias.map((d, i) => [diaLargo(d), ...SEVERIDADES.map((s) => numero(x.porSeveridad[s.id][i])), numero(SEVERIDADES.reduce((t, s) => t + x.porSeveridad[s.id][i], 0))]).reverse(),
  };

  const tipos = ORDEN_TIPOS.filter((t) => x.porTipo.get(t)).sort((a, b) => x.porTipo.get(b) - x.porTipo.get(a));
  const grafico = dibujar('por-tipo', opcionesBarrasH(c, { etiquetas: tipos.map((t) => `${r.tipos.has(t) ? '● ' : ''}${TIPOS[t]}`), valores: tipos.map((t) => x.porTipo.get(t)), nombre: 'alertas' }));
  if (grafico && !grafico.__tocable) {
    grafico.__tocable = true;
    grafico.on('click', (p) => {
      const t = ORDEN_TIPOS.find((id) => p.name.replace('● ', '') === TIPOS[id]);
      if (!t) return;
      if (r.tipos.has(t)) r.tipos.delete(t); else r.tipos.add(t);
      pintarResumen();
    });
  }
  e.tablas['por-tipo'] = { columnas: ['Tipo', 'Alertas'], numericas: 1, filas: tipos.map((t) => [TIPOS[t], numero(x.porTipo.get(t))]) };

  const porEquipo = new Map();
  for (const a of x.lista) {
    const actual = porEquipo.get(a.destinoIp) || { nombre: a.destinoNombre ? `${a.destinoNombre} (${a.destinoIp})` : a.destinoIp, n: 0 };
    actual.n += 1;
    porEquipo.set(a.destinoIp, actual);
  }
  const equipos = [...porEquipo.values()].sort((a, b) => b.n - a.n).slice(0, 8);
  dibujar('equipos', opcionesBarrasH(c, { etiquetas: equipos.map((q) => q.nombre), valores: equipos.map((q) => q.n), nombre: 'alertas' }));
  e.tablas.equipos = { columnas: ['Equipo', 'Alertas'], numericas: 1, filas: equipos.map((q) => [q.nombre, numero(q.n)]) };

  dibujar('acierto', opcionesBarrasH(c, {
    etiquetas: x.precision.map((p) => p.m.resumen),
    valores: x.precision.map((p) => (p.marcadas ? (100 * p.buenas) / p.marcadas : 0)),
    textos: x.precision.map((p) => (p.marcadas ? `${numero((100 * p.buenas) / p.marcadas, 0)} % · ${numero(p.buenas)} de ${numero(p.marcadas)}` : 'sin revisadas')),
    nombre: 'de las revisadas que marcó',
  }, { maximo: 100 }));
  e.tablas.acierto = {
    columnas: ['Método', 'Revisadas que marcó', 'Ataque real', 'Precisión'], numericas: 3,
    filas: x.precision.map((p) => [p.m.resumen, numero(p.marcadas), numero(p.buenas), p.marcadas ? `${numero((100 * p.buenas) / p.marcadas, 1)} %` : '–']),
  };
  refrescarTablas('por-dia', 'por-tipo', 'equipos', 'acierto');
}

export function cambiarPeriodo(valor) {
  if (valor === 'fechas') { hojaFechas(); return; }
  r.dias = Number(valor);
  r.fechas = null;
  pintarResumen();
}

function aLocal(ms) {
  const d = new Date(ms);
  const dos = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}T${dos(d.getHours())}:${dos(d.getMinutes())}`;
}

function hojaFechas() {
  const { desde, hasta } = rango();
  const campoDesde = el('input', { type: 'datetime-local', value: aLocal(desde) });
  const campoHasta = el('input', { type: 'datetime-local', value: aLocal(hasta) });
  const error = el('div', { class: 'error-hoja', hidden: true });
  abrirHoja({
    titulo: 'Elegí las fechas',
    cuerpo: [el('label', { class: 'campo' }, el('span', {}, 'Desde'), campoDesde), el('label', { class: 'campo' }, el('span', {}, 'Hasta'), campoHasta), error],
    acciones: [
      { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
      {
        texto: 'Aplicar', tipo: 'principal', alHacer: (h) => {
          const d = Date.parse(campoDesde.value); const a = Date.parse(campoHasta.value);
          if (!(a > d)) { error.hidden = false; error.textContent = 'La hora de «Hasta» tiene que ser después de la de «Desde».'; return; }
          r.fechas = { desde: d, hasta: a }; h.cerrar(); pintarResumen();
        },
      },
    ],
  });
}

// ---- Descargar: la imagen se dibuja en un lienzo y el PDF es la impresión del navegador, en A4 ----

export function guardarPdf() {
  window.print();
}

export function descargarImagen() {
  const x = calcular();
  const ancho = 1080;
  const lienzo = document.createElement('canvas');
  const escala = 2;
  const alto = 1360;
  lienzo.width = ancho * escala;
  lienzo.height = alto * escala;
  const g = lienzo.getContext('2d');
  g.scale(escala, escala);
  const fuente = (peso, tamano) => `${peso} ${tamano}px Inter, system-ui, sans-serif`;
  g.fillStyle = '#f7f5ef';
  g.fillRect(0, 0, ancho, alto);
  g.fillStyle = '#ffffff';
  redondeado(g, 40, 40, ancho - 80, alto - 80, 24);
  g.fill();
  const logo = document.querySelector('.marca img');
  if (logo && logo.complete) g.drawImage(logo, 80, 80, 64, 64);
  g.fillStyle = '#1c1b1f';
  g.font = fuente(600, 40);
  g.fillText(x.titulo, 164, 112);
  g.font = fuente(400, 24);
  g.fillStyle = '#5f5e5a';
  g.fillText(`Del ${diaLargo(x.desde)} al ${diaLargo(x.hasta)} · ${e.acceso.red ? e.acceso.red.nombre : ''}`, 164, 146);

  const cifras = [[numero(x.total), 'alertas'], [numero(x.revisadas), 'revisadas'], [numero(x.sinRevisar), 'sin revisar'], [porcentaje(x.revisadas, x.total), 'cobertura']];
  cifras.forEach(([valor, texto], i) => {
    const x0 = 80 + i * 232;
    g.fillStyle = '#fbfaf6';
    redondeado(g, x0, 190, 212, 120, 16);
    g.fill();
    g.fillStyle = '#1c1b1f';
    g.font = fuente(600, 44);
    g.fillText(valor, x0 + 20, 252);
    g.font = fuente(400, 20);
    g.fillStyle = '#5f5e5a';
    g.fillText(texto, x0 + 20, 288);
  });

  const porDia = e.graficos['por-dia'];
  if (porDia) {
    const imagen = new Image();
    imagen.onload = () => { g.drawImage(imagen, 80, 350, 920, 380); terminar(); };
    imagen.src = porDia.getDataURL({ pixelRatio: 2, backgroundColor: '#ffffff' });
  } else {
    terminar();
  }

  function terminar() {
    g.fillStyle = '#1c1b1f';
    g.font = fuente(600, 26);
    g.fillText('Por tipo de ataque', 80, 790);
    const tipos = ORDEN_TIPOS.filter((t) => x.porTipo.get(t)).sort((a, b) => x.porTipo.get(b) - x.porTipo.get(a)).slice(0, 6);
    const maximo = Math.max(1, ...tipos.map((t) => x.porTipo.get(t)));
    tipos.forEach((t, i) => {
      const y = 830 + i * 46;
      g.font = fuente(400, 21);
      g.fillStyle = '#1c1b1f';
      g.fillText(TIPOS[t], 80, y + 20);
      g.fillStyle = '#edebe4';
      redondeado(g, 380, y + 2, 520, 24, 6);
      g.fill();
      g.fillStyle = '#0f6e56';
      redondeado(g, 380, y + 2, Math.max(8, (520 * x.porTipo.get(t)) / maximo), 24, 6);
      g.fill();
      g.fillStyle = '#1c1b1f';
      g.font = fuente(600, 21);
      g.fillText(numero(x.porTipo.get(t)), 916, y + 21);
    });
    g.font = fuente(600, 26);
    g.fillText('Precisión de cada método (de las revisadas)', 80, 1150);
    g.font = fuente(400, 21);
    x.precision.forEach((p, i) => {
      g.fillStyle = '#5f5e5a';
      g.fillText(`${p.m.resumen}: ${p.marcadas ? `${numero((100 * p.buenas) / p.marcadas, 0)} % (${numero(p.buenas)} de ${numero(p.marcadas)})` : 'sin revisadas'}`, 80 + i * 320, 1190);
    });
    g.fillStyle = '#8a8983';
    g.font = fuente(400, 18);
    g.fillText('Generado con Zonda · no incluye IPs ni nombres de equipos', 80, 1280);
    const enlace = document.createElement('a');
    enlace.download = `Resumen_${dia(x.desde).replace('/', '-')}_${dia(x.hasta).replace('/', '-')}.png`;
    enlace.href = lienzo.toDataURL('image/png');
    enlace.click();
    avisoInferior('Imagen del resumen descargada.', { duracion: 3000 });
  }
}

function redondeado(g, x, y, ancho, alto, radio) {
  g.beginPath();
  g.moveTo(x + radio, y);
  g.arcTo(x + ancho, y, x + ancho, y + alto, radio);
  g.arcTo(x + ancho, y + alto, x, y + alto, radio);
  g.arcTo(x, y + alto, x, y, radio);
  g.arcTo(x, y, x + ancho, y, radio);
  g.closePath();
}

export const textoFechasResumen = () => (r.fechas ? `${fechaHora(r.fechas.desde)} a ${fechaHora(r.fechas.hasta)}` : '');
