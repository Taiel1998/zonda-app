// Las opciones de ECharts de cada gráfico del tablero. Los colores salen de las variables de estilos.css
// del tema actual; las reglas son las del tablero: una sola escala por gráfico, marcas finas, grilla
// tenue y sin cortar, barras de a lo sumo 24 px con la punta redondeada, una separación de 2 px del
// color de la superficie entre segmentos, y el texto siempre en los colores de texto, nunca en el de la serie.

import { bitsPorSegundo, ejeNumero, escapar, fechaHora, hora, horaConSegundos, dia, numero, tasa } from './util.js';

/** Los servicios del gráfico de conexiones, siempre en este orden y con este color (el color sigue al
    servicio, nunca a su lugar en el ranking). El orden está validado para daltonismo en claro y oscuro. */
export const GRUPOS = [
  { id: 'ssh', nombre: 'SSH', color: '--serie-1', servicios: ['ssh'] },
  { id: 'web', nombre: 'Web', color: '--serie-2', servicios: ['http', 'ssl', 'https', 'quic', 'http2'] },
  { id: 'dns', nombre: 'DNS', color: '--serie-3', servicios: ['dns', 'mdns', 'llmnr'] },
  { id: 'archivos', nombre: 'Archivos', color: '--serie-4', servicios: ['smb', 'netbios', 'dce_rpc', 'ftp', 'ftp-data', 'nfs', 'tftp'] },
  { id: 'otros', nombre: 'Otros', color: '--serie-otros', servicios: [] },
];
const GRUPO_DE = new Map(GRUPOS.flatMap((g) => g.servicios.map((s) => [s, g.id])));
export const grupoDe = (servicio) => GRUPO_DE.get(servicio) || 'otros';

export const SEVERIDADES = [
  { id: 'alta', nombre: 'Alta', color: '--alta' },
  { id: 'media', nombre: 'Media', color: '--media' },
  { id: 'baja', nombre: 'Baja', color: '--baja' },
];
const PESO = { alta: 3, media: 2, baja: 1 };
export const masGrave = (a, b) => ((PESO[a] || 0) >= (PESO[b] || 0) ? a : b);

export function colores() {
  const estilo = getComputedStyle(document.documentElement);
  const leer = (nombre) => estilo.getPropertyValue(nombre).trim();
  const c = {};
  for (const nombre of ['--superficie', '--superficie-2', '--texto', '--texto-2', '--texto-3', '--grilla', '--eje', '--borde', '--verde', '--arista',
    '--alta', '--media', '--baja', '--serie-1', '--serie-2', '--serie-3', '--serie-4', '--serie-otros', '--neutro']) {
    c[nombre] = leer(nombre);
  }
  return c;
}

const FUENTE = 'Inter, system-ui, -apple-system, "Segoe UI", sans-serif';

function base(c) {
  return {
    textStyle: { fontFamily: FUENTE, color: c['--texto-2'] },
    animationDuration: 500,
    animationDurationUpdate: 350,
    tooltip: {
      confine: true,
      backgroundColor: c['--superficie'],
      borderColor: c['--borde'],
      borderWidth: 1,
      padding: [10, 12],
      textStyle: { fontFamily: FUENTE, color: c['--texto'], fontSize: 13 },
      extraCssText: 'border-radius: 10px; box-shadow: 0 8px 28px rgba(0,0,0,.14);',
    },
  };
}

function ejeTiempo(c, desde, hasta) {
  const largo = hasta - desde;
  return {
    type: 'time',
    min: desde,
    max: hasta,
    axisLine: { lineStyle: { color: c['--eje'], width: 1 } },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: {
      color: c['--texto-2'],
      fontSize: 12,
      hideOverlap: true,
      formatter: (valor) => (largo > 2 * 86400e3 ? dia(valor) : hora(valor)),
    },
    axisPointer: { show: true, type: 'line', lineStyle: { color: c['--texto-3'], width: 1, type: 'solid' }, label: { show: false } },
  };
}

/** El eje vertical; sin datos, llega a `vacio` para que la grilla no muestre marcas repetidas. */
function ejeValor(c, formato, valores = null, vacio = 1) {
  const hayDatos = !valores || valores.some((v) => v > 0);
  return {
    type: 'value',
    min: 0,
    max: hayDatos ? undefined : vacio,
    minInterval: 0,
    splitNumber: 4,
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: c['--grilla'], width: 1, type: 'solid' } },
    axisLabel: { color: c['--texto-2'], fontSize: 12, formatter: formato },
  };
}

