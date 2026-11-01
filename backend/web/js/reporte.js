// El PDF del Resumen: un reporte armado para la hoja A4, no la imagen del celular achicada. Lleva la red y el
// período, los totales, las alertas por día con su severidad, los tipos de ataque, cuánto acierta cada método,
// la revisión y las altas sin revisar. Sale siempre en claro y sin IPs ni nombres de equipos, como la imagen.
// Se dibuja en un lienzo del tamaño del área útil de la hoja (515 × 762 puntos, a 300 puntos por pulgada) y
// pdfDe (compartir.js) lo pone en la hoja con sus márgenes de 40, sin librerías.

import { numero } from './util.js';
import { TIPOS, e, servicioCorto, tiposDe } from './comun.js';
import { pdfDe } from './compartir.js';

const ANCHO = 515;
const ALTO = 762;
const ESCALA = 300 / 72;
const HUECO = 10;
const MAX_HUECO = 22;
const FILA = 15.5;
const PIE = 22;
const ALTO_TOTALES = 56;
const MIN_GRAFICO = 150;
const MAX_GRAFICO = 280;
const MAX_ALTAS = 8;
// Lo que se alinea a la derecha queda un punto adentro, para que no lo corte el borde del lienzo.
const DERECHA = ANCHO - 1;
const LOGO = new URL('../img/logo_zonda.png', import.meta.url).href;

// Los colores de la imagen del Resumen (los del tema claro de la app).
const C = {
  verde: '#0F6E56', verdeClaro: '#E1F5EE', verdeOscuro: '#085041', crema: '#F7F5EF', blanco: '#FFFFFF', texto: '#1C1B1F',
  texto2: '#5F5E5A', borde: '#D3D1C7', pista: '#EDEBE4', barra: '#5AB89A', medioFondo: '#FAEEDA', medioTexto: '#854F0B',
  altaFondo: '#FCEBEB', altaTexto: '#A32D2D', alta: '#E24B4A', media: '#EF9F27', baja: '#639922',
};
// En cada barra, de abajo hacia arriba; la leyenda va al revés.
const SEVERIDADES = ['baja', 'media', 'alta'];
const NOMBRE_SEVERIDAD = { alta: 'Alta', media: 'Media', baja: 'Baja' };
const TITULO_GRAFICO = { hora: 'Alertas por hora', dia: 'Alertas por día', semana: 'Alertas por semana' };
const DIA_CORTO = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];
const DIA_LARGO = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

const fuente = (peso, tamano) => `${peso} ${tamano}px Inter, system-ui, -apple-system, "Segoe UI", sans-serif`;
const dos = (n) => String(n).padStart(2, '0');
const ddMM = (d) => `${dos(d.getDate())}/${dos(d.getMonth() + 1)}`;
const hhmm = (d) => `${dos(d.getHours())}:${dos(d.getMinutes())}`;
const dia = (d) => `${DIA_CORTO[d.getDay()]} ${ddMM(d)}`;
const plural = (n, uno, otros) => (n === 1 ? uno : otros);
const enBarra = (a, b) => { const t = Date.parse(a.inicio); return t >= b.inicio && t < b.fin; };

/** «Del sáb 24/10 al vie 30/10», «Sábado 24/10» o, si no son días enteros, con la hora de cada punta. */
function textoPeriodo(rg) {
  const desde = new Date(rg.desde);
  const hasta = new Date(rg.hasta);
  const enteros = hhmm(desde) === '00:00' && hhmm(hasta) === '23:59';
  if (enteros && desde.toDateString() === hasta.toDateString()) return `${DIA_LARGO[desde.getDay()]} ${ddMM(desde)}`;
  if (enteros) return `Del ${dia(desde)} al ${dia(hasta)}`;
  return `Del ${dia(desde)} ${hhmm(desde)} al ${dia(hasta)} ${hhmm(hasta)}`;
}

/** El nombre de cada barra: «sáb 24» con siete días o menos, el número del día con más, y la hora o la
    semana como en el Resumen. Con muchas barras, una sí y otra no. */
