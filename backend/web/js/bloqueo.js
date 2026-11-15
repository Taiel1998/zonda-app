// El bloqueo del origen de una alerta en el tablero (decisión 51), como la tarjeta «Bloqueo» de la app: cómo
// está ese equipo en el firewall y lo que puede hacer cada uno. Quien bloquea (un administrador, o un analista
// con permiso) lo hace en el acto con la llave de esta computadora; sin la llave, o con «Pide aprobación», lo
// pide y lo aprueba alguien que bloquea. También la tarjeta «Bloqueos» de En vivo y su página (decisión 63).

import { el, fechaHora, icono, nuevoId, numero, vaciar } from './util.js';
import { $, TIPOS, e, esAdmin, nombreEquipo, pedir } from './comun.js';
import { abrirHoja, avisoError, avisoInferior } from './hojas.js';
import { cabecerasConLlave, tieneLlave } from './llave.js';
import { contarPaneles } from './filas.js';

// Lo que está abierto: un pedido que espera, uno aprobado que el firewall todavía no confirmó y uno aplicado.
const ACTIVOS = ['pedido', 'aprobado', 'aplicado'];
const DURACIONES = [
  { segundos: 3600, nombre: '1 hora', durante: 'durante 1 hora' },
  { segundos: 86400, nombre: '24 horas', durante: 'durante 24 horas' },
  { segundos: null, nombre: 'Hasta desbloquear', durante: 'hasta que alguien lo desbloquee' },
];
const TITULOS = {
  bloquear: (equipo) => `Bloquear ${equipo}`,
  pedir: (equipo) => `Pedir bloqueo de ${equipo}`,
  duracion: (equipo) => `Cambiar la duración del bloqueo de ${equipo}`,
  desbloquear: (equipo) => `Desbloquear ${equipo}`,
  rechazar: (equipo) => `Rechazar el pedido de bloqueo de ${equipo}`,
};
const CONFIRMAR = { bloquear: 'Bloquear', pedir: 'Pedir bloqueo', duracion: 'Guardar', desbloquear: 'Desbloquear', rechazar: 'Rechazar' };
const EJEMPLOS = {
  duracion: 'Por ejemplo: el ataque sigue.',
  desbloquear: 'Por ejemplo: se cambió la contraseña y se revisó el equipo.',
  rechazar: 'Por ejemplo: es el backup nocturno.',
};
const AVISOS = {
  bloquear: (equipo) => `Bloqueando ${equipo}. El firewall lo aplica en unos segundos.`,
  pedir: () => 'Pedido enviado. Queda pendiente de aprobación.',
  aprobar: (equipo) => `Bloqueo de ${equipo} aprobado. El firewall lo aplica en unos segundos.`,
  duracion: (equipo) => `Duración del bloqueo de ${equipo} cambiada.`,
  desbloquear: (equipo) => `${equipo} desbloqueado.`,
  rechazar: () => 'Pedido rechazado.',
};

let alCambiar = () => {};
let abrirAlerta = () => {};
let verPagina = () => {};
let siguiendo = [];

/** Qué se vuelve a pintar cuando cambian los bloqueos (el detalle abierto y las listas) y qué abre la tarjeta
    «Bloqueos» de En vivo: el detalle de una alerta o la página. */
export function configurarBloqueos(opciones) {
  ({ alCambiar, abrirAlerta, verPagina } = opciones);
  // El buscador de la página rehace solo los grupos: el campo no pierde lo escrito ni el foco.
  $('buscar-bloqueos').addEventListener('input', () => pintarPaginaBloqueos());
}

/** Lo que se ve de los bloqueos: si cambia, se vuelve a pintar (la hora de la última consulta va en la pieza). */
function firma(datos) {
  if (!datos) return 'sin firewall';
  const { habilitado, conectado, nuncaBloquear } = datos.firewall;
  return JSON.stringify([datos.bloqueos, habilitado, conectado, nuncaBloquear]);
}

/** Los bloqueos de la red y su firewall; null en un servidor sin bloqueos. */
export async function actualizarBloqueos() {
  const antes = firma(e.bloqueos);
  try {
    e.bloqueos = await pedir('/bloqueos');
  } catch (falla) {
    if (falla.estado !== 404) throw falla;
    e.bloqueos = null;
  }
  if (firma(e.bloqueos) === antes) return;
  pintarBloqueos();
  alCambiar();
}