function filaGlobito(color, valor, nombre, punto = false) {
  return `<div class="globito-fila"><i class="globito-llave${punto ? ' punto' : ''}" style="background:${color}"></i><b>${escapar(valor)}</b><em>${escapar(nombre)}</em></div>`;
}

function tramoDe(t, paso) {
  if (paso >= 3600) return `${fechaHora(t)} a ${hora(t + paso * 1000)}`;
  if (paso >= 60) return `${fechaHora(t)} a ${hora(t + paso * 1000)}`;
  return `${horaConSegundos(t)} a ${horaConSegundos(t + paso * 1000)}`;
}

/** Barras apiladas con la punta redondeada solo en el segmento de arriba de cada barra. */
function apilar(series, tiempos) {
  const n = tiempos.length;
  for (let i = 0; i < n; i += 1) {
    let arriba = -1;
    series.forEach((s, j) => { if (s.valores[i] > 0) arriba = j; });
    series.forEach((s, j) => {
      const valor = s.valores[i] > 0 ? s.valores[i] : null;
      s.datos[i] = j === arriba ? { value: [tiempos[i], valor], itemStyle: { borderRadius: [4, 4, 0, 0] } } : [tiempos[i], valor];
    });
  }
}

/**
 * Conexiones por segundo, apiladas por servicio, con una línea en cada alerta que empezó en la ventana.
 * datos: { tiempos (ms), porGrupo: {id: [por segundo]}, paso (s), desde, hasta, alertas: [{t, severidad, texto}] }
 */
export function opcionesConexiones(c, datos) {
  const series = GRUPOS.map((g) => ({ grupo: g, valores: datos.porGrupo[g.id], datos: new Array(datos.tiempos.length) }));
  apilar(series, datos.tiempos);
  const alertasEnTramo = new Map();
  for (const alerta of datos.alertas) {
    const i = Math.floor((alerta.t - datos.desde) / (datos.paso * 1000));
    if (!alertasEnTramo.has(i)) alertasEnTramo.set(i, []);
    alertasEnTramo.get(i).push(alerta);
  }
  const conEtiqueta = datos.alertas.length <= 6;
  const totales = datos.tiempos.map((_, i) => GRUPOS.reduce((suma, g) => suma + datos.porGrupo[g.id][i], 0));
  const opciones = {
    ...base(c),
    grid: { left: 8, right: 16, top: 30, bottom: 6, containLabel: true },
    xAxis: ejeTiempo(c, datos.desde, datos.hasta),
    yAxis: ejeValor(c, ejeNumero, totales, 1),
    tooltip: {
      ...base(c).tooltip,
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: c['--texto-3'] } },
      formatter: (params) => {
        const t = params[0].axisValue;
        const i = Math.round((t - datos.desde) / (datos.paso * 1000));
        if (i < 0 || i >= datos.tiempos.length) return '';
        let total = 0;
        const filas = GRUPOS.map((g) => {
          const v = datos.porGrupo[g.id][i];
          total += v;
          return filaGlobito(c[g.color], tasa(v), g.nombre);
        }).reverse();
        let html = `<div class="globito"><div class="globito-titulo">${escapar(tramoDe(datos.tiempos[i], datos.paso))}</div>${filas.join('')}`;
        html += `<div class="globito-separador"></div><div class="globito-fila"><span></span><b>${escapar(tasa(total))}</b><em>conexiones por segundo</em></div>`;
        for (const alerta of alertasEnTramo.get(i) || []) {
          html += `<div class="globito-separador"></div>${filaGlobito(c[`--${alerta.severidad}`], alerta.nivel, alerta.texto, true)}`;
        }
        return `${html}</div>`;
      },
    },
    series: series.map((s, j) => ({
      name: s.grupo.nombre,
      type: 'bar',
      stack: 'conexiones',
      barMaxWidth: 24,
      barMinWidth: 1,
      data: s.datos,
      itemStyle: { color: c[s.grupo.color], borderColor: c['--superficie'], borderWidth: datos.tiempos.length > 300 ? 0 : 1 },
      emphasis: { disabled: true },
      markLine: j !== 0 ? undefined : {
        silent: true,
        symbol: ['none', 'circle'],
        symbolSize: 8,
        animation: false,
        data: datos.alertas.map((a) => ({
          xAxis: a.t,
          lineStyle: { color: c[`--${a.severidad}`], width: 1.5, type: 'solid', opacity: 0.9 },
          itemStyle: { color: c[`--${a.severidad}`], borderColor: c['--superficie'], borderWidth: 2 },
          label: {
            show: conEtiqueta,
            position: 'end',
            formatter: a.corto,
            color: c['--texto'],
            fontSize: 12,
            fontWeight: 600,
            fontFamily: FUENTE,
            backgroundColor: c['--superficie'],
            padding: [2, 4],
            borderRadius: 4,
          },
        })),
      },
    })),
  };
  return opciones;
}

