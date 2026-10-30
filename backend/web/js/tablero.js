// El tablero web de Zonda (decisión 49): la vista de escritorio de la red, al lado de la app. Usa la misma
// API y el mismo acceso que un teléfono: se entra con un código de pareo de un solo uso y el navegador
// queda como un dispositivo más, con su perfil y su vencimiento. Solo muestra: revisar sigue en la app.

import {
  api, ErrorApi, leerAcceso, guardarAcceso, borrarAcceso,
  numero, tasa, porcentaje, bitsPorSegundo, bytes, hora, horaConSegundos, fechaHora, fechaHoraSegundos, haceCuanto, dia, diaLargo,
  el, vaciar, icono,
} from './util.js';
import {
  GRUPOS, grupoDe, SEVERIDADES, masGrave, colores,
  opcionesConexiones, opcionesSerieSimple, formatoBits, opcionesMapa, opcionesPorDia, opcionesBarrasH,
} from './graficos.js';

const VENTANAS = {
  '15m': { segundos: 900, paso: 5, cada: 5000, nota: 'Barras de 5 segundos · se actualiza cada 5 segundos', texto: 'estos 15 minutos' },
  '1h': { segundos: 3600, paso: 15, cada: 5000, nota: 'Barras de 15 segundos · se actualiza cada 5 segundos', texto: 'la última hora' },
  '24h': { segundos: 86400, paso: 300, cada: 30000, nota: 'Barras de 5 minutos · se actualiza cada 30 segundos', texto: 'las últimas 24 horas' },
  '7d': { segundos: 604800, paso: 3600, cada: 60000, nota: 'Barras de una hora · se actualiza cada minuto', texto: 'los últimos 7 días' },
};

const TIPOS = {
  fuerza_bruta: 'Fuerza bruta',
  escaneo: 'Escaneo de puertos',
  ddos: 'Denegación de servicio',
  probable_falsa_alarma: 'Probable falsa alarma',
  sin_tipificar: 'Sin tipo identificado',
  servicio_no_autorizado: 'Servicio no autorizado',
  volumen_inusual: 'Volumen inusual',
};
const ORDEN_TIPOS = ['fuerza_bruta', 'escaneo', 'ddos', 'sin_tipificar', 'probable_falsa_alarma', 'servicio_no_autorizado', 'volumen_inusual'];
const ESTADOS = { sin_revisar: 'Sin revisar', ataque_real: 'Ataque real', falso_positivo: 'Falso positivo' };
const NIVEL = { alta: 'Alta', media: 'Media', baja: 'Baja' };
const MOTIVOS = {
  escalado_al_proveedor: 'Escalado al proveedor', bloqueado_manualmente: 'Bloqueado manualmente', contrasena_cambiada: 'Contraseña cambiada',
  investigando: 'Investigando', tarea_programada: 'Tarea programada', backup: 'Backup', equipo_nuevo: 'Equipo nuevo',
  usuario_autorizado: 'Usuario autorizado', otro: 'Otro',
};
const METODOS = [
  { id: 'ia', nombre: 'Modelo de IA', corto: 'IA', icono: 'ia' },
  { id: 'umbrales', nombre: 'Umbrales', corto: 'Umbrales', icono: 'umbral' },
  { id: 'reglas', nombre: 'Reglas', corto: 'Reglas', icono: 'regla' },
];
const CLAVE_TEMA = 'zonda.tablero.tema';

const e = {
  acceso: null,
  vista: 'vivo',
  ventana: '15m',
  dias: 7,
  filtroSeveridad: new Set(['alta', 'media', 'baja']),
  filtroEstado: new Set(['sin_revisar', 'ataque_real', 'falso_positivo']),
  alertas: new Map(),
  cursor: null,
  sincronizada: false,
  nuevas: new Set(),
  salud: null,
  conteo: null,
  trafico: null,
  ahora: null,
  pares: null,
  desfase: 0,
  ultimaRespuesta: 0,
  servidorCaido: false,
  graficos: {},
  tablas: {},
  tablasVisibles: new Set(),
  limiteTabla: 50,
  areas: [],
  detalleAbierto: null,
  ticker: null,
  tareas: {},
};

const $ = (id) => document.getElementById(id);
const ahoraServidor = () => Date.now() + e.desfase;

// ---------------------------------------------------------------- Tema

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

// ---------------------------------------------------------------- Ingreso

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
    const salida = await api('/pareo', { metodo: 'POST', cuerpo: { codigo, nombreDispositivo: 'Tablero web' } });
    e.acceso = { token: salida.token, vence: salida.vence, perfil: salida.perfil, persona: salida.persona, red: salida.red };
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

// ---------------------------------------------------------------- Consultas periódicas

async function pedir(ruta) {
  try {
    const datos = await api(ruta, { token: e.acceso.token });
    e.ultimaRespuesta = Date.now();
    e.servidorCaido = false;
    return datos;
  } catch (falla) {
    if (falla instanceof ErrorApi && falla.estado === 401) {
      terminarSesion('El acceso de este navegador venció o lo quitaron. Pedí un código nuevo.');
      throw falla;
    }
    if (falla.estado === 0) e.servidorCaido = true;
    throw falla;
  }
}

const TAREAS = {
  salud: { cada: () => 5000, correr: actualizarSalud },
  alertas: { cada: () => 5000, correr: sincronizarAlertas },
  ahora: { cada: () => 5000, correr: actualizarAhora },
  trafico: { cada: () => VENTANAS[e.ventana].cada, correr: actualizarTrafico },
  pares: { cada: () => Math.max(10000, VENTANAS[e.ventana].cada), correr: actualizarPares },
};

function detenerTicker() {
  if (e.ticker) clearInterval(e.ticker);
  e.ticker = null;
}

function arrancarTicker() {
  detenerTicker();
  e.ticker = setInterval(tic, 1000);
  tic();
}

function tic() {
  if (!e.acceso) return;
  const ahora = Date.now();
  for (const [nombre, tarea] of Object.entries(TAREAS)) {
    const estado = e.tareas[nombre] || (e.tareas[nombre] = { ultima: 0, corriendo: false });
    if (estado.corriendo || ahora - estado.ultima < tarea.cada()) continue;
    estado.corriendo = true;
    estado.ultima = ahora;
    tarea.correr().catch(() => { /* se muestra en el indicador y se reintenta en la vuelta siguiente */ }).finally(() => { estado.corriendo = false; });
  }
  pintarEnVivo();
}