/** Si este navegador bloquea en el acto: lo dice el servidor en Personas; sin ese dato, por el perfil. */
function yoBloqueo() {
  const yo = e.personas.find((p) => p.vos);
  return yo && typeof yo.puedeBloquear === 'boolean' ? yo.puedeBloquear : esAdmin();
}

// Cómo se dice cada estado: «bloqueado» es solo el que el firewall ya aplicó. Uno aprobado está «bloqueando»: el
// firewall lo aplica en unos segundos, y si no responde, el equipo sigue sin bloquear.
const NOMBRE_ESTADO = { pedido: 'pedido', aprobado: 'bloqueando', aplicado: 'bloqueado' };

/** De cada alerta con un bloqueo o un pedido, cómo está: «bloqueado», «bloqueando» o «pedido». Una por equipo. */
export function bloqueoPorAlerta() {
  const activos = (e.bloqueos ? e.bloqueos.bloqueos : []).filter((b) => ACTIVOS.includes(b.estado));
  return new Map(activos.map((b) => [b.alertaId, NOMBRE_ESTADO[b.estado]]));
}

/** El bloqueo o el pedido abierto de una IP, o null. */
export function activoDe(ip) {
  return (e.bloqueos && e.bloqueos.bloqueos.find((b) => b.ip === ip && ACTIVOS.includes(b.estado))) || null;
}

/** Si el firewall ya bloqueó el origen de la alerta (no un pedido ni uno aprobado que falta aplicar): «Revisar»
    propone «Bloqueado manualmente», como en la app. */
export function origenBloqueado(a) {
  const bloqueo = activoDe(a.origenIp);
  return Boolean(bloqueo && bloqueo.estado === 'aplicado');
}

function numeroIp(ip) {
  const partes = String(ip).split('.').map(Number);
  if (partes.length !== 4 || partes.some((p) => !Number.isInteger(p) || p < 0 || p > 255)) return null;
  return partes.reduce((total, parte) => total * 256 + parte, 0);
}

/** Si una IPv4 está en una red de la lista de «nunca bloquear» del firewall («192.168.20.0/24» o una IP sola). */
function enLaRed(ip, red) {
  const [base, largo = '32'] = String(red).split('/');
  const direccion = numeroIp(ip);
  const inicio = numeroIp(base);
  const bits = Number(largo);
  if (direccion === null || inicio === null || !Number.isInteger(bits) || bits < 0 || bits > 32) return false;
  const tamano = 2 ** (32 - bits);
  return Math.floor(direccion / tamano) === Math.floor(inicio / tamano);
}

/** «1 h», «24 h» o «hasta desbloquear»: la duración en la tarjeta, como en la app. */
function corto(segundos) {
  if (segundos == null) return 'hasta desbloquear';
  return segundos % 3600 === 0 ? `${segundos / 3600} h` : `${Math.ceil(segundos / 60)} min`;
}

/** «Bloqueado · vence 13:50», con el día si no es hoy, o «Bloqueado hasta desbloquear». */
function tituloBloqueado(bloqueo) {
  return bloqueo.venceEn ? `Bloqueado · vence ${fechaHora(Date.parse(bloqueo.venceEn))}` : 'Bloqueado hasta desbloquear';
}

/** El motivo que trae escrito la hoja, impersonal como en la app: «Fuerza bruta de pc-rrhh a srv-sueldos.». */
function motivoPropuesto(a) {
  const tipo = a.tipoAtaque && !['sin_tipificar', 'probable_falsa_alarma'].includes(a.tipoAtaque) ? TIPOS[a.tipoAtaque] : null;
  return `${tipo || 'Alerta'} de ${nombreEquipo(a, 'origen')} a ${nombreEquipo(a, 'destino')}.`;
}

function fila(nombreIcono, tono, titulo, detalle, accion = null) {
  return el('div', { class: 'fila-bloqueo' },
    el('span', { class: `fila-bloqueo-icono ${tono}` }, icono(nombreIcono)),
    el('div', { class: 'fila-bloqueo-texto' }, el('strong', {}, titulo), detalle ? el('span', { class: 'nota' }, detalle) : null),
    accion);
}

const boton = (texto, alTocar) => el('button', { type: 'button', class: 'boton-secundario boton-chico', onclick: alTocar }, texto);