/** Un solo indicador en el tiempo (tráfico o sin respuesta), en el verde de la marca: sin leyenda, el título lo nombra. */
export function opcionesSerieSimple(c, datos, { tipo, formato, nombre }) {
  const color = c['--verde'];
  const serie = tipo === 'linea'
    ? {
      type: 'line',
      showSymbol: false,
      symbolSize: 8,
      lineStyle: { width: 2, color, cap: 'round', join: 'round' },
      areaStyle: { color, opacity: 0.1 },
      itemStyle: { color, borderColor: c['--superficie'], borderWidth: 2 },
      data: datos.tiempos.map((t, i) => [t, datos.valores[i]]),
      sampling: 'lttb',
    }
    : {
      type: 'bar',
      barMaxWidth: 24,
      barMinWidth: 1,
      itemStyle: { color, borderRadius: [4, 4, 0, 0] },
      data: datos.tiempos.map((t, i) => [t, datos.valores[i] > 0 ? datos.valores[i] : null]),
      emphasis: { disabled: true },
    };
  return {
    ...base(c),
    grid: { left: 8, right: 16, top: 16, bottom: 6, containLabel: true },
    xAxis: ejeTiempo(c, datos.desde, datos.hasta),
    yAxis: ejeValor(c, tipo === 'linea' ? formato : ejeNumero, datos.valores, tipo === 'linea' ? 1000 : 1),
    tooltip: {
      ...base(c).tooltip,
      trigger: 'axis',
      formatter: (params) => {
        const t = params[0].axisValue;
        const i = Math.round((t - datos.desde) / (datos.paso * 1000));
        if (i < 0 || i >= datos.tiempos.length) return '';
        return `<div class="globito"><div class="globito-titulo">${escapar(tramoDe(datos.tiempos[i], datos.paso))}</div>${filaGlobito(color, formato(datos.valores[i]), nombre)}</div>`;
      },
    },
    series: [{ name: nombre, ...serie }],
  };
}

export const formatoBits = (v) => bitsPorSegundo(v);

/**
 * Quién habla con quién: los equipos en columnas por área, flechas del origen al destino.
 * datos: { areas: [{nombre, nodos: [{id, nombre, ip, conexiones, forma}]}], aristas: [{origen, destino, conexiones, fallidas, bytes, servicios, severidad, alertaTexto}] }
 */
