// El estado compartido del tablero y lo que usan todas las vistas: los textos y las reglas de la app
// (motivos, comentarios propuestos, perfiles), los pedidos a la API y los gráficos con su vista en tabla.

import { api, ErrorApi, el, vaciar } from './util.js';

export const VENTANAS = {
  '15m': { segundos: 900, paso: 5, cada: 5000, nota: 'Barras de 5 segundos · se actualiza cada 5 segundos', texto: 'estos 15 minutos' },
  '1h': { segundos: 3600, paso: 15, cada: 5000, nota: 'Barras de 15 segundos · se actualiza cada 5 segundos', texto: 'la última hora' },
  '24h': { segundos: 86400, paso: 300, cada: 30000, nota: 'Barras de 5 minutos · se actualiza cada 30 segundos', texto: 'las últimas 24 horas' },
  '7d': { segundos: 604800, paso: 3600, cada: 60000, nota: 'Barras de una hora · se actualiza cada minuto', texto: 'los últimos 7 días' },
};

export const TIPOS = {
  fuerza_bruta: 'Fuerza bruta',
  escaneo: 'Escaneo de puertos',
  ddos: 'Denegación de servicio',
  probable_falsa_alarma: 'Probable falsa alarma',
  sin_tipificar: 'Sin tipo identificado',
  servicio_no_autorizado: 'Servicio no autorizado',
  volumen_inusual: 'Volumen inusual',
};
export const ORDEN_TIPOS = ['fuerza_bruta', 'escaneo', 'ddos', 'sin_tipificar', 'probable_falsa_alarma', 'servicio_no_autorizado', 'volumen_inusual'];
export const ESTADOS = { sin_revisar: 'Sin revisar', ataque_real: 'Ataque real', falso_positivo: 'Falso positivo' };
export const NIVEL = { alta: 'Alta', media: 'Media', baja: 'Baja' };
export const ORDEN_SEVERIDAD = ['alta', 'media', 'baja'];
export const MOTIVOS = {
  escalado_al_proveedor: 'Escalado al proveedor', bloqueado_manualmente: 'Bloqueado manualmente', contrasena_cambiada: 'Contraseña cambiada',
  investigando: 'Investigando', tarea_programada: 'Tarea programada', backup: 'Backup', equipo_nuevo: 'Equipo nuevo',
  usuario_autorizado: 'Usuario autorizado', otro: 'Otro',
};
// Los mismos motivos por resultado que el servidor (MOTIVOS_POR_RESULTADO) y la app.
export const MOTIVOS_POR_RESULTADO = {
  ataque_real: ['escalado_al_proveedor', 'bloqueado_manualmente', 'contrasena_cambiada', 'investigando'],
  falso_positivo: ['tarea_programada', 'backup', 'equipo_nuevo', 'usuario_autorizado', 'otro'],
};
export const RESULTADOS = {
  ataque_real: { nombre: 'Ataque real', explicacion: 'Actividad no autorizada o maliciosa.', color: 'var(--alta)', pregunta: 'Ataque real: ¿qué se hizo?' },
  falso_positivo: { nombre: 'Falso positivo', explicacion: 'Tráfico legítimo, como un backup.', color: 'var(--texto-3)', pregunta: 'Falso positivo: ¿por qué?' },
};
export const METODOS = [
  { id: 'ia', nombre: 'Modelo de IA', corto: 'IA', icono: 'ia', resumen: 'Modelo de IA' },
  { id: 'umbrales', nombre: 'Umbrales', corto: 'Umbrales', icono: 'umbral', resumen: 'Umbrales estadísticos' },
  { id: 'reglas', nombre: 'Reglas', corto: 'Reglas', icono: 'regla', resumen: 'Reglas estáticas' },
];
export const IMPORTANCIAS = {
  critico: { nombre: 'Crítico', explicacion: 'Sus alertas suben un nivel de severidad.' },
  normal: { nombre: 'Normal', explicacion: 'Sus alertas quedan con la severidad del tipo de ataque.' },
  poco_importante: { nombre: 'Poco importante', explicacion: 'Sus alertas bajan un nivel de severidad.' },
};
export const PERFILES = { administrador: 'Administrador', analista: 'Analista' };

export const e = {
  acceso: null,
  perfil: null,
  miId: null,
  vista: 'vivo',
  ventana: '15m',
  alertas: new Map(),
  cursor: null,
  sincronizada: false,
  nuevas: new Set(),
  salud: null,
  conteo: null,
  trafico: null,
  ahora: null,
  pares: null,
  personas: [],
  equipos: [],
  severidades: {},
  registro: [],
  telegram: null,
  desfase: 0,
  ultimaRespuesta: 0,
  servidorCaido: false,
  graficos: {},
  tablas: {},
  tablasVisibles: new Set(),
  areas: [],
  detalleAbierto: null,
};

export const $ = (id) => document.getElementById(id);
export const ahoraServidor = () => Date.now() + e.desfase;
export const esAdmin = () => e.perfil === 'administrador';

let alExpirar = () => {};
export function cuandoExpire(funcion) { alExpirar = funcion; }