/** La tarjeta «Bloqueo» del detalle, debajo del responsable; null en modo de observación (sin firewall). */
export function seccionBloqueo(a) {
  const firewall = e.bloqueos && e.bloqueos.firewall;
  if (!firewall || !firewall.habilitado) return null;
  const actual = activoDe(a.origenIp);
  const bloquea = yoBloqueo();
  const conLlave = bloquea && tieneLlave();
  const contenido = [];
  if (!actual) {
    const nunca = (firewall.nuncaBloquear || []).some((red) => enLaRed(a.origenIp, red));
    const aviso = nunca ? 'El firewall nunca bloquea este equipo' : firewall.conectado ? null : 'El firewall no responde';
    const accion = nunca ? null : boton(conLlave ? 'Bloquear' : 'Pedir bloqueo', () => hojaBloqueo(a, conLlave ? 'bloquear' : 'pedir'));
    contenido.push(fila('candado-abierto', '', 'Sin bloquear', aviso, accion));
  } else if (actual.estado === 'pedido') {
    contenido.push(fila('espera', 'media', `Pedido de bloqueo · ${corto(actual.duracion)}`, `Lo pidió ${actual.pedidoPor} · ${fechaHora(Date.parse(actual.pedidoEn))}`));
    contenido.push(el('p', { class: 'nota bloqueo-nota' }, `“${actual.motivo}”`));
    if (!bloquea) {
      contenido.push(el('p', { class: 'nota bloqueo-nota' }, 'Pendiente de aprobación'));
    } else {
      contenido.push(el('div', { class: 'fila-bloqueo-botones' },
        boton('Rechazar', () => hojaBloqueo(a, 'rechazar', actual)),
        conLlave ? boton('Aprobar', () => ejecutar(a, 'aprobar', { actual })) : null));
      if (!conLlave) contenido.push(el('p', { class: 'nota bloqueo-nota' }, 'Se aprueba desde el teléfono o con la llave de esta computadora.'));
    }
  } else {
    // Como en la app: aprobado y todavía sin aplicar dice «Bloqueando…», con el reloj, o «Bloqueo sin aplicar» si el
    // firewall no responde; «Bloqueado» sale recién cuando el firewall lo confirma.
    const aplicado = actual.estado === 'aplicado';
    const detalle = !firewall.conectado ? 'El firewall no responde'
      : !aplicado ? 'El firewall lo aplica en unos segundos'
        : actual.aprobadoEn ? `Lo bloqueó ${actual.aprobadoPor} · ${fechaHora(Date.parse(actual.aprobadoEn))}` : null;
    const enEspera = firewall.conectado ? 'Bloqueando…' : 'Bloqueo sin aplicar';
    contenido.push(aplicado ? fila('candado', 'alta', tituloBloqueado(actual), detalle) : fila('espera', 'media', enEspera, detalle));
    if (conLlave) {
      contenido.push(el('div', { class: 'fila-bloqueo-botones' },
        boton('Cambiar duración', () => hojaBloqueo(a, 'duracion', actual)),
        boton('Desbloquear', () => hojaBloqueo(a, 'desbloquear', actual))));
    } else if (bloquea) {
      contenido.push(el('p', { class: 'nota bloqueo-nota' }, 'Se cambia o se desbloquea desde el teléfono o con la llave de esta computadora.'));
    }
  }
  return el('section', { class: 'detalle-seccion' }, el('h3', {}, 'Bloqueo'), contenido);
}