export function opcionesMapa(c, datos) {
  // Las posiciones van en píxeles del recuadro, con dos anclas invisibles en las esquinas: así ECharts no
  // estira las coordenadas para llenarlo y los círculos siguen siendo círculos.
  const ancho = Math.max(320, datos.ancho || 800);
  const alto = Math.max(240, datos.alto || 380);
  const margenX = Math.min(90, ancho * 0.08);
  const columnas = datos.areas.length;
  const xDe = (columna) => (columnas <= 1 ? ancho / 2 : margenX + (columna * (ancho - 2 * margenX)) / (columnas - 1));
  const filasMax = Math.max(1, ...datos.areas.map((a) => a.nodos.length));
  const arriba = 70;
  const abajo = alto - 56;
  const altoFila = filasMax > 1 ? Math.min(120, (abajo - arriba) / (filasMax - 1)) : 0;
  const nodos = [
    { id: 'ancla:1', name: '', x: 0, y: 0, symbolSize: 0, label: { show: false }, tooltip: { show: false }, emphasis: { disabled: true } },
    { id: 'ancla:2', name: '', x: ancho, y: alto, symbolSize: 0, label: { show: false }, tooltip: { show: false }, emphasis: { disabled: true } },
  ];
  const maxConexiones = Math.max(1, ...datos.areas.flatMap((a) => a.nodos.map((n) => n.conexiones)));
  datos.areas.forEach((area, columna) => {
    nodos.push({
      id: `area:${area.nombre}`,
      name: area.nombre,
      x: xDe(columna),
      y: 22,
      symbolSize: 1,
      itemStyle: { color: 'transparent', borderWidth: 0 },
      label: { show: true, position: 'inside', formatter: area.nombre, color: c['--texto-2'], fontSize: 12.5, fontWeight: 600 },
      tooltip: { show: false },
      emphasis: { disabled: true },
    });
    const centro = (arriba + abajo) / 2;
    const primero = centro - ((area.nodos.length - 1) * altoFila) / 2;
    area.nodos.forEach((nodo, fila) => {
      const tamano = 16 + 18 * (Math.log1p(nodo.conexiones) / Math.log1p(maxConexiones));
      nodos.push({
        id: nodo.id,
        name: nodo.nombre,
        x: xDe(columna),
        y: primero + fila * altoFila,
        symbol: nodo.forma,
        symbolSize: nodo.forma === 'roundRect' ? [tamano * 1.15, tamano * 0.9] : tamano,
        value: nodo.conexiones,
        itemStyle: { color: c['--superficie'], borderColor: c['--texto-2'], borderWidth: 2 },
        label: {
          show: true,
          position: 'bottom',
          distance: 6,
          formatter: `{n|${nodo.nombre}}${nodo.nombre !== nodo.ip ? `\n{ip|${nodo.ip}}` : ''}`,
          rich: {
            n: { color: c['--texto'], fontSize: 12.5, fontWeight: 600, fontFamily: FUENTE, lineHeight: 17 },
            ip: { color: c['--texto-2'], fontSize: 11.5, fontFamily: FUENTE, lineHeight: 15 },
          },
        },
        datos: nodo,
      });
    });
  });
  const maxArista = Math.max(1, ...datos.aristas.map((a) => a.conexiones));
  const aristas = datos.aristas.map((a) => ({
    source: a.origen,
    target: a.destino,
    value: a.conexiones,
    lineStyle: {
      width: 1.5 + 4.5 * (Math.log1p(a.conexiones) / Math.log1p(maxArista)),
      color: a.severidad ? c[`--${a.severidad}`] : c['--arista'],
      opacity: a.severidad ? 0.95 : 0.8,
      curveness: 0.16,
    },
    datos: a,
  }));
  return {
    ...base(c),
    tooltip: {
      ...base(c).tooltip,
      trigger: 'item',
      formatter: (p) => {
        if (p.dataType === 'edge') {
          const a = p.data.datos;
          let html = `<div class="globito"><div class="globito-titulo">${escapar(a.origenNombre)} → ${escapar(a.destinoNombre)}</div>`;
          html += `<div class="globito-fila"><span></span><b>${escapar(numero(a.conexiones))}</b><em>conexiones</em></div>`;
          html += `<div class="globito-fila"><span></span><b>${escapar(numero(a.fallidas))}</b><em>sin respuesta</em></div>`;
          html += `<div class="globito-nota">${escapar(a.servicios.map((s) => s.toUpperCase()).slice(0, 4).join(' · '))}</div>`;
          if (a.severidad) html += `<div class="globito-separador"></div>${filaGlobito(c[`--${a.severidad}`], a.nivel, a.alertaTexto, true)}`;
          return `${html}</div>`;
        }
        const n = p.data.datos;
        if (!n) return '';
        return `<div class="globito"><div class="globito-titulo">${escapar(n.nombre)}</div><div class="globito-nota">${escapar(n.ip)} · ${escapar(n.area)}</div><div class="globito-fila"><span></span><b>${escapar(numero(n.conexiones))}</b><em>conexiones en la ventana</em></div></div>`;
      },
    },
    series: [{
      type: 'graph',
      layout: 'none',
      left: 0,
      right: 0,
      top: 0,
      bottom: 0,
      roam: false,
      nodeScaleRatio: 0,
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [0, 9],
      data: nodos,
      links: aristas,
      emphasis: { focus: 'adjacency', lineStyle: { opacity: 1 } },
      blur: { itemStyle: { opacity: 0.35 }, lineStyle: { opacity: 0.15 }, label: { opacity: 0.4 } },
      animationDurationUpdate: 300,
    }],
  };
}