/** Un pedido a la API con el token de este navegador. 401: el acceso venció o lo quitaron. */
export async function pedir(ruta, opciones = {}) {
  try {
    const datos = await api(ruta, { ...opciones, token: e.acceso.token });
    e.ultimaRespuesta = Date.now();
    e.servidorCaido = false;
    return datos;
  } catch (falla) {
    if (falla instanceof ErrorApi && falla.estado === 401) {
      alExpirar('El acceso de este navegador venció o lo quitaron. Pedí un código nuevo.');
    }
    if (falla.estado === 0) e.servidorCaido = true;
    throw falla;
  }
}

export const mandar = (ruta, metodo, cuerpo) => pedir(ruta, { metodo, cuerpo });

// ---- Cambios en las alertas: las vistas se anotan y se vuelven a pintar ----

const oyentes = new Set();
export function alCambiarAlertas(funcion) { oyentes.add(funcion); }
export function avisarCambio(nuevas = []) { for (const funcion of oyentes) funcion(nuevas); }
export function guardarAlerta(alerta) {
  e.alertas.set(alerta.id, alerta);
  avisarCambio([]);
}

// ---- Lo que se dice de una alerta, igual que en la app ----

export function nombreEquipo(a, lado) {
  return a[`${lado}Nombre`] || a[`${lado}Ip`];
}

export function tiposDe(a) {
  if (a.tipoAtaque) return [a.tipoAtaque];
  const tipos = [];
  if (a.veredictos.reglas) tipos.push('servicio_no_autorizado');
  if (a.veredictos.umbrales) tipos.push('volumen_inusual');
  return tipos.length ? tipos : ['sin_tipificar'];
}

export const tipoCorto = (a) => tiposDe(a).map((t) => TIPOS[t]).join(' y ');
export const metodosDe = (a) => METODOS.filter((m) => a.veredictos[m.id]).map((m) => m.id);
export const servicioCorto = (servicio) => (servicio && servicio !== 'otro' ? servicio.toUpperCase() : 'Sin identificar');
export const servicioDe = (a) => `${servicioCorto(a.servicio)} · ${(a.protocolo || 'tcp').toUpperCase()}/${a.puerto}`;
export const responsableId = (a) => (a.asignacion && a.asignacion.responsableId) || null;
export const nombrePersona = (p) => (p ? p.nombre || p.telefono : '');

export function chipSeveridad(severidad) {
  return el('span', { class: `sev ${severidad}` }, NIVEL[severidad]);
}

export function chipEstado(estado) {
  return el('span', { class: `estado-rev ${estado}` }, ESTADOS[estado]);
}

/** El comentario que propone cada motivo, en forma impersonal (decisión 48), como en la app. */
export function comentarioPropuesto(motivo, a) {
  const origen = nombreEquipo(a, 'origen');
  const destino = nombreEquipo(a, 'destino');
  return {
    escalado_al_proveedor: 'Se avisó al proveedor de seguridad, que está revisando el servidor.',
    bloqueado_manualmente: `Se bloqueó el acceso ${servicioCorto(a.servicio)} de ${origen} y se avisó al proveedor.`,
    contrasena_cambiada: 'Se cambió la contraseña de la cuenta usada y se revisaron los accesos.',
    investigando: 'Se están revisando los registros del servidor.',
    tarea_programada: 'Es la copia nocturna que corre todos los domingos.',
    backup: `Es el backup semanal de ${origen} hacia ${destino}.`,
    equipo_nuevo: `${origen} es un equipo nuevo que se configuró hoy.`,
    usuario_autorizado: 'Es un acceso autorizado del administrador.',
    otro: 'Prueba de conectividad que hizo el proveedor.',
  }[motivo] || '';
}

// ---- Gráficos y su vista en tabla ----

export function dibujar(nombre, opciones, alCambiarTamano) {
  const contenedor = $(`grafico-${nombre}`);
  if (!contenedor || !window.echarts) return null;
  let grafico = e.graficos[nombre];
  if (!grafico) {
    grafico = window.echarts.init(contenedor, null, { renderer: 'canvas' });
    e.graficos[nombre] = grafico;
    new ResizeObserver(() => { grafico.resize(); if (alCambiarTamano) alCambiarTamano(); }).observe(contenedor);
    grafico.setOption(opciones, true);
    return grafico;
  }
  grafico.setOption(opciones, { replaceMerge: ['series', 'xAxis', 'yAxis'] });
  return grafico;
}

export function pintarTabla(nombre) {
  const datos = e.tablas[nombre];
  const lugar = $(`tabla-${nombre}`);
  if (!datos || !lugar) return;
  const desdeNumericas = datos.columnas.length - datos.numericas;
  vaciar(lugar, datos.filas.length ? el('table', { class: 'tabla' },
    el('thead', {}, el('tr', {}, datos.columnas.map((col, i) => el('th', { class: i >= desdeNumericas ? 'num' : null }, col)))),
    el('tbody', {}, datos.filas.slice(0, 500).map((fila) => el('tr', {}, fila.map((celda, i) => el('td', { class: i >= desdeNumericas ? 'num' : null }, celda))))))
    : el('p', { class: 'nota' }, 'No hay datos para mostrar.'));
}

export function alternarTabla(boton) {
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

export function refrescarTablas(...nombres) {
  for (const nombre of nombres) if (e.tablasVisibles.has(nombre)) pintarTabla(nombre);
}
