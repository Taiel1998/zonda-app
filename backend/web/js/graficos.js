// Las opciones de ECharts de cada gráfico del tablero. Los colores salen de las variables de estilos.css
// del tema actual; las reglas son las del tablero: una sola escala por gráfico, marcas finas, grilla
// tenue y sin cortar, barras de a lo sumo 24 px con la punta redondeada, una separación de 2 px del
// color de la superficie entre segmentos, y el texto siempre en los colores de texto, nunca en el de la serie.

import { bitsPorSegundo, ejeNumero, escapar, fechaHora, hora, horaConSegundos, dia, numero } from './util.js';

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
  for (const nombre of ['--superficie', '--superficie-2', '--texto', '--texto-2', '--texto-3', '--grilla', '--eje', '--borde', '--borde-fuerte', '--verde', '--arista',
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
      // Aparece y se va al instante, como en Grafana: con la transición de ECharts quedaba un globito a medio
      // borrar al sacar el mouse.
      transitionDuration: 0,
      backgroundColor: c['--superficie'],
      borderColor: c['--borde'],
      borderWidth: 1,
      padding: [10, 12],
      textStyle: { fontFamily: FUENTE, color: c['--texto'], fontSize: 13 },
      extraCssText: 'border-radius: 10px; box-shadow: 0 8px 28px rgba(0,0,0,.14);',
    },
  };
}

/** El eje de las horas; con `conMarcas`, deja lugar entre la línea y las horas para las marcas de las alertas. */
function ejeTiempo(c, desde, hasta, conMarcas = false) {
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
      margin: conMarcas ? 17 : 8,
      // Con segundos en los rangos cortos; en los de más de medio día, la fecha en cada medianoche.
      formatter: (valor) => {
        const fecha = new Date(valor);
        const medianoche = fecha.getHours() === 0 && fecha.getMinutes() === 0;
        if (largo > 3 * 86400e3) return medianoche ? dia(valor) : '';
        if (largo > 12 * 3600e3 && medianoche) return dia(valor);
        return largo <= 10 * 60e3 ? horaConSegundos(valor) : hora(valor);
      },
    },
    axisPointer: { show: true, type: 'line', lineStyle: { color: c['--texto-3'], width: 1, type: 'solid' }, label: { show: false } },
  };
}