function forzar(...nombres) {
  for (const nombre of nombres) if (e.tareas[nombre]) e.tareas[nombre].ultima = 0;
  tic();
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

// ---------------------------------------------------------------- Estado del sistema y sin revisar

async function actualizarSalud() {
  const salud = await pedir('/salud');
  e.salud = salud;
  if (salud.hora) e.desfase = Date.parse(salud.hora) - Date.now();
  e.conteo = await pedir('/estado');
  pintarEstado();
  pintarSinRevisar();
}

const PIEZAS = {
  funciona: { texto: 'Funciona', color: 'var(--baja)' },
  parcial: { texto: 'Viendo parcialmente', color: 'var(--media)' },
  detenido: { texto: 'Detenido', color: 'var(--alta)' },
  sin_datos: { texto: 'Sin datos', color: 'var(--alta)' },
  no_responde: { texto: 'No responde', color: 'var(--alta)' },
};

function pintarEstado() {
  const sistema = e.salud && e.salud.sistema;
  const servidor = e.servidorCaido ? 'no_responde' : 'funciona';
  const sensor = sistema ? sistema.sensor : 'sin_datos';
  const analisis = sistema ? sistema.analisis : 'sin_datos';
  const piezas = [
    { nombre: 'Sensor de red', estado: sensor, detalle: detalleSensor(sistema) },
    { nombre: 'Análisis en vivo', estado: analisis, detalle: sistema && sistema.segundosDesdeUltimaCorrida != null ? `Última revisión hace ${duracion(sistema.segundosDesdeUltimaCorrida)}` : 'Sin latido' },
    { nombre: 'Servidor de alertas', estado: servidor, detalle: servidor === 'funciona' ? `Versión ${e.salud ? e.salud.version : ''}` : 'No contesta' },
  ];
  let titulo; let bajada; let color; let simbolo;
  const caidas = piezas.filter((p) => !['funciona', 'parcial'].includes(p.estado));
  if (!caidas.length && sensor === 'funciona') {
    titulo = 'Todo funciona'; bajada = 'El sensor escucha y el análisis revisa el tráfico'; color = 'var(--baja)'; simbolo = 'check';
  } else if (!caidas.length) {
    titulo = 'El sensor ve parcialmente'; bajada = 'Puede haber tráfico que no ve'; color = 'var(--media)'; simbolo = 'alerta';
  } else {
    titulo = caidas.length === 1 ? `Revisar: ${caidas[0].nombre.toLowerCase()}` : 'Revisar el sistema';
    bajada = caidas.map((p) => `${p.nombre}: ${PIEZAS[p.estado].texto.toLowerCase()}`).join(' · ');
    color = 'var(--alta)'; simbolo = 'alerta';
  }
  vaciar($('estado-principal'),
    el('div', { class: 'icono', style: `background:${color}` }, icono(simbolo)),
    el('div', {}, el('strong', {}, titulo), el('span', {}, bajada)));
  vaciar($('estado-piezas'), piezas.map((p) => el('div', { class: 'pieza' },
    el('div', { class: 'pieza-nombre' }, el('span', { class: 'punto', style: `background:${PIEZAS[p.estado].color}` }), p.nombre.split(' ')[0]),
    el('div', { class: 'pieza-estado', title: `${PIEZAS[p.estado].texto} · ${p.detalle}` }, `${PIEZAS[p.estado].texto} · ${p.detalle}`))));
}

function detalleSensor(sistema) {
  if (!sistema || sistema.sensor === 'sin_datos') return 'Sin datos del sensor';
  if (sistema.sensor === 'detenido') return 'No escucha la red';
  if (sistema.perdidaCaptura != null && sistema.perdidaCaptura > 0) return `Pierde el ${numero(sistema.perdidaCaptura, 1)} % del tráfico`;
  return 'Escuchando la red';
}

function duracion(segundos) {
  if (segundos < 60) return `${segundos} s`;
  if (segundos < 3600) return `${Math.round(segundos / 60)} min`;
  return `${Math.round(segundos / 3600)} h`;
}

function pintarSinRevisar() {
  const conteo = (e.conteo && e.conteo.sinRevisar) || { alta: 0, media: 0, baja: 0 };
  vaciar($('sin-revisar'), SEVERIDADES.map((s) => el('div', { class: 'contador' },
    el('div', { class: 'contador-titulo' }, el('span', { class: 'punto', style: `background:var(${s.color})` }), s.nombre),
    el('div', { class: 'contador-numero' }, numero(conteo[s.id])))));
}

// ---------------------------------------------------------------- Tráfico

/** La serie que mandó el servidor, en una grilla pareja de tramos (los que faltan son ceros). */
function enGrilla(serie) {
  const paso = serie.paso;
  const n = Math.max(0, Math.round((serie.hasta - serie.desde) / paso));
  const indice = (t) => Math.round((t - serie.desde) / paso);
  const tiempos = Array.from({ length: n }, (_, i) => (serie.desde + i * paso) * 1000);
  const ceros = () => new Array(n).fill(0);
  const conexiones = ceros(); const fallidas = ceros(); const bytesT = ceros(); const paquetes = ceros();
  for (const p of serie.total) {
    const i = indice(p.t);
    if (i < 0 || i >= n) continue;
    conexiones[i] = p.conexiones; fallidas[i] = p.fallidas; bytesT[i] = p.bytes; paquetes[i] = p.paquetes;
  }
  const porGrupo = Object.fromEntries(GRUPOS.map((g) => [g.id, ceros()]));
  for (const [servicio, puntos] of Object.entries(serie.servicios)) {
    const grupo = porGrupo[grupoDe(servicio)];
    for (const p of puntos) {
      const i = indice(p.t);
      if (i >= 0 && i < n) grupo[i] += p.conexiones;
    }
  }
  return { paso, desde: serie.desde * 1000, hasta: serie.hasta * 1000, tiempos, conexiones, fallidas, bytes: bytesT, paquetes, porGrupo };
}

async function actualizarTrafico() {
  const ventana = VENTANAS[e.ventana];
  const desde = Math.floor((ahoraServidor() / 1000 - ventana.segundos) / ventana.paso) * ventana.paso;
  const pedido = e.ventana;
  const serie = await pedir(`/trafico?desde=${desde}&paso=${ventana.paso}`);
  if (pedido !== e.ventana) return;
  e.trafico = { ventana: pedido, ...enGrilla(serie), ultimoTramo: serie.ultimoTramo };
  pintarTrafico();
}

async function actualizarAhora() {
  const desde = Math.floor(ahoraServidor() / 1000 / 5) * 5 - 45;
  e.ahora = enGrilla(await pedir(`/trafico?desde=${desde}&paso=5`));
  pintarAhora();
}

function pintarAhora() {
  const g = e.ahora;
  if (!g) return;
  // Los seis tramos de 5 segundos antes del que está en curso: Zeek escribe cada conexión cuando termina.
  const fin = Math.max(0, Math.floor((ahoraServidor() - g.desde) / 5000));
  const desdeI = Math.max(0, fin - 6);
  let conexiones = 0; let bytesT = 0;
  for (let i = desdeI; i < fin && i < g.tiempos.length; i += 1) { conexiones += g.conexiones[i]; bytesT += g.bytes[i]; }
  const segundos = Math.max(1, (fin - desdeI) * 5);
  $('conexiones-ahora').textContent = tasa(conexiones / segundos);
  $('trafico-ahora').textContent = bitsPorSegundo((bytesT * 8) / segundos);
}

function alertasEnVentana(desdeMs, hastaMs) {
  const lista = [];
  for (const a of e.alertas.values()) {
    const t = Date.parse(a.inicio);
    if (t >= desdeMs && t < hastaMs) lista.push({ ...a, t });
  }
  return lista;
}

function pintarTrafico() {
  const g = e.trafico;
  if (!g) return;
  const c = colores();
  const ventana = VENTANAS[g.ventana];
  $('nota-ventana').textContent = ventana.nota;

  // Alertas de la ventana: las más graves primero, hasta 40 líneas.
  const alertas = alertasEnVentana(g.desde, g.hasta)
    .sort((a, b) => (SEVERIDADES.findIndex((s) => s.id === a.severidad) - SEVERIDADES.findIndex((s) => s.id === b.severidad)) || b.t - a.t)
    .slice(0, 40)
    .map((a) => ({ t: a.t, severidad: a.severidad, nivel: NIVEL[a.severidad], corto: `${NIVEL[a.severidad]} · ${tipoCorto(a)}`, texto: `${tipoCorto(a)} · ${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')}` }));

  const porSegundo = (lista) => lista.map((v) => v / g.paso);
  const porGrupo = Object.fromEntries(Object.entries(g.porGrupo).map(([id, v]) => [id, porSegundo(v)]));
  dibujar('conexiones', opcionesConexiones(c, { tiempos: g.tiempos, porGrupo, paso: g.paso, desde: g.desde, hasta: g.hasta, alertas }));
  dibujar('trafico', opcionesSerieSimple(c, { tiempos: g.tiempos, valores: g.bytes.map((b) => (b * 8) / g.paso), paso: g.paso, desde: g.desde, hasta: g.hasta }, { tipo: 'linea', formato: formatoBits, nombre: 'bits por segundo' }));
  dibujar('fallidas', opcionesSerieSimple(c, { tiempos: g.tiempos, valores: porSegundo(g.fallidas), paso: g.paso, desde: g.desde, hasta: g.hasta }, { tipo: 'barras', formato: tasa, nombre: 'sin respuesta por segundo' }));

  const total = g.conexiones.reduce((a, b) => a + b, 0);
  const fallidas = g.fallidas.reduce((a, b) => a + b, 0);
  const bytesTotal = g.bytes.reduce((a, b) => a + b, 0);
  $('sin-respuesta').textContent = numero(fallidas);
  $('sin-respuesta-detalle').textContent = total ? `${porcentaje(fallidas, total)} de ${numero(total)} conexiones en ${ventana.texto}` : `Ninguna conexión en ${ventana.texto}`;
  $('trafico-ahora-detalle').textContent = `${bytes(bytesTotal)} en ${ventana.texto}`;
  $('conexiones-ahora-detalle').textContent = `Promedio de los últimos 30 segundos. ${numero(total)} conexiones en ${ventana.texto}.`;

  const vacio = $('vacio-conexiones');
  vacio.hidden = total > 0;
  vacio.textContent = g.ultimoTramo == null ? 'Todavía no llegó tráfico del sensor. El sensor lo manda cada 5 segundos.'
    : `No hubo conexiones en ${ventana.texto}. Lo último que vio el sensor fue a las ${fechaHora(g.ultimoTramo * 1000)}.`;

  // Las tablas de los tres gráficos.
  const filas = [];
  for (let i = g.tiempos.length - 1; i >= 0; i -= 1) {
    if (!g.conexiones[i] && !g.bytes[i]) continue;
    filas.push(i);
  }
  e.tablas.conexiones = {
    columnas: ['Desde', ...GRUPOS.map((x) => x.nombre), 'Total por segundo'],
    numericas: GRUPOS.length + 1,
    filas: filas.filter((i) => g.conexiones[i]).map((i) => [horaTramo(g.tiempos[i], g.paso), ...GRUPOS.map((x) => tasa(porGrupo[x.id][i])), tasa(g.conexiones[i] / g.paso)]),
  };
  e.tablas.trafico = { columnas: ['Desde', 'Bits por segundo', 'Bytes'], numericas: 2, filas: filas.map((i) => [horaTramo(g.tiempos[i], g.paso), bitsPorSegundo((g.bytes[i] * 8) / g.paso), bytes(g.bytes[i])]) };
  e.tablas.fallidas = { columnas: ['Desde', 'Sin respuesta por segundo', 'Sin respuesta'], numericas: 2, filas: filas.filter((i) => g.fallidas[i]).map((i) => [horaTramo(g.tiempos[i], g.paso), tasa(g.fallidas[i] / g.paso), numero(g.fallidas[i])]) };
  for (const nombre of ['conexiones', 'trafico', 'fallidas']) if (e.tablasVisibles.has(nombre)) pintarTabla(nombre);
}

function horaTramo(ms, paso) {
  return paso >= 60 ? fechaHora(ms) : horaConSegundos(ms);
}

// ---------------------------------------------------------------- Mapa

function ipANumero(ip) {
  const partes = ip.split('.').map(Number);
  if (partes.length !== 4 || partes.some((p) => Number.isNaN(p))) return null;
  return ((partes[0] << 24) >>> 0) + (partes[1] << 16) + (partes[2] << 8) + partes[3];
}

function areaDe(ip) {
  const n = ipANumero(ip);
  if (n !== null) {
    for (const area of e.areas) {
      for (const red of area.redes) {
        const [base, bits] = red.split('/');
        const b = ipANumero(base);
        const mascara = bits === '0' ? 0 : (~0 << (32 - Number(bits))) >>> 0;
        if (b !== null && ((n & mascara) >>> 0) === ((b & mascara) >>> 0)) return area.nombre;
      }
    }
  }
  return 'Fuera de la red';
}

function esDifusion(ip) {
  const v6 = ip.toLowerCase();
  if (v6.includes(':')) return v6.startsWith('fe80:') || v6.startsWith('ff') || v6 === '::';
  const partes = ip.split('.').map(Number);
  return ip === '0.0.0.0' || partes[3] === 255 || (partes[0] >= 224 && partes[0] <= 239);
}

async function actualizarPares() {
  const ventana = VENTANAS[e.ventana];
  const desde = Math.floor(ahoraServidor() / 1000 - ventana.segundos);
  const pedido = e.ventana;
  const pares = await pedir(`/trafico/pares?desde=${desde}`);
  if (pedido !== e.ventana) return;
  e.pares = { ventana: pedido, ...pares };
  pintarMapa();
}

function pintarMapa() {
  const p = e.pares;
  if (!p) return;
  const c = colores();
  const alertas = alertasEnVentana(p.desde * 1000, p.hasta * 1000);
  const alertaDelPar = new Map();
  for (const a of alertas) {
    const clave = `${a.origenIp}>${a.destinoIp}`;
    const actual = alertaDelPar.get(clave);
    if (!actual || masGrave(a.severidad, actual.severidad) === a.severidad && a.severidad !== actual.severidad) alertaDelPar.set(clave, a);
  }
  // Los 40 pares con más conexiones, y todos los que tienen una alerta. Fuera lo que no es una conversación
  // entre equipos: difusión y multidifusión, y las direcciones IPv6 de enlace local (descubrimiento de vecinos).
  const conversaciones = p.pares.filter((par) => !esDifusion(par.origen) && !esDifusion(par.destino));
  const elegidos = conversaciones.filter((par, i) => i < 40 || alertaDelPar.has(`${par.origen}>${par.destino}`));
  const nodos = new Map();
  const nodo = (ip, nombre) => {
    if (!nodos.has(ip)) {
      let area = areaDe(ip);
      let nombreFinal = nombre || ip;
      let forma = 'circle';
      // Las puertas de enlace (la .1 de cada subred) son el firewall que separa las áreas: van en su columna.
      if (/(^fw|firewall)/i.test(nombreFinal) || (area !== 'Fuera de la red' && /\.1$/.test(ip))) {
        forma = 'diamond';
        area = 'Firewall';
        if (!nombre) nombreFinal = 'firewall';
      } else if (/^srv|servidor/i.test(nombreFinal) || /servidor/i.test(area)) {
        forma = 'roundRect';
      }
      nodos.set(ip, { id: ip, ip, nombre: nombreFinal, area, conexiones: 0, forma });
    }
    return nodos.get(ip);
  };
  const aristas = elegidos.map((par) => {
    const origen = nodo(par.origen, par.origenNombre);
    const destino = nodo(par.destino, par.destinoNombre);
    origen.conexiones += par.conexiones;
    destino.conexiones += par.conexiones;
    const alerta = alertaDelPar.get(`${par.origen}>${par.destino}`);
    return {
      origen: par.origen, destino: par.destino, origenNombre: origen.nombre, destinoNombre: destino.nombre,
      conexiones: par.conexiones, fallidas: par.fallidas, bytes: par.bytes, servicios: par.servicios,
      severidad: alerta ? alerta.severidad : null, nivel: alerta ? `Alerta ${NIVEL[alerta.severidad].toLowerCase()}` : null,
      alertaTexto: alerta ? tipoCorto(alerta) : null,
    };
  });
  const ordenAreas = e.areas.map((a) => a.nombre);
  const antesDeServidores = ordenAreas.findIndex((nombre) => /servidor/i.test(nombre));
  ordenAreas.splice(antesDeServidores < 0 ? ordenAreas.length : antesDeServidores, 0, 'Firewall');
  ordenAreas.push('Fuera de la red');
  const areas = ordenAreas
    .map((nombre) => ({ nombre, nodos: [...nodos.values()].filter((n) => n.area === nombre).sort((a, b) => b.conexiones - a.conexiones) }))
    .filter((a) => a.nodos.length);
  const ventana = VENTANAS[p.ventana];
  const vacio = $('vacio-mapa');
  vacio.hidden = aristas.length > 0;
  vacio.textContent = `No hubo tráfico entre equipos en ${ventana.texto}.`;
  const lienzo = $('grafico-mapa');
  dibujar('mapa', opcionesMapa(c, { areas, aristas, ancho: lienzo.clientWidth, alto: lienzo.clientHeight }));
  e.tablas.mapa = {
    columnas: ['Origen', 'Destino', 'Conexiones', 'Sin respuesta', 'Servicios', 'Alerta'],
    numericas: 0,
    filas: aristas.map((a) => [`${a.origenNombre} (${a.origen})`, `${a.destinoNombre} (${a.destino})`, numero(a.conexiones), numero(a.fallidas), a.servicios.join(', ').toUpperCase(), a.severidad ? `${NIVEL[a.severidad]} · ${a.alertaTexto}` : '–']),
  };
  if (e.tablasVisibles.has('mapa')) pintarTabla('mapa');
}

function pintarLeyendas() {
  vaciar($('leyenda-conexiones'),
    GRUPOS.map((g) => el('span', {}, el('i', { style: `background:var(${g.color})` }), g.nombre)),
    el('span', { style: 'margin-left:12px' }, 'Alertas:'),
    SEVERIDADES.map((s) => el('span', {}, el('i', { class: 'linea', style: `background:var(${s.color})` }), s.nombre)));
  vaciar($('leyenda-mapa'),
    el('span', {}, el('i', { class: 'linea', style: 'background:var(--arista)' }), 'Sin alerta'),
    SEVERIDADES.map((s) => el('span', {}, el('i', { class: 'linea', style: `background:var(${s.color})` }), `Alerta ${s.nombre.toLowerCase()}`)),
    el('span', { style: 'margin-left:12px' }, '○ Equipo · ▢ Servidor · ◇ Firewall'));
  vaciar($('leyenda-por-dia'), SEVERIDADES.map((s) => el('span', {}, el('i', { style: `background:var(${s.color})` }), s.nombre)));
}

// ---------------------------------------------------------------- Alertas

async function sincronizarAlertas() {
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
    alCambiarAlertas([]);
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
  if (pagina.alertas.length) alCambiarAlertas(nuevas);
}

function alCambiarAlertas(nuevas) {
  for (const a of nuevas) e.nuevas.add(a.id);
  pintarUltimas();
  if (e.trafico) pintarTrafico();
  if (e.pares) pintarMapa();
  if (e.vista === 'alertas') pintarVistaAlertas();
  if (e.detalleAbierto && e.alertas.has(e.detalleAbierto)) abrirDetalle(e.detalleAbierto, false);
  if (nuevas.length) avisar(nuevas);
}

function nombreEquipo(a, lado) {
  return a[`${lado}Nombre`] || a[`${lado}Ip`];
}

function tiposDe(a) {
  if (a.tipoAtaque) return [a.tipoAtaque];
  const tipos = [];
  if (a.veredictos.reglas) tipos.push('servicio_no_autorizado');
  if (a.veredictos.umbrales) tipos.push('volumen_inusual');
  return tipos.length ? tipos : ['sin_tipificar'];
}

function tipoCorto(a) {
  return tiposDe(a).map((t) => TIPOS[t]).join(' y ');
}

function metodosDe(a) {
  return METODOS.filter((m) => a.veredictos[m.id]).map((m) => m.id);
}

function servicioDe(a) {
  return `${(a.servicio || '').toUpperCase()} · ${(a.protocolo || 'tcp').toUpperCase()}/${a.puerto}`;
}

function filaSeveridad(severidad) {
  return el('span', { class: `sev ${severidad}` }, NIVEL[severidad]);
}

function pintarUltimas() {
  const lista = [...e.alertas.values()].sort((a, b) => Date.parse(b.inicio) - Date.parse(a.inicio)).slice(0, 8);
  const ahora = ahoraServidor();
  vaciar($('ultimas-alertas'), lista.length ? lista.map((a) => el('li', {},
    el('button', { type: 'button', class: e.nuevas.has(a.id) ? 'nueva' : null, onclick: () => abrirDetalle(a.id) },
      filaSeveridad(a.severidad),
      el('span', { class: 'feed-tipo' }, tipoCorto(a)),
      el('span', { class: 'feed-hora', title: fechaHora(Date.parse(a.inicio)) }, haceCuanto(Date.parse(a.inicio), ahora)),
      el('span', { class: 'feed-par' }, `${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')} · ${(a.servicio || '').toUpperCase()}`),
      el('span', {}, el('span', { class: `estado-rev ${a.estadoRevision}` }, ESTADOS[a.estadoRevision])))))
    : el('li', { class: 'nota' }, 'Todavía no hay alertas.'));
}

function avisar(nuevas) {
  const recientes = nuevas.filter((a) => ahoraServidor() - Date.parse(a.creadaEn) < 15 * 60e3);
  if (!recientes.length) return;
  // La más grave y, entre las igual de graves, la que empezó último.
  const a = recientes.sort((x, y) => (SEVERIDADES.findIndex((s) => s.id === x.severidad) - SEVERIDADES.findIndex((s) => s.id === y.severidad))
    || Date.parse(y.inicio) - Date.parse(x.inicio))[0];
  const aviso = $('aviso');
  aviso.className = `aviso ${a.severidad}`;
  vaciar(aviso,
    filaSeveridad(a.severidad),
    el('div', { class: 'aviso-texto' },
      el('strong', {}, `Alerta nueva: ${tipoCorto(a)}`),
      el('span', {}, `${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')} · ${servicioDe(a)} · ${hora(Date.parse(a.inicio))}${recientes.length > 1 ? ` · y ${recientes.length - 1} más` : ''}`)),
    el('button', { type: 'button', class: 'boton-secundario', onclick: () => { aviso.hidden = true; abrirDetalle(a.id); } }, 'Ver detalle'),
    el('button', { type: 'button', class: 'boton-icono', 'aria-label': 'Cerrar el aviso', onclick: () => { aviso.hidden = true; } }, icono('cruz')));
  aviso.hidden = false;
  clearTimeout(avisar.reloj);
  avisar.reloj = setTimeout(() => { aviso.hidden = true; }, 20000);
}

// ---------------------------------------------------------------- Vista de alertas

function alertasDelPeriodo() {
  const desde = ahoraServidor() - e.dias * 86400e3;
  return [...e.alertas.values()].filter((a) => Date.parse(a.inicio) >= desde && e.filtroSeveridad.has(a.severidad) && e.filtroEstado.has(a.estadoRevision));
}

function pintarVistaAlertas() {
  const lista = alertasDelPeriodo().sort((a, b) => Date.parse(b.inicio) - Date.parse(a.inicio));
  const c = colores();
  const revisadas = lista.filter((a) => a.estadoRevision !== 'sin_revisar');
  const reales = revisadas.filter((a) => a.estadoRevision === 'ataque_real');
  const sinRevisar = lista.length - revisadas.length;
  $('kpi-total').textContent = numero(lista.length);
  $('kpi-total-detalle').textContent = `En los últimos ${e.dias} días`;
  $('kpi-sin-revisar').textContent = numero(sinRevisar);
  $('kpi-sin-revisar-detalle').textContent = SEVERIDADES.map((s) => `${numero(lista.filter((a) => a.estadoRevision === 'sin_revisar' && a.severidad === s.id).length)} ${s.nombre.toLowerCase()}`).join(' · ');
  $('kpi-cobertura').textContent = porcentaje(revisadas.length, lista.length);
  $('kpi-cobertura-detalle').textContent = `${numero(revisadas.length)} de ${numero(lista.length)} revisadas`;
  $('kpi-reales').textContent = numero(reales.length);
  $('kpi-reales-detalle').textContent = `${numero(revisadas.length - reales.length)} fueron falsos positivos`;

  // Por día (en la hora de este navegador).
  const hoy = new Date(ahoraServidor()); hoy.setHours(12, 0, 0, 0);
  const dias = Array.from({ length: e.dias }, (_, i) => hoy.getTime() - (e.dias - 1 - i) * 86400e3);
  const claveDia = (ms) => { const d = new Date(ms); d.setHours(12, 0, 0, 0); return d.getTime(); };
  const porSeveridad = Object.fromEntries(SEVERIDADES.map((s) => [s.id, new Array(dias.length).fill(0)]));
  for (const a of lista) {
    const i = dias.indexOf(claveDia(Date.parse(a.inicio)));
    if (i >= 0) porSeveridad[a.severidad][i] += 1;
  }
  dibujar('por-dia', opcionesPorDia(c, { dias, porSeveridad, etiquetas: dias.map((d) => diaLargo(d)) }));
  e.tablas['por-dia'] = {
    columnas: ['Día', 'Alta', 'Media', 'Baja', 'Total'], numericas: 4,
    filas: dias.map((d, i) => [diaLargo(d), ...SEVERIDADES.map((s) => numero(porSeveridad[s.id][i])), numero(SEVERIDADES.reduce((t, s) => t + porSeveridad[s.id][i], 0))]).reverse(),
  };

  // Por tipo.
  const porTipo = new Map();
  for (const a of lista) for (const t of tiposDe(a)) porTipo.set(t, (porTipo.get(t) || 0) + 1);
  const tipos = ORDEN_TIPOS.filter((t) => porTipo.get(t)).sort((x, y) => porTipo.get(y) - porTipo.get(x));
  dibujar('por-tipo', opcionesBarrasH(c, { etiquetas: tipos.map((t) => TIPOS[t]), valores: tipos.map((t) => porTipo.get(t)), nombre: 'alertas' }));
  e.tablas['por-tipo'] = { columnas: ['Tipo', 'Alertas'], numericas: 1, filas: tipos.map((t) => [TIPOS[t], numero(porTipo.get(t))]) };

  // Equipos destino.
  const porEquipo = new Map();
  for (const a of lista) {
    const clave = a.destinoIp;
    const actual = porEquipo.get(clave) || { nombre: a.destinoNombre ? `${a.destinoNombre} (${a.destinoIp})` : a.destinoIp, n: 0 };
    actual.n += 1;
    porEquipo.set(clave, actual);
  }
  const equipos = [...porEquipo.values()].sort((a, b) => b.n - a.n).slice(0, 8);
  dibujar('equipos', opcionesBarrasH(c, { etiquetas: equipos.map((x) => x.nombre), valores: equipos.map((x) => x.n), nombre: 'alertas' }));
  e.tablas.equipos = { columnas: ['Equipo', 'Alertas'], numericas: 1, filas: equipos.map((x) => [x.nombre, numero(x.n)]) };

  // Acierto de cada método sobre las revisadas que marcó (como el Resumen de la app).
  const acierto = METODOS.map((m) => {
    const marcadas = revisadas.filter((a) => a.veredictos[m.id]);
    const buenas = marcadas.filter((a) => a.estadoRevision === 'ataque_real').length;
    return { m, marcadas: marcadas.length, buenas, valor: marcadas.length ? (100 * buenas) / marcadas.length : 0 };
  });
  dibujar('acierto', opcionesBarrasH(c, {
    etiquetas: acierto.map((x) => x.m.nombre),
    valores: acierto.map((x) => x.valor),
    textos: acierto.map((x) => (x.marcadas ? `${numero(x.valor, 0)} % · ${numero(x.buenas)} de ${numero(x.marcadas)}` : 'sin revisadas')),
    nombre: 'de las revisadas que marcó',
  }, { maximo: 100 }));
  e.tablas.acierto = {
    columnas: ['Método', 'Revisadas que marcó', 'Ataque real', 'Precisión'], numericas: 3,
    filas: acierto.map((x) => [x.m.nombre, numero(x.marcadas), numero(x.buenas), x.marcadas ? `${numero(x.valor, 1)} %` : '–']),
  };
  for (const nombre of ['por-dia', 'por-tipo', 'equipos', 'acierto']) if (e.tablasVisibles.has(nombre)) pintarTabla(nombre);

  // La tabla de alertas.
  $('sub-tabla-alertas').textContent = `${numero(lista.length)} alertas con los filtros de arriba, las más nuevas primero. Tocá una fila para ver el detalle.`;
  const cuerpo = $('tabla-alertas').tBodies[0];
  vaciar(cuerpo, lista.slice(0, e.limiteTabla).map((a) => el('tr', { class: 'clic', tabindex: '0', onclick: () => abrirDetalle(a.id), onkeydown: (ev) => { if (ev.key === 'Enter') abrirDetalle(a.id); } },
    el('td', {}, filaSeveridad(a.severidad)),
    el('td', {}, tipoCorto(a)),
    el('td', { class: 'par' }, `${nombreEquipo(a, 'origen')} → ${nombreEquipo(a, 'destino')}`),
    el('td', { class: 'secundario' }, servicioDe(a)),
    el('td', { class: 'secundario' }, fechaHora(Date.parse(a.inicio), ahoraServidor())),
    el('td', { class: 'num' }, numero(a.cantidadFlujos)),
    el('td', {}, el('span', { class: 'metodos' }, METODOS.map((m) => el('span', { class: `metodo${a.veredictos[m.id] ? ' si' : ''}`, title: `${m.nombre}: ${a.veredictos[m.id] ? 'anómalo' : 'normal'}` }, m.corto)))),
    el('td', {}, el('span', { class: `estado-rev ${a.estadoRevision}` }, ESTADOS[a.estadoRevision])))));
  $('ver-mas').hidden = lista.length <= e.limiteTabla;
}

function pintarFiltrosAlertas() {
  vaciar($('filtro-severidad'), SEVERIDADES.map((s) => el('button', {
    type: 'button', class: 'chip-filtro', 'aria-pressed': String(e.filtroSeveridad.has(s.id)),
    onclick: () => alternar(e.filtroSeveridad, s.id),
  }, el('span', { class: 'punto', style: `background:var(${s.color})` }), s.nombre)));
  vaciar($('filtro-estado'), Object.entries(ESTADOS).map(([id, nombre]) => el('button', {
    type: 'button', class: 'chip-filtro', 'aria-pressed': String(e.filtroEstado.has(id)),
    onclick: () => alternar(e.filtroEstado, id),
  }, nombre)));
}

function alternar(conjunto, valor) {
  if (conjunto.has(valor) && conjunto.size > 1) conjunto.delete(valor);
  else conjunto.add(valor);
  e.limiteTabla = 50;
  pintarFiltrosAlertas();
  pintarVistaAlertas();
}

// ---------------------------------------------------------------- Detalle de una alerta

function abrirDetalle(id, enfocar = true) {
  const a = e.alertas.get(id);
  if (!a) return;
  e.detalleAbierto = id;
  e.nuevas.delete(id);
  const panel = $('detalle');
  const v = a.veredictos;
  const inicio = Date.parse(a.inicio);
  const fin = Date.parse(a.fin);
  const explicacion = {
    ia: `Error de reconstrucción ${numero(v.errorReconstruccion, 4)} frente a un umbral de ${numero(v.umbralIa, 4)}.`,
    umbrales: v.servicioSinLineaBase
      ? `El servicio no tenía línea base: ${numero(v.contexto60s)} conexiones en 60 segundos.`
      : `${numero(v.contexto60s)} conexiones al servicio en 60 segundos, frente a ${numero(v.umbralServicio)} de un día normal.`,
    reglas: v.parEnCatalogo ? `${v.parDestino} está en el catálogo de pares autorizados.` : `${v.parDestino} no está en el catálogo de pares autorizados.`,
  };
  const historial = a.historial || [];
  vaciar(panel,
    el('div', { class: 'detalle-cabecera' },
      el('div', {},
        el('div', { style: 'display:flex;gap:8px;align-items:center;flex-wrap:wrap' }, filaSeveridad(a.severidad), el('span', { class: `estado-rev ${a.estadoRevision}` }, ESTADOS[a.estadoRevision])),
        el('h2', { id: 'detalle-titulo' }, tipoCorto(a)),
        el('p', {}, a.tipoAtaque && a.confianzaTipo != null ? `Lo tipificó la segunda etapa con una confianza del ${numero(a.confianzaTipo * 100, 0)} %.` : 'Sin tipo de la segunda etapa: lo vieron las reglas o los umbrales.')),
      el('button', { type: 'button', class: 'boton-icono', 'aria-label': 'Cerrar el detalle', onclick: cerrarDetalle }, icono('cruz'))),
    el('div', { class: 'recorrido' },
      el('div', {}, el('strong', {}, nombreEquipo(a, 'origen')), el('span', {}, a.origenIp)),
      el('span', { class: 'flecha' }, icono('flecha')),
      el('div', { style: 'text-align:right' }, el('strong', {}, nombreEquipo(a, 'destino')), el('span', {}, a.destinoIp))),
    el('section', { class: 'detalle-seccion' },
      el('dl', { class: 'datos' },
        el('div', {}, el('dt', {}, 'Servicio'), el('dd', {}, servicioDe(a))),
        el('div', {}, el('dt', {}, 'Flujos'), el('dd', {}, numero(a.cantidadFlujos))),
        el('div', {}, el('dt', {}, 'Empezó'), el('dd', {}, fechaHoraSegundos(inicio, ahoraServidor()))),
        el('div', {}, el('dt', {}, 'Terminó'), el('dd', {}, fechaHoraSegundos(fin, ahoraServidor()))),
        el('div', {}, el('dt', {}, 'Puertos distintos'), el('dd', {}, numero(a.puertosDistintos))),
        el('div', {}, el('dt', {}, 'Aviso de Telegram'), el('dd', {}, a.avisoTelegram ? `Salió a las ${horaConSegundos(Date.parse(a.avisoTelegram))}` : 'No salió')),
        a.asignacion ? el('div', {}, el('dt', {}, 'Responsable'), el('dd', {}, a.asignacion.responsable || 'Sin asignar')) : null)),
    el('section', { class: 'detalle-seccion' },
      el('h3', {}, 'Qué vio cada método'),
      METODOS.map((m) => el('div', { class: `veredicto ${v[m.id] ? 'anomalo' : 'normal'}` },
        el('span', { class: 'veredicto-icono' }, icono(m.icono)),
        el('span', { class: 'veredicto-nombre' }, m.nombre),
        el('span', { class: 'veredicto-resultado' }, v[m.id] ? 'Anómalo' : 'Normal'),
        el('span', { class: 'veredicto-explicacion' }, explicacion[m.id])))),
    el('section', { class: 'detalle-seccion' },
      el('h3', {}, 'Revisiones'),
      historial.length ? el('ul', { class: 'historial' }, historial.slice().reverse().map((r) => el('li', {},
        el('div', {}, el('strong', {}, ESTADOS[r.resultado] || r.resultado), ` · ${MOTIVOS[r.motivo] || r.motivo}`),
        el('div', { class: 'comentario' }, r.comentario),
        el('div', { class: 'meta' }, `${r.persona || r.autor} · ${fechaHora(Date.parse(r.revisadaEn || r.fecha), ahoraServidor())}`))))
        : el('p', { class: 'nota' }, 'Todavía nadie la revisó. Se revisa desde la app, con el motivo y un comentario.')));
  $('velo').hidden = false;
  panel.hidden = false;
  if (enfocar) panel.querySelector('.boton-icono').focus();
  pintarUltimas();
}

function cerrarDetalle() {
  e.detalleAbierto = null;
  $('detalle').hidden = true;
  $('velo').hidden = true;
}

// ---------------------------------------------------------------- Gráficos y tablas

function dibujar(nombre, opciones) {
  const contenedor = $(`grafico-${nombre}`);
  if (!contenedor || !window.echarts) return;
  let grafico = e.graficos[nombre];
  if (!grafico) {
    grafico = window.echarts.init(contenedor, null, { renderer: 'canvas' });
    e.graficos[nombre] = grafico;
    // El mapa ubica los equipos en píxeles: si cambia el tamaño, se vuelve a armar.
    new ResizeObserver(() => { grafico.resize(); if (nombre === 'mapa' && e.pares) pintarMapa(); }).observe(contenedor);
    grafico.setOption(opciones, true);
    return;
  }
  grafico.setOption(opciones, { replaceMerge: ['series', 'xAxis', 'yAxis'] });
}

function redibujarTodo() {
  for (const grafico of Object.values(e.graficos)) grafico.clear();
  pintarLeyendas();
  pintarTrafico();
  pintarMapa();
  if (e.vista === 'alertas') pintarVistaAlertas();
}

function pintarTabla(nombre) {
  const datos = e.tablas[nombre];
  const lugar = $(`tabla-${nombre}`);
  if (!datos || !lugar) return;
  const desdeNumericas = datos.columnas.length - datos.numericas;
  vaciar(lugar, datos.filas.length ? el('table', { class: 'tabla' },
    el('thead', {}, el('tr', {}, datos.columnas.map((col, i) => el('th', { class: i >= desdeNumericas ? 'num' : null }, col)))),
    el('tbody', {}, datos.filas.slice(0, 500).map((fila) => el('tr', {}, fila.map((celda, i) => el('td', { class: i >= desdeNumericas ? 'num' : null }, celda))))))
    : el('p', { class: 'nota' }, 'No hay datos en la ventana.'));
}

function alternarTabla(boton) {
  const nombre = boton.dataset.tabla;
  const mostrar = !e.tablasVisibles.has(nombre);
  if (mostrar) e.tablasVisibles.add(nombre); else e.tablasVisibles.delete(nombre);
  boton.setAttribute('aria-pressed', String(mostrar));
  boton.textContent = mostrar ? 'Gráfico' : 'Tabla';
  $(`grafico-${nombre}`).hidden = mostrar;
  $(`tabla-${nombre}`).hidden = !mostrar;
  if (mostrar) pintarTabla(nombre);
  else if (e.graficos[nombre]) e.graficos[nombre].resize();
}

// ---------------------------------------------------------------- Vistas y controles

function cambiarVista(vista) {
  e.vista = vista;
  for (const boton of document.querySelectorAll('.pestania')) boton.setAttribute('aria-selected', String(boton.dataset.vista === vista));
  $('vista-vivo').hidden = vista !== 'vivo';
  $('vista-alertas').hidden = vista !== 'alertas';
  if (vista === 'alertas') pintarVistaAlertas();
  requestAnimationFrame(() => { for (const grafico of Object.values(e.graficos)) grafico.resize(); });
}

function cambiarVentana(ventana) {
  e.ventana = ventana;
  for (const boton of $('ventanas').querySelectorAll('button')) boton.setAttribute('aria-pressed', String(boton.dataset.ventana === ventana));
  $('nota-ventana').textContent = VENTANAS[ventana].nota;
  for (const nombre of ['conexiones', 'trafico', 'fallidas', 'mapa']) {
    const contenedor = $(`grafico-${nombre}`);
    if (contenedor) contenedor.classList.add('cargando');
  }
  Promise.allSettled([actualizarTrafico(), actualizarPares()]).finally(() => {
    for (const nombre of ['conexiones', 'trafico', 'fallidas', 'mapa']) $(`grafico-${nombre}`).classList.remove('cargando');
  });
  e.tareas.trafico = { ultima: Date.now(), corriendo: false };
  e.tareas.pares = { ultima: Date.now(), corriendo: false };
}

function cambiarPeriodo(dias) {
  e.dias = dias;
  e.limiteTabla = 50;
  for (const boton of $('periodos').querySelectorAll('button')) boton.setAttribute('aria-pressed', String(Number(boton.dataset.dias) === dias));
  pintarVistaAlertas();
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
  const acceso = e.acceso;
  vaciar($('red-actual'), acceso.red ? acceso.red.nombre : 'Red', el('small', {}, `${acceso.persona || 'Tablero web'} · ${acceso.perfil === 'administrador' ? 'administrador' : 'analista'}`));
  pintarLeyendas();
  pintarFiltrosAlertas();
  pintarSinRevisar();
  pintarEstado();
  await cargarAreas();
  arrancarTicker();
}

function iniciar() {
  aplicarTema(temaGuardado());
  $('form-ingreso').addEventListener('submit', entrar);
  $('codigo').addEventListener('input', (ev) => {
    const valor = normalizarCodigo(ev.target.value);
    if (valor !== ev.target.value) ev.target.value = valor;
  });
  $('boton-tema').addEventListener('click', () => {
    const tema = document.documentElement.dataset.tema === 'oscuro' ? 'claro' : 'oscuro';
    try { localStorage.setItem(CLAVE_TEMA, tema); } catch (_) { /* solo por esta vez */ }
    aplicarTema(tema);
    redibujarTodo();
  });
  $('boton-salir').addEventListener('click', salir);
  for (const boton of document.querySelectorAll('.pestania')) boton.addEventListener('click', () => cambiarVista(boton.dataset.vista));
  for (const boton of $('ventanas').querySelectorAll('button')) boton.addEventListener('click', () => cambiarVentana(boton.dataset.ventana));
  for (const boton of $('periodos').querySelectorAll('button')) boton.addEventListener('click', () => cambiarPeriodo(Number(boton.dataset.dias)));
  for (const boton of document.querySelectorAll('.boton-tabla')) boton.addEventListener('click', () => alternarTabla(boton));
  $('ver-mas').addEventListener('click', () => { e.limiteTabla += 50; pintarVistaAlertas(); });
  $('velo').addEventListener('click', cerrarDetalle);
  document.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && e.detalleAbierto) cerrarDetalle(); });
  document.addEventListener('visibilitychange', () => {
    if (!e.acceso) return;
    if (document.hidden) detenerTicker(); else arrancarTicker();
  });
  setInterval(() => { if (e.acceso && e.alertas.size) pintarUltimas(); }, 30000);

  e.acceso = leerAcceso();
  if (e.acceso) arrancarTablero(); else mostrarIngreso();
}

iniciar();