function nombreBarra(escala, b, i, cantidad) {
  if (escala !== 'dia') return b.etiqueta || '';
  const d = new Date(b.inicio);
  if (cantidad <= 7) return `${DIA_CORTO[d.getDay()]} ${d.getDate()}`;
  return cantidad > 16 && i % 2 ? '' : String(d.getDate());
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

/**
 * Dibuja el reporte. `x` es lo que calcula el Resumen (calcular() en resumen.js), con el período y los filtros
 * ya aplicados; `o` trae lo que solo sabe el Resumen: los filtros elegidos en palabras (`filtros`), la severidad
 * de cada tipo en esta red (`severidadDe`), los tipos que detecta la IA y los que no (`bloques`) y cómo se
 * escribe una duración (`duracion`).
 */
export async function dibujarReporte(x, o) {
  await Promise.all([400, 500, 600].map((peso) => document.fonts.load(fuente(peso, 12))));
  const logo = new Image();
  logo.src = LOGO;
  try { await logo.decode(); } catch (_) { /* sin el logo, el reporte sale igual */ }

  const lienzo = document.createElement('canvas');
  lienzo.width = Math.round(ANCHO * ESCALA);
  lienzo.height = Math.round(ALTO * ESCALA);
  const g = lienzo.getContext('2d');
  // El blanco va en píxeles enteros: si la última columna quedara a medias, el JPEG la oscurece.
  g.fillStyle = C.blanco;
  g.fillRect(0, 0, lienzo.width, lienzo.height);
  g.scale(ESCALA, ESCALA);
  g.textBaseline = 'alphabetic';
  const texto = (t, x0, y0, { peso = 400, tamano = 9.5, color = C.texto, alinear = 'left' } = {}) => {
    g.font = fuente(peso, tamano);
    g.fillStyle = color;
    g.textAlign = alinear;
    g.fillText(t, x0, y0);
    g.textAlign = 'left';
  };
  // Con borde, la caja se mete medio trazo para que la línea entre entera en el lienzo.
  const caja = (x0, y0, ancho, alto, radio, relleno, borde = null) => {
    const m = borde ? 0.375 : 0;
    g.beginPath();
    g.roundRect(x0 + m, y0 + m, ancho - 2 * m, alto - 2 * m, radio);
    if (relleno) { g.fillStyle = relleno; g.fill(); }
    if (borde) { g.strokeStyle = borde; g.lineWidth = 0.75; g.stroke(); }
  };
  const punto = (x0, y0, color) => {
    g.fillStyle = color;
    g.beginPath();
    g.arc(x0, y0, 2.8, 0, Math.PI * 2);
    g.fill();
  };
  const titulo = (t, x0, y0) => texto(t, x0 + 10, y0 + 17, { peso: 500, tamano: 11 });

  // La cabecera: el logo y Zonda a la izquierda; el reporte, la red y el período a la derecha.
  if (logo.complete && logo.naturalWidth) g.drawImage(logo, 0, 0, 30, 30);
  texto('Zonda', 38, 14, { peso: 600, tamano: 16, color: C.verde });
  texto('Lo que se mueve en tu red, a la vista', 38, 26.5, { tamano: 8.5, color: C.texto2 });
  texto('Reporte de alertas', DERECHA, 12, { peso: 500, tamano: 12.5, alinear: 'right' });
  const red = e.acceso && e.acceso.red ? e.acceso.red.nombre : '';
  if (red) texto(red, DERECHA, 24, { tamano: 9, color: C.texto2, alinear: 'right' });
  texto(textoPeriodo(x.rg), DERECHA, red ? 35.5 : 24, { tamano: 9, color: C.texto2, alinear: 'right' });
  g.fillStyle = C.verde;
  g.fillRect(0, 42, ANCHO, 1.5);
  let y = 43.5 + HUECO;

  // Los filtros, si el Resumen tiene alguno: el reporte muestra lo mismo que la pantalla.
  if (o.filtros.length) {
    g.font = fuente(400, 9);
    const filas = renglones(g, `Filtros: ${o.filtros.join(' · ')}`, ANCHO - 16);
    const ancho = Math.min(ANCHO, Math.max(...filas.map((f) => g.measureText(f).width)) + 16);
    caja(0, y, ancho, filas.length * 12 + 8, 5, C.crema);
    filas.forEach((f, i) => texto(f, 8, y + 13 + i * 12, { tamano: 9 }));
    y += filas.length * 12 + 8 + HUECO;
  }

  // Primero se mide lo que no es el gráfico. El gráfico se queda con el lugar que sobra (entre 150 y 280
  // puntos) y lo que sigue sobrando se reparte en los huecos, para que la hoja no quede vacía abajo.
  const altasSinRevisar = x.lista.filter((a) => a.severidad === 'alta' && a.estadoRevision === 'sin_revisar')
    .sort((p, q) => Date.parse(q.inicio) - Date.parse(p.inicio));
  const maximoTipo = Math.max(1, ...x.porTipo.values());
  const grupos = [['Lo que detectó la IA', o.bloques[0]], ['Lo que la IA no detectó', o.bloques[1]]].map(([nombre, tipos]) => ({
    nombre,
    filas: tipos.filter((t) => x.porTipo.get(t)).sort((p, q) => x.porTipo.get(q) - x.porTipo.get(p)),
  }));
  const altoTipos = 28 + grupos.reduce((suma, gr, i) => suma + (i ? 7 : 0) + 13 + Math.max(1, gr.filas.length) * FILA, 0) + 6;
  const altoMetodos = 28 + x.precision.length * FILA + 8 + 17 + 50 + 6;
  const altoColumnas = Math.max(altoTipos, altoMetodos);
  const altoAltas = (filas, sobran) => 26 + Math.max(1, filas) * FILA + (sobran ? FILA : 0) + 6;
  const libre = (filas) => ALTO - PIE - y - ALTO_TOTALES - altoColumnas - altoAltas(filas, altasSinRevisar.length > filas);
  let filas = Math.min(MAX_ALTAS, altasSinRevisar.length);
  while (filas > 1 && libre(filas) - 3 * HUECO < MIN_GRAFICO) filas -= 1;
  const mostradas = altasSinRevisar.slice(0, filas);
  const sobran = altasSinRevisar.length - mostradas.length;
  const resto = libre(filas);
  const altoGrafico = Math.max(MIN_GRAFICO, Math.min(MAX_GRAFICO, resto - 3 * HUECO));
  const hueco = Math.max(HUECO, Math.min(MAX_HUECO, (resto - altoGrafico) / 3));

  // Los totales, en cuatro tarjetas.
  const cobertura = x.total ? `${Math.round((100 * x.revisadas) / x.total)} %` : '0 %';
  const anchoTotal = (ANCHO - 3 * 8) / 4;
  [
    { n: x.total, etiqueta: plural(x.total, 'alerta', 'alertas'), fondo: C.crema, color: C.texto, color2: C.texto2 },
    { n: x.revisadas, etiqueta: `${plural(x.revisadas, 'revisada', 'revisadas')} · ${cobertura}`, fondo: C.verdeClaro, color: C.verde, color2: C.verdeOscuro },
    { n: x.sinRevisar, etiqueta: 'sin revisar', fondo: C.medioFondo, color: C.medioTexto, color2: C.medioTexto },
    { n: altasSinRevisar.length, etiqueta: plural(altasSinRevisar.length, 'alta sin revisar', 'altas sin revisar'), fondo: C.altaFondo, color: C.altaTexto, color2: C.altaTexto },
  ].forEach((t, i) => {
    const x0 = i * (anchoTotal + 8);
    caja(x0, y, anchoTotal, ALTO_TOTALES, 6, t.fondo);
    texto(numero(t.n), x0 + 12, y + 27, { peso: 600, tamano: 22, color: t.color });
    texto(t.etiqueta, x0 + 12, y + 44, { tamano: 9.5, color: t.color2 });
  });
  y += ALTO_TOTALES + hueco;

  // Las alertas por día (o por hora o por semana), con la severidad apilada y el total arriba.
  caja(0, y, ANCHO, altoGrafico, 6, null, C.borde);
  titulo(TITULO_GRAFICO[x.escala] || 'Alertas', 0, y);
  g.font = fuente(400, 8.5);
  const leyenda = [...SEVERIDADES].reverse();
  let lx = ANCHO - 10 - leyenda.reduce((suma, s) => suma + 10 + g.measureText(NOMBRE_SEVERIDAD[s]).width + 10, -10);
  for (const s of leyenda) {
    caja(lx, y + 11.5, 6.5, 6.5, 1, C[s]);
    texto(NOMBRE_SEVERIDAD[s], lx + 10, y + 17.5, { tamano: 8.5, color: C.texto2 });
    lx += 10 + g.measureText(NOMBRE_SEVERIDAD[s]).width + 10;
  }
  const arriba = y + 30 + 13;
  const base = y + altoGrafico - 23;
  const maximo = Math.max(1, ...x.conteos);
  const lugar = (ANCHO - 20) / Math.max(1, x.barras.length);
  const anchoBarra = Math.min(lugar * 0.6, 36);
  const chico = x.barras.length > 16;
  x.barras.forEach((b, i) => {
    const centro = 10 + i * lugar + lugar / 2;
    const n = x.conteos[i];
    const cuenta = { alta: 0, media: 0, baja: 0 };
    for (const a of x.lista) if (enBarra(a, b) && a.severidad in cuenta) cuenta[a.severidad] += 1;
    const alto = n ? Math.max(2, ((base - arriba) * n) / maximo) : 0;
    let tope = base;
    const visibles = SEVERIDADES.filter((s) => cuenta[s]);
    visibles.forEach((s, j) => {
      const h = (alto * cuenta[s]) / n;
      caja(centro - anchoBarra / 2, tope - h, anchoBarra, h, j === visibles.length - 1 ? [2.5, 2.5, 0, 0] : 0, C[s]);
      tope -= h;
    });
    if (n || x.barras.length <= 7) texto(numero(n), centro, tope - 4.5, { peso: 500, tamano: chico ? 8 : 9, color: n ? C.texto : C.texto2, alinear: 'center' });
    const nombre = nombreBarra(x.escala, b, i, x.barras.length);
    if (nombre) texto(nombre, centro, base + 14, { tamano: chico ? 8 : 9, color: C.texto2, alinear: 'center' });
  });
  g.fillStyle = C.borde;
  g.fillRect(10, base, ANCHO - 20, 0.75);
  y += altoGrafico + hueco;

  // A la izquierda, los tipos de ataque en los dos grupos del Resumen, de más a menos.
  const anchoColumna = (ANCHO - HUECO) / 2;
  caja(0, y, anchoColumna, altoColumnas, 6, null, C.borde);
  titulo('Por tipo de ataque', 0, y);
  let yy = y + 28;
  grupos.forEach((gr, i) => {
    if (i) yy += 7;
    texto(gr.nombre, 10, yy + 9.5, { peso: 500, tamano: 8.5, color: C.texto2 });
    yy += 13;
    if (!gr.filas.length) {
      texto('Ninguna', 10, yy + 11, { tamano: 9, color: C.texto2 });
      yy += FILA;
      return;
    }
    const pistaX = 140;
    const pistaW = anchoColumna - pistaX - 10 - 30;
    for (const t of gr.filas) {
      const n = x.porTipo.get(t);
      punto(13.5, yy + 7.5, C[o.severidadDe(t)] || C.texto2);
      g.font = fuente(400, 9.5);
      texto(recortado(g, TIPOS[t] || t, pistaX - 28), 21, yy + 11, { tamano: 9.5 });
      caja(pistaX, yy + 5, pistaW, 5.5, 2.75, C.pista);
      caja(pistaX, yy + 5, Math.max(2.5, (pistaW * n) / maximoTipo), 5.5, 2.75, C.barra);
      texto(numero(n), anchoColumna - 10, yy + 11, { peso: 500, tamano: 9.5, alinear: 'right' });
      yy += FILA;
    }
  });

  // A la derecha, cuánto acierta cada método sobre las revisadas y cómo se revisó.
  const x1 = anchoColumna + HUECO;
  caja(x1, y, anchoColumna, altoColumnas, 6, null, C.borde);
  titulo('Cuánto acierta cada método', x1, y);
  yy = y + 28;
  for (const p of x.precision) {
    texto(p.m.resumen, x1 + 10, yy + 11, { tamano: 9.5 });
    const pistaX = x1 + 116;
    const pistaW = anchoColumna - 116 - 10 - 80;
    caja(pistaX, yy + 5, pistaW, 5.5, 2.75, C.pista);
    if (p.total && p.parte) caja(pistaX, yy + 5, Math.max(2.5, (pistaW * p.parte) / p.total), 5.5, 2.75, C.verde);
    const valor = p.total ? `${Math.round((100 * p.parte) / p.total)} % · ${numero(p.parte)} de ${numero(p.total)}` : 'sin revisadas';
    texto(valor, x1 + anchoColumna - 10, yy + 11, { tamano: 9, color: p.total ? C.texto : C.texto2, alinear: 'right' });
    yy += FILA;
  }
  yy += 8;
  texto('Revisión', x1 + 10, yy + 12, { peso: 500, tamano: 10 });
  yy += 17;
  const tercio = (anchoColumna - 20) / 3;
  [
    { valor: x.mediana === null ? '—' : o.duracion(x.mediana), etiqueta: 'hasta la primera revisión' },
    { valor: x.altasTotal ? `${x.altasParte} de ${x.altasTotal}` : '—', etiqueta: 'altas revisadas en la primera hora' },
    { valor: `${numero(x.reales)} · ${numero(x.falsos)}`, etiqueta: 'reales · falsos positivos' },
  ].forEach((m, i) => {
    const mx = x1 + 10 + i * tercio;
    texto(m.valor, mx, yy + 14, { peso: 600, tamano: 14, color: C.verde });
    g.font = fuente(400, 8.5);
    renglones(g, m.etiqueta, tercio - 6).slice(0, 3).forEach((l, j) => texto(l, mx, yy + 26 + j * 10.5, { tamano: 8.5, color: C.texto2 }));
  });
  y += altoColumnas + hueco;

  // Las altas sin revisar, las más nuevas primero: el tipo, el servicio, cuándo empezó y cuántos flujos.
  caja(0, y, ANCHO, altoAltas(filas, sobran > 0), 6, null, C.borde);
  titulo('Altas sin revisar', 0, y);
  yy = y + 26;
  if (!mostradas.length) texto('Ninguna en el período', 10, yy + 11, { tamano: 9, color: C.texto2 });
  for (const a of mostradas) {
    g.fillStyle = C.pista;
    g.fillRect(10, yy, ANCHO - 20, 0.75);
    punto(13.5, yy + 8, C.alta);
    texto(`${tiposDe(a).map((t) => TIPOS[t] || t).join(' y ')} · ${servicioCorto(a.servicio)}`, 21, yy + 11.5, { tamano: 9.5 });
    const inicio = new Date(a.inicio);
    const flujos = a.cantidadFlujos || 0;
    texto(`${dia(inicio)} ${hhmm(inicio)} · ${numero(flujos)} ${plural(flujos, 'flujo', 'flujos')}`, ANCHO - 10, yy + 11.5, { tamano: 9, color: C.texto2, alinear: 'right' });
    yy += FILA;
  }
  if (sobran > 0) texto(`Y ${numero(sobran)} más en la vista Alertas`, 10, yy + 11.5, { tamano: 9, color: C.texto2 });

  // El pie, siempre al final de la hoja.
  g.fillStyle = C.borde;
  g.fillRect(0, ALTO - 16, ANCHO, 0.75);
  const ahora = new Date();
  texto(`Generado con Zonda el ${ddMM(ahora)}/${ahora.getFullYear()} a las ${hhmm(ahora)} · sin IPs ni nombres de equipos`, 0, ALTO - 4, { tamano: 8.5, color: C.texto2 });
  texto('1 de 1', DERECHA, ALTO - 4, { tamano: 8.5, color: C.texto2, alinear: 'right' });
  return { lienzo, ancho: ANCHO, alto: ALTO };
}

/** El reporte como PDF de una hoja A4, listo para bajar. */
export async function reportePdf(x, o) {
  const { lienzo, ancho, alto } = await dibujarReporte(x, o);
  return pdfDe(lienzo, ancho, alto);
}