/** Un color de la paleta con transparencia, para el tramo que se marca al arrastrar. */
function conAlfa(color, alfa) {
  const hex = /^#([0-9a-f]{6})$/i.exec(color);
  if (!hex) return color;
  const n = parseInt(hex[1], 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alfa})`;
}

/** El pincel de los gráficos de tiempo: arrastrar marca un tramo y el tablero se acerca a él (tiempo.js). */
function pincel(c) {
  return {
    xAxisIndex: 0,
    brushType: 'lineX',
    brushMode: 'single',
    transformable: false,
    brushStyle: { color: conAlfa(c['--verde'], 0.14), borderColor: c['--verde'], borderWidth: 1 },
    outOfBrush: { colorAlpha: 1 },
    throttleType: 'debounce',
    throttleDelay: 0,
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

/**
 * Las alertas, como las marcas de Grafana pero sin la línea vertical: un triángulo chico debajo del eje, del
 * color de la severidad, en el momento en que empezó cada una. Las más graves quedan arriba. No entra en el
 * globito del eje: el suyo lo arma vivo.js, que también abre la alerta al tocar la marca (el de ECharts se
 * repetiría en los tres gráficos de tiempo, que comparten el cursor).
 * marcas: [{id, t, severidad}]
 */
function serieMarcas(c, marcas) {
  const orden = marcas.slice().sort((a, b) => (PESO[a.severidad] || 0) - (PESO[b.severidad] || 0) || a.t - b.t || (a.id < b.id ? -1 : 1));
  return {
    id: 'marcas',
    name: 'Alertas',
    type: 'custom',
    z: 5,
    clip: false,
    animation: false,
    tooltip: { show: false },
    data: orden.map((m) => ({ value: [m.t, 0], id: m.id })),
    encode: { x: 0, y: 1 },
    renderItem: (params, api) => {
      const m = orden[params.dataIndex];
      const [x, y] = api.coord([api.value(0), 0]);
      return {
        type: 'group',
        children: [
          // Un recuadro invisible más grande que la marca, para que sea fácil de apuntar.
          { type: 'rect', shape: { x: x - 7, y: y + 1, width: 14, height: 12 }, style: { fill: 'rgba(0,0,0,0)' } },
          { type: 'polygon', shape: { points: [[x, y + 2.5], [x - 5.5, y + 11], [x + 5.5, y + 11]] }, style: { fill: c[`--${m.severidad}`], stroke: c['--superficie'], lineWidth: 1 } },
        ],
      };
    },
  };
}

/**
 * Las conexiones de cada barra por servicio: una línea por servicio y una sola escala, como un panel de
 * Grafana; la leyenda en tabla (vivo.js) deja ver uno solo o sumar y sacar servicios. El globito da cada
 * servicio que se ve, el total del tramo y las alertas que empezaron en él; debajo del eje, las marcas.
 * datos: { tiempos (ms), porGrupo: {id: [del tramo]}, conexiones: [del tramo], paso (s), desde, hasta,
 *          grupos: [los ids que se ven], marcas: [como en serieMarcas] }
 */
export function opcionesServicios(c, datos) {
  const grupos = GRUPOS.filter((g) => datos.grupos.includes(g.id));
  const marcasEnTramo = new Map();
  for (const m of datos.marcas) {
    const i = Math.floor((m.t - datos.desde) / (datos.paso * 1000));
    if (!marcasEnTramo.has(i)) marcasEnTramo.set(i, []);
    marcasEnTramo.get(i).push(m);
  }
  return {
    ...base(c),
    grid: { left: 8, right: 16, top: 16, bottom: 6, containLabel: true },
    brush: pincel(c),
    xAxis: ejeTiempo(c, datos.desde, datos.hasta, true),
    yAxis: { ...ejeValor(c, ejeNumero, grupos.flatMap((g) => datos.porGrupo[g.id]), 1), minInterval: 1 },
    tooltip: {
      ...base(c).tooltip,
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: c['--texto-3'] } },
      formatter: (params) => {
        if (!params.length) return '';
        const i = Math.round((params[0].axisValue - datos.desde) / (datos.paso * 1000));
        if (i < 0 || i >= datos.tiempos.length) return '';
        let html = `<div class="globito"><div class="globito-titulo">${escapar(tramoDe(datos.tiempos[i], datos.paso))}</div>`;
        for (const g of grupos) html += filaGlobito(c[g.color], numero(datos.porGrupo[g.id][i]), g.nombre);
        const total = datos.conexiones[i];
        html += `<div class="globito-separador"></div><div class="globito-fila"><span></span><b>${escapar(numero(total))}</b><em>${total === 1 ? 'conexión en total' : 'conexiones en total'}</em></div>`;
        // Las alertas iguales del mismo tramo (el mismo tipo y el mismo par) van en una sola fila, con ×N.
        const iguales = new Map();
        for (const m of marcasEnTramo.get(i) || []) {
          const clave = `${m.severidad}|${m.tipo}|${m.par}`;
          if (iguales.has(clave)) iguales.get(clave).veces += 1;
          else iguales.set(clave, { m, veces: 1 });
        }
        if (iguales.size) html += '<div class="globito-separador"></div>';
        for (const { m, veces } of iguales.values()) {
          const texto = `${m.tipo} · ${m.par}`;
          html += filaGlobito(c[`--${m.severidad}`], m.nivel, veces > 1 ? `${texto} ×${veces}` : texto, true);
        }
        return `${html}</div>`;
      },
    },
    series: [
      serieMarcas(c, datos.marcas),
      ...grupos.map((g) => ({
        id: `servicio-${g.id}`,
        name: g.nombre,
        type: 'line',
        showSymbol: false,
        symbolSize: 8,
        lineStyle: { width: 2, color: c[g.color], cap: 'round', join: 'round' },
        itemStyle: { color: c[g.color], borderColor: c['--superficie'], borderWidth: 2 },
        data: datos.tiempos.map((t, i) => [t, datos.porGrupo[g.id][i]]),
      })),
    ],
  };
}

/**
 * Las alertas que empezaron en cada barra, apiladas por severidad con las altas abajo, con el mismo eje y el
 * mismo cursor que los gráficos de tráfico, como un panel de barras de Grafana. Las marcas se tapan cuando
 * caen varias juntas; acá se cuentan. El globito da cuántas hay de cada severidad y de cada tipo de ataque;
 * tocar una barra abre esas alertas en la lista (vivo.js).
 * datos: { tiempos (ms), paso (s), desde, hasta, porSeveridad: {alta: [], media: [], baja: []},
 *          porTipo: [[{ tipo, n }] de cada barra, de mayor a menor] }
 */
export function opcionesAlertasTiempo(c, datos) {
  const totales = datos.tiempos.map((_, i) => SEVERIDADES.reduce((suma, s) => suma + datos.porSeveridad[s.id][i], 0));
  return {
    ...base(c),
    grid: { left: 8, right: 16, top: 12, bottom: 6, containLabel: true },
    brush: pincel(c),
    xAxis: ejeTiempo(c, datos.desde, datos.hasta),
    yAxis: { ...ejeValor(c, ejeNumero, totales, 1), minInterval: 1 },
    tooltip: {
      ...base(c).tooltip,
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: c['--texto-3'] } },
      formatter: (params) => {
        if (!params.length) return '';
        const i = Math.floor((params[0].axisValue - datos.tiempos[0]) / (datos.paso * 1000));
        if (i < 0 || i >= datos.tiempos.length) return '';
        const html = `<div class="globito"><div class="globito-titulo">${escapar(tramoDe(datos.tiempos[i], datos.paso))}</div>`;
        if (!totales[i]) return `${html}<div class="globito-nota">Sin alertas</div></div>`;
        const filas = SEVERIDADES.filter((s) => datos.porSeveridad[s.id][i]).map((s) => {
          const n = datos.porSeveridad[s.id][i];
          return filaGlobito(c[s.color], numero(n), n === 1 ? s.nombre.toLowerCase() : `${s.nombre.toLowerCase()}s`, true);
        });
        const tipos = datos.porTipo[i].map((x) => `<div class="globito-fila"><span></span><b>${escapar(numero(x.n))}</b><em>${escapar(x.tipo)}</em></div>`);
        return `${html}${filas.join('')}<div class="globito-separador"></div>${tipos.join('')}<div class="globito-nota">Tocá la barra para verlas en la lista.</div></div>`;
      },
    },
    // Sin alertas, la barra queda vacía (null). Los segmentos van sin borde: en una semana cada barra mide
    // pocos píxeles y el borde se comería el color.
    series: SEVERIDADES.map((s) => ({
      id: `alertas-${s.id}`,
      name: s.nombre,
      type: 'bar',
      stack: 'alertas',
      barMaxWidth: 24,
      barMinWidth: 2,
      itemStyle: { color: c[s.color], borderRadius: 1 },
      emphasis: { disabled: true },
      // Cada barra va en el medio de su tramo: las de 3 horas cubren de 0 a 3, no empiezan en 0.
      data: datos.tiempos.map((t, i) => [t + datos.paso * 500, datos.porSeveridad[s.id][i] || null]),
    })),
  };
}

/**
 * Un gráfico chico para las piezas del estado del sistema: sin ejes ni grilla, solo la línea de base, y
 * el valor de cada punto en el globito. Con `paso`, cada barra es un tramo de ese largo. Con `ejes` (el
 * panel en grande), lleva las horas abajo y los valores a la izquierda.
 * datos: { desde, hasta (ms), puntos: [[t (ms), valor o null]] }
 */
export function opcionesMini(c, datos, { tipo, color, nombre, formato = (v) => numero(v), paso = 0, maximo, ejes = false } = {}) {
  const serie = tipo === 'linea'
    ? { type: 'line', showSymbol: false, symbolSize: 5, lineStyle: { width: 1.5, color }, itemStyle: { color }, areaStyle: { color, opacity: 0.12 }, data: datos.puntos }
    : { type: 'bar', barMaxWidth: ejes ? 12 : 5, barMinWidth: 1, itemStyle: { color, borderRadius: [2, 2, 0, 0] }, data: datos.puntos, emphasis: { disabled: true } };
  return {
    ...base(c),
    animation: false,
    grid: ejes ? { left: 8, right: 12, top: 10, bottom: 4, containLabel: true } : { left: 0, right: 0, top: 4, bottom: 1 },
    xAxis: ejes ? ejeTiempo(c, datos.desde, datos.hasta) : {
      type: 'time',
      min: datos.desde,
      max: datos.hasta,
      axisLine: { show: true, lineStyle: { color: c['--eje'] } },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { show: false },
    },
    yAxis: ejes ? { ...ejeValor(c, (v) => formato(v), null), max: maximo, minInterval: tipo === 'linea' ? 0 : 1 } : { type: 'value', min: 0, max: maximo, show: false },
    tooltip: {
      ...base(c).tooltip,
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: c['--texto-3'] } },
      formatter: (params) => {
        const punto = params[0];
        if (!punto) return '';
        const [t, valor] = punto.value;
        const titulo = paso ? `${hora(t)} a ${hora(t + paso * 1000)}` : horaConSegundos(t);
        return `<div class="globito"><div class="globito-titulo">${escapar(titulo)}</div>${filaGlobito(color, valor == null ? 'Sin respuesta' : formato(valor), valor == null ? '' : nombre)}</div>`;
      },
    },
    series: [{ name: nombre, ...serie }],
  };
}

/** Un solo indicador en el tiempo (tráfico o sin respuesta), en el verde de la marca: sin leyenda, el título
    lo nombra. Con `marcas`, lleva debajo del eje las de las alertas, como el de las conexiones por servicio. */
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
  const marcas = datos.marcas || null;
  return {
    ...base(c),
    grid: { left: 8, right: 16, top: 16, bottom: 6, containLabel: true },
    brush: pincel(c),
    xAxis: ejeTiempo(c, datos.desde, datos.hasta, Boolean(marcas)),
    // Las barras cuentan conexiones: el eje va de a números enteros.
    yAxis: tipo === 'linea' ? ejeValor(c, formato, datos.valores, 1000) : { ...ejeValor(c, ejeNumero, datos.valores, 1), minInterval: 1 },
    tooltip: {
      ...base(c).tooltip,
      trigger: 'axis',
      formatter: (params) => {
        if (!params.length) return '';
        const i = Math.round((params[0].axisValue - datos.desde) / (datos.paso * 1000));
        if (i < 0 || i >= datos.tiempos.length) return '';
        return `<div class="globito"><div class="globito-titulo">${escapar(tramoDe(datos.tiempos[i], datos.paso))}</div>${filaGlobito(color, formato(datos.valores[i]), nombre)}</div>`;
      },
    },
    series: [...(marcas ? [serieMarcas(c, marcas)] : []), { id: 'valores', name: nombre, ...serie }],
  };
}

export const formatoBits = (v) => bitsPorSegundo(v);

/**
 * Comunicaciones entre áreas como el «Node graph» de Grafana, que se parece al de Neo4j: un círculo por
 * equipo, del color de su área y más grande con más conexiones, con un anillo del color de su alerta más
 * grave; y una flecha del origen al destino, más gruesa con más conexiones y del color de la alerta del par.
 * Las posiciones vienen de mapa.js, de 0 a 1 del recuadro; dos anclas invisibles en las esquinas hacen que
 * ECharts no las estire. Con un equipo o una flecha elegidos, lo que no tiene que ver queda apagado y las
 * flechas de lo elegido dicen sus servicios; sin nada elegido, eso pasa al pasar el mouse.
 * datos: { nodos: [{id, nombre, ip, area, conexiones, severidad, nivel, alertaTexto}],
 *          aristas: [{origen, destino, origenNombre, destinoNombre, conexiones, fallidas, servicios, severidad, nivel, alertaTexto, etiqueta}],
 *          posiciones: Map(id → [u, v]), colorDe: (área) → variable del color, ancho, alto,
 *          elegido: {tipo: 'nodo', id} o {tipo: 'arista', clave} o null, encendidos: Set de ids o null }
 */
export function opcionesGrafo(c, datos) {
  const { ancho, alto, elegido, encendidos } = datos;
  const maxNodo = Math.max(1, ...datos.nodos.map((n) => n.conexiones));
  const maxArista = Math.max(1, ...datos.aristas.map((a) => a.conexiones));
  const limpio = (texto) => String(texto).replace(/[{}|]/g, '');
  const nodoElegido = elegido && elegido.tipo === 'nodo' ? elegido.id : null;
  const ancla = (id, x, y) => ({ id, name: '', x, y, symbolSize: 0, draggable: false, label: { show: false }, tooltip: { show: false }, emphasis: { disabled: true } });
  const nodos = [ancla('ancla:1', 0, 0), ancla('ancla:2', ancho, alto)];
  for (const n of datos.nodos) {
    const [u, v] = datos.posiciones.get(n.id) || [0.5, 0.5];
    const apagado = Boolean(encendidos) && !encendidos.has(n.id);
    const alfa = apagado ? 0.3 : 1;
    nodos.push({
      id: n.id,
      name: n.nombre,
      x: u * ancho,
      y: v * alto,
      symbol: 'circle',
      symbolSize: 22 + 16 * Math.sqrt(n.conexiones / maxNodo),
      value: n.conexiones,
      itemStyle: {
        color: c[datos.colorDe(n.area)],
        borderColor: n.severidad ? c[`--${n.severidad}`] : c['--superficie'],
        borderWidth: n.severidad ? 3.5 : 2,
        opacity: apagado ? 0.2 : 1,
        // El halo separa la alerta del color del área (un servidor rosa con alerta roja); el elegido, en verde.
        shadowBlur: n.id === nodoElegido ? 18 : n.severidad ? 12 : 0,
        shadowColor: n.id === nodoElegido ? conAlfa(c['--verde'], 0.7) : n.severidad ? conAlfa(c[`--${n.severidad}`], 0.8) : 'transparent',
      },
      label: {
        show: true,
        position: 'bottom',
        distance: 5,
        formatter: n.nombre !== n.ip ? `{n|${limpio(n.nombre)}}\n{ip|${limpio(n.ip)}}` : `{n|${limpio(n.ip)}}`,
        backgroundColor: conAlfa(c['--superficie'], apagado ? 0 : 0.8),
        padding: [1, 3],
        borderRadius: 4,
        rich: {
          n: { color: conAlfa(c['--texto'], alfa), fontSize: 12.5, fontWeight: 600, fontFamily: FUENTE, lineHeight: 16, align: 'center' },
          ip: { color: conAlfa(c['--texto-2'], alfa), fontSize: 11.5, fontFamily: FUENTE, lineHeight: 15, align: 'center' },
        },
      },
      datos: n,
    });
  }
  const aristas = datos.aristas.map((a) => {
    const encendida = !elegido || (nodoElegido ? a.origen === nodoElegido || a.destino === nodoElegido : elegido.clave === `${a.origen}>${a.destino}`);
    const grosor = 1.2 + 4 * (Math.log1p(a.conexiones) / Math.log1p(maxArista));
    return {
      source: a.origen,
      target: a.destino,
      value: a.conexiones,
      symbolSize: [0, 7 + grosor * 1.5],
      // La alerta baja va punteada: su verde no se confunde con el área RRHH.
      lineStyle: { width: grosor, color: a.severidad ? c[`--${a.severidad}`] : c['--arista'], type: a.severidad === 'baja' ? 'dashed' : 'solid', opacity: encendida ? (a.severidad ? 0.95 : 0.75) : 0.07 },
      label: { show: Boolean(elegido) && encendida, formatter: limpio(a.etiqueta) },
      datos: a,
    };
  });
  return {
    ...base(c),
    tooltip: {
      ...base(c).tooltip,
      trigger: 'item',
      formatter: (p) => {
        if (p.dataType === 'edge') {
          const a = p.data.datos;
          if (!a) return '';
          let html = `<div class="globito"><div class="globito-titulo">${escapar(a.origenNombre)} → ${escapar(a.destinoNombre)}</div>`;
          html += `<div class="globito-fila"><span></span><b>${escapar(numero(a.conexiones))}</b><em>conexiones</em></div>`;
          html += `<div class="globito-fila"><span></span><b>${escapar(numero(a.fallidas))}</b><em>sin respuesta</em></div>`;
          html += `<div class="globito-nota">${escapar(a.servicios.map((s) => s.toUpperCase()).slice(0, 4).join(' · '))}</div>`;
          if (a.severidad) html += `<div class="globito-separador"></div>${filaGlobito(c[`--${a.severidad}`], a.nivel, a.alertaTexto, true)}`;
          return `${html}</div>`;
        }
        const n = p.data.datos;
        if (!n) return '';
        let html = `<div class="globito"><div class="globito-titulo">${escapar(n.nombre)}</div><div class="globito-nota">${escapar(n.ip)} · ${escapar(n.area)}</div>`;
        html += `<div class="globito-fila"><span></span><b>${escapar(numero(n.conexiones))}</b><em>conexiones</em></div>`;
        if (n.severidad) html += `<div class="globito-separador"></div>${filaGlobito(c[`--${n.severidad}`], n.nivel, n.alertaTexto, true)}`;
        return `${html}</div>`;
      },
    },
    series: [{
      id: 'grafo',
      type: 'graph',
      layout: 'none',
      left: 0,
      right: 0,
      top: 0,
      bottom: 0,
      roam: true,
      scaleLimit: { min: 0.5, max: 3 },
      nodeScaleRatio: 0.5,
      draggable: true,
      autoCurveness: true,
      edgeSymbol: ['none', 'arrow'],
      edgeLabel: { fontSize: 11.5, fontFamily: FUENTE, color: c['--texto'], backgroundColor: conAlfa(c['--superficie'], 0.9), padding: [1, 4], borderRadius: 4 },
      data: nodos,
      links: aristas,
      emphasis: elegido ? { focus: 'none', scale: 1.06 } : { focus: 'adjacency', scale: 1.08, edgeLabel: { show: true } },
      blur: { itemStyle: { opacity: 0.2 }, lineStyle: { opacity: 0.07 }, label: { opacity: 0.3 } },
      animationDurationUpdate: 300,
    }],
  };
}