/** Alertas por día, apiladas por severidad (los colores de severidad, siempre con su nombre en la leyenda). */
export function opcionesPorDia(c, datos) {
  const series = SEVERIDADES.slice().reverse().map((s) => ({ sev: s, valores: datos.porSeveridad[s.id], datos: new Array(datos.dias.length) }));
  const indices = datos.dias.map((_, i) => i);
  apilar(series, indices);
  for (const s of series) s.datos = s.datos.map((d) => (Array.isArray(d) ? d[1] : { value: d.value[1], itemStyle: d.itemStyle }));
  const totales = datos.dias.map((_, i) => SEVERIDADES.reduce((suma, s) => suma + datos.porSeveridad[s.id][i], 0));
  return {
    ...base(c),
    grid: { left: 8, right: 12, top: 16, bottom: 6, containLabel: true },
    xAxis: {
      type: 'category',
      data: datos.dias.map((d) => dia(d)),
      axisLine: { lineStyle: { color: c['--eje'] } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { color: c['--texto-2'], fontSize: 12, hideOverlap: true },
      axisPointer: { show: true, type: 'shadow', shadowStyle: { color: c['--neutro'], opacity: 0.5 }, label: { show: false } },
    },
    yAxis: { ...ejeValor(c, (v) => numero(v), totales, 4), minInterval: 1 },
    tooltip: {
      ...base(c).tooltip,
      trigger: 'axis',
      formatter: (params) => {
        const i = params[0].dataIndex;
        if (i === undefined || i < 0) return '';
        const filas = SEVERIDADES.map((s) => filaGlobito(c[s.color], numero(datos.porSeveridad[s.id][i]), s.nombre));
        const total = SEVERIDADES.reduce((suma, s) => suma + datos.porSeveridad[s.id][i], 0);
        return `<div class="globito"><div class="globito-titulo">${escapar(datos.etiquetas[i])}</div>${filas.join('')}<div class="globito-separador"></div><div class="globito-fila"><span></span><b>${escapar(numero(total))}</b><em>alertas</em></div></div>`;
      },
    },
    series: series.map((s) => ({
      name: s.sev.nombre,
      type: 'bar',
      stack: 'severidad',
      barMaxWidth: 24,
      data: s.datos,
      itemStyle: { color: c[s.sev.color], borderColor: c['--superficie'], borderWidth: 1 },
      emphasis: { disabled: true },
    })),
  };
}

/** Barras horizontales de una sola serie, de mayor a menor, con el valor en la punta. */
export function opcionesBarrasH(c, datos, { formato = (v) => numero(v), maximo, globito } = {}) {
  const color = c['--verde'];
  const etiquetas = datos.etiquetas.slice().reverse();
  const valores = datos.valores.slice().reverse();
  const textos = (datos.textos || datos.valores.map((v) => formato(v))).slice().reverse();
  return {
    ...base(c),
    grid: { left: 8, right: 96, top: 6, bottom: 6, containLabel: true },
    xAxis: { type: 'value', min: 0, max: maximo, show: false },
    yAxis: {
      type: 'category',
      data: etiquetas,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: c['--texto'], fontSize: 13, fontFamily: FUENTE, width: 190, overflow: 'truncate' },
    },
    tooltip: {
      ...base(c).tooltip,
      trigger: 'item',
      formatter: (p) => `<div class="globito"><div class="globito-titulo">${escapar(p.name)}</div>${filaGlobito(color, globito ? globito(datos.etiquetas.length - 1 - p.dataIndex) : textos[p.dataIndex], datos.nombre || '')}</div>`,
    },
    series: [{
      type: 'bar',
      barMaxWidth: 18,
      data: valores,
      itemStyle: { color, borderRadius: [0, 4, 4, 0] },
      showBackground: true,
      backgroundStyle: { color: c['--neutro'], opacity: 0.5, borderRadius: [0, 4, 4, 0] },
      label: {
        show: true,
        position: 'right',
        distance: 8,
        color: c['--texto'],
        fontSize: 13,
        fontWeight: 600,
        fontFamily: FUENTE,
        formatter: (p) => textos[p.dataIndex],
      },
      emphasis: { itemStyle: { opacity: 0.85 } },
    }],
  };
}