/** La hoja del bloqueo, como la de la app: qué se corta, la duración (al bloquear o cambiarla) y el motivo obligatorio. */
function hojaBloqueo(a, accion, actual = null) {
  const equipo = nombreEquipo(a, 'origen');
  const conDuracion = ['bloquear', 'pedir', 'duracion'].includes(accion);
  let duracion = accion === 'duracion' ? actual.duracion : DURACIONES[0].segundos;
  let motivo = ['bloquear', 'pedir'].includes(accion) ? motivoPropuesto(a) : '';
  const hoja = abrirHoja({ titulo: '', cuerpo: [] });

  function queSeCorta() {
    if (accion === 'desbloquear') return el('p', { class: 'bloqueo-corte' }, `${equipo} (${a.origenIp}) recupera el acceso a otras áreas y a internet.`);
    if (!conDuracion) return null;
    const elegida = DURACIONES.find((d) => d.segundos === duracion) || DURACIONES[0];
    // Hasta desbloquear se puede elegir siempre; la línea pasa a ámbar para que se vea que no vence sola.
    return el('p', { class: `bloqueo-corte${duracion == null ? ' sin-vencimiento' : ''}` }, `${equipo} (${a.origenIp}) no podrá acceder a otras áreas ni a internet ${elegida.durante}.`);
  }

  function pintar() {
    const texto = el('textarea', { maxlength: '500', 'aria-required': 'true', placeholder: EJEMPLOS[accion] || '' });
    texto.value = motivo;
    const falta = el('small', { class: 'error-campo', hidden: true }, 'Escribí el motivo.');
    texto.addEventListener('input', () => {
      motivo = texto.value;
      if (motivo.trim()) falta.hidden = true;
    });
    hoja.pintar({
      titulo: TITULOS[accion](equipo),
      cuerpo: [
        queSeCorta(),
        conDuracion ? el('div', { class: 'grupo-opciones' },
          el('p', {}, accion === 'duracion' ? '¿Por cuánto tiempo, desde ahora?' : '¿Por cuánto tiempo?'),
          el('div', { class: 'opciones', role: 'radiogroup' }, DURACIONES.map((d) => el('button', {
            type: 'button', class: `chip-filtro${duracion === d.segundos ? ' activo' : ''}`, role: 'radio', 'aria-checked': String(duracion === d.segundos),
            onclick: () => { duracion = d.segundos; pintar(); },
          }, d.nombre)))) : null,
        el('label', { class: 'campo' }, el('span', {}, 'Motivo', el('span', { class: 'asterisco', title: 'Obligatorio' }, ' *')), texto, falta),
      ],
      acciones: [
        { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
        {
          texto: CONFIRMAR[accion],
          tipo: 'principal',
          alHacer: (h) => {
            if (!motivo.trim()) {
              falta.hidden = false;
              texto.focus();
              return;
            }
            h.cerrar();
            ejecutar(a, accion, { duracion, motivo: motivo.trim(), actual });
          },
        },
      ],
    });
  }

  pintar();
}

/** Manda la acción; lo que corta o devuelve tráfico va con la firma de la llave (Windows pide la huella o el PIN). */
async function ejecutar(a, accion, { duracion = null, motivo = '', actual = null }) {
  const ruta = actual ? `/bloqueos/${encodeURIComponent(actual.id)}` : '/bloqueos';
  try {
    if (accion === 'bloquear' || accion === 'pedir') {
      const cabeceras = accion === 'bloquear' ? await cabecerasConLlave({ tipo: 'bloquear', alertaId: a.id, duracion }) : {};
      await pedir(ruta, { metodo: 'POST', cuerpo: { accionId: nuevoId(), alertaId: a.id, duracion, motivo }, cabeceras });
    } else if (accion === 'aprobar') {
      await pedir(`${ruta}/aprobacion`, { metodo: 'POST', cabeceras: await cabecerasConLlave({ tipo: 'aprobar', bloqueoId: actual.id }) });
    } else if (accion === 'duracion') {
      const cabeceras = await cabecerasConLlave({ tipo: 'duracion', bloqueoId: actual.id, duracion });
      await pedir(`${ruta}/duracion`, { metodo: 'PUT', cuerpo: { duracion, motivo }, cabeceras });
    } else if (accion === 'desbloquear') {
      const cabeceras = await cabecerasConLlave({ tipo: 'desbloquear', bloqueoId: actual.id });
      await pedir(`${ruta}/desbloqueo`, { metodo: 'POST', cuerpo: { motivo }, cabeceras });
    } else {
      await pedir(`${ruta}/rechazo`, { metodo: 'POST', cuerpo: { motivo } });
    }
    avisoInferior(AVISOS[accion](nombreEquipo(a, 'origen')), { duracion: 5000 });
  } catch (falla) {
    avisoError(falla.cancelada ? 'No se confirmó con la llave: no se aplicó ningún cambio.' : `No se pudo: ${falla.message}`, { duracion: 7000 });
  }
  await actualizarBloqueos().catch(() => {});
  seguirAlFirewall();
}

/** Como la app: el firewall pregunta cada 10 s, así que se vuelve a mirar a los 4, 8 y 12 s para ver «aplicado». */
function seguirAlFirewall() {
  for (const reloj of siguiendo) clearTimeout(reloj);
  siguiendo = [4000, 8000, 12000].map((ms) => setTimeout(() => actualizarBloqueos().catch(() => {}), ms));
}

// Los tres números de la tarjeta, en el orden de la página: lo que espera a una persona, lo que espera al firewall y
// lo que ya está bloqueado. El ícono y el color, como en cada grupo de la página; si el firewall no responde, los
// aprobados dicen «Sin aplicar», como el título de su grupo.
const NUMEROS = [
  { estado: 'pedido', nombre: () => 'Pedidos', icono: 'espera', tono: 'media', titulo: 'Ver los pedidos de bloqueo' },
  { estado: 'aprobado', nombre: (conectado) => (conectado ? 'Bloqueando' : 'Sin aplicar'), icono: 'espera', tono: 'media', titulo: 'Ver los equipos que se están bloqueando' },
  { estado: 'aplicado', nombre: () => 'Bloqueados', icono: 'candado', tono: 'alta', titulo: 'Ver los equipos bloqueados' },
];
let activosAhora = [];

/** Con un solo bloqueo o pedido, el detalle de su alerta; si no, la página «Bloqueos». */
function abrirBloqueos() {
  if (activosAhora.length === 1) abrirAlerta(activosAhora[0].alertaId);
  else verPagina();
}

/** La tarjeta «Bloqueos» de En vivo (decisión 63), al lado de «Alertas sin revisar» y con su forma: cuántos pedidos,
    cuántos que el firewall está aplicando y cuántos equipos bloqueados hay ahora. Solo en una red con firewall; sin
    él, la fila queda como siempre. Antes era una línea chica al pie de la pieza «Firewall», que no se veía. */
export function pintarBloqueos() {
  const tarjeta = $('tarjeta-bloqueos');
  if (!tarjeta) return;
  const habilitado = Boolean(e.bloqueos && e.bloqueos.firewall && e.bloqueos.firewall.habilitado);
  activosAhora = habilitado ? e.bloqueos.bloqueos.filter((b) => ACTIVOS.includes(b.estado)) : [];
  if (tarjeta.hidden === habilitado) {
    tarjeta.hidden = !habilitado;
    $('grilla-estado').classList.toggle('con-bloqueos', habilitado);
    contarPaneles();
  }
  if (!habilitado) return;
  const lugar = $('bloqueos-vivo');
  const conectado = Boolean(e.bloqueos.firewall.conectado);
  const cuantos = (estado) => numero(activosAhora.filter((b) => b.estado === estado).length);
  // Como los contadores de sin revisar: si ya están, cambian solo los nombres y los números, así no se pierde el foco
  // del teclado.
  if (lugar.children.length) {
    for (const n of NUMEROS) {
      const boton = lugar.querySelector(`[data-estado="${n.estado}"]`);
      boton.querySelector('.contador-nombre').textContent = n.nombre(conectado);
      boton.querySelector('.contador-numero').textContent = cuantos(n.estado);
    }
    return;
  }
  vaciar(lugar, NUMEROS.map((n) => el('button', { type: 'button', class: 'contador', dataset: { estado: n.estado }, title: n.titulo, onclick: abrirBloqueos },
    el('span', { class: 'contador-titulo' }, el('span', { class: `fila-bloqueo-icono ${n.tono}` }, icono(n.icono)), el('span', { class: 'contador-nombre' }, n.nombre(conectado))),
    el('span', { class: 'contador-numero' }, cuantos(n.estado)))));
}

// Los grupos de la página, en este orden y con los títulos de la app: lo que espera a una persona, lo que espera al
// firewall (o «Sin aplicar», si no responde) y lo que ya está bloqueado.
const GRUPOS = [
  { estado: 'pedido', titulo: () => 'Esperan aprobación', icono: 'espera', tono: 'media' },
  { estado: 'aprobado', titulo: (conectado) => (conectado ? 'Bloqueando' : 'Sin aplicar'), icono: 'espera', tono: 'media' },
  { estado: 'aplicado', titulo: () => 'Bloqueados', icono: 'candado', tono: 'alta' },
];

// De cuántos equipos en adelante aparece el buscador de la página: más o menos lo que entra en la pantalla de un
// teléfono. Con menos, la página queda limpia, como en la app.
const LISTA_LARGA = 8;
// Para buscar sin importar las tildes ni las mayúsculas, como en Ajustes → Equipos.
const sinTildes = (texto) => String(texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

/** El texto gris de cada fila, como en la app: «Pedido de Laura», «El firewall lo aplica en unos segundos» (o «El
    firewall no responde») y «Vence 13:34», con el día si no es hoy, o «Hasta desbloquear». */
function textoDeLaFila(b, conectado) {
  if (b.estado === 'pedido') return `Pedido de ${b.pedidoPor}`;
  if (b.estado === 'aprobado') return conectado ? 'El firewall lo aplica en unos segundos' : 'El firewall no responde';
  return b.venceEn ? `Vence ${fechaHora(Date.parse(b.venceEn))}` : 'Hasta desbloquear';
}

// El orden dentro de cada grupo, como la app: los pedidos, con el más viejo arriba; los que el firewall está
// aplicando, por la hora en que se aprobaron; los bloqueados, con el que vence antes arriba y los que no vencen al
// final.
const TURNO = { pedido: 0, aprobado: 1, aplicado: 2 };

const antes = (x, y) => (x === y ? 0 : x < y ? -1 : 1);

function compararBloqueos(x, y) {
  if (x.estado !== y.estado) return TURNO[x.estado] - TURNO[y.estado];
  if (x.estado === 'pedido') return antes(Date.parse(x.pedidoEn), Date.parse(y.pedidoEn));
  if (x.estado === 'aprobado') return antes(Date.parse(x.aprobadoEn || x.pedidoEn), Date.parse(y.aprobadoEn || y.pedidoEn));
  return antes(x.venceEn ? Date.parse(x.venceEn) : Infinity, y.venceEn ? Date.parse(y.venceEn) : Infinity);
}

/** La página «Bloqueos» (decisión 63), como la pantalla de la app: lo activo ahora, en grupos con título, cada uno
    en su tarjeta. Un grupo vacío no aparece. Cada fila abre su alerta: ahí se aprueba, se rechaza, se cambia la
    duración o se desbloquea, como siempre. Lo terminado queda en el registro de cambios. Se vuelve a pintar con cada
    consulta de los bloqueos (cada 15 s). */
export function pintarPaginaBloqueos() {
  const lugar = $('lista-bloqueos');
  if (!lugar) return;
  const conectado = Boolean(e.bloqueos && e.bloqueos.firewall && e.bloqueos.firewall.conectado);
  const activos = (e.bloqueos ? e.bloqueos.bloqueos : []).filter((b) => ACTIVOS.includes(b.estado)).sort(compararBloqueos);
  // Con una lista larga, el buscador por equipo o IP, como el de Ajustes → Equipos. No se va mientras tenga algo
  // escrito, aunque la lista se acorte.
  const campo = $('buscar-bloqueos');
  const buscado = sinTildes(campo.value.trim());
  campo.hidden = activos.length <= LISTA_LARGA && !buscado;
  const coincide = (b) => !buscado || sinTildes(b.nombre).includes(buscado) || b.ip.includes(buscado);
  const grupos = GRUPOS.map((g) => ({ ...g, filas: activos.filter((b) => b.estado === g.estado && coincide(b)) })).filter((g) => g.filas.length);
  // Se arma de nuevo con cada consulta: si el foco estaba en una fila, vuelve a la misma.
  const enfocada = lugar.contains(document.activeElement) ? document.activeElement.dataset.id : null;
  // Con nombre, la dirección va delante del texto gris, como en la app: el catálogo puede darle el mismo nombre a dos
  // direcciones (pc-rrhh en la demo) y el firewall bloquea por dirección.
  const texto = (b) => (b.nombre ? `${b.ip} · ${textoDeLaFila(b, conectado)}` : textoDeLaFila(b, conectado));
  vaciar(lugar, grupos.length
    ? grupos.map((g) => el('section', { class: 'tarjeta grupo-bloqueos' },
      el('h2', { class: 'grupo-titulo' }, g.titulo(conectado)),
      el('ul', { class: 'lista-bloqueos' }, g.filas.map((b) => el('li', {},
        el('button', { type: 'button', class: 'fila-bloqueo', dataset: { id: b.id }, onclick: () => abrirAlerta(b.alertaId) },
          el('span', { class: `fila-bloqueo-icono ${g.tono}` }, icono(g.icono)),
          el('span', { class: 'fila-bloqueo-texto' }, el('strong', {}, b.nombre || b.ip), el('span', { class: 'nota' }, texto(b))),
          el('span', { class: 'fila-bloqueo-ir' }, icono('derecha'))))))))
    : el('article', { class: 'tarjeta' }, el('p', { class: 'vacio-lista' },
      activos.length ? `Ningún equipo coincide con «${campo.value.trim()}».` : 'No hay equipos bloqueados ni pedidos de bloqueo.')));
  if (enfocada) {
    const boton = lugar.querySelector(`[data-id="${CSS.escape(enfocada)}"]`);
    if (boton) boton.focus({ preventScroll: true });
  }
}
