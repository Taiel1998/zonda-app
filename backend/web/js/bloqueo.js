// El bloqueo del origen de una alerta en el tablero (decisión 51), como la tarjeta «Bloqueo» de la app: cómo
// está ese equipo en el firewall y lo que puede hacer cada uno. Quien bloquea (un administrador, o un analista
// con permiso) lo hace en el acto con la llave de esta computadora; sin la llave, o con «Pide aprobación», lo
// pide y lo aprueba alguien que bloquea. También la lista de bloqueos de la pieza «Firewall».

import { el, fechaHora, icono, nuevoId, vaciar } from './util.js';
import { $, TIPOS, e, esAdmin, nombreEquipo, pedir } from './comun.js';
import { abrirHoja, avisoInferior } from './hojas.js';
import { cabecerasConLlave, tieneLlave } from './llave.js';

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
  bloquear: 'Bloqueado. El firewall lo aplica en unos segundos.',
  pedir: 'Pedido enviado. Lo aprueba alguien que puede bloquear.',
  aprobar: 'Aprobado. El firewall lo aplica en unos segundos.',
  duracion: 'Duración cambiada.',
  desbloquear: 'Desbloqueado.',
  rechazar: 'Pedido rechazado.',
};

let abrirAlerta = () => {};
let alCambiar = () => {};
let siguiendo = [];

/** Qué abre cada bloqueo de la lista (su alerta) y qué se vuelve a pintar cuando cambian (el detalle abierto). */
export function configurarBloqueos(opciones) {
  ({ abrirAlerta, alCambiar } = opciones);
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

/** El bloqueo o el pedido abierto de una IP, o null. */
export function activoDe(ip) {
  return (e.bloqueos && e.bloqueos.bloqueos.find((b) => b.ip === ip && ACTIVOS.includes(b.estado))) || null;
}

/** Si el origen de la alerta ya está bloqueado (no solo pedido): «Revisar» propone «Bloqueado manualmente». */
export function origenBloqueado(a) {
  const bloqueo = activoDe(a.origenIp);
  return Boolean(bloqueo && bloqueo.estado !== 'pedido');
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
      contenido.push(el('p', { class: 'nota bloqueo-nota' }, 'Espera a alguien que pueda bloquear'));
    } else {
      contenido.push(el('div', { class: 'fila-bloqueo-botones' },
        boton('Rechazar', () => hojaBloqueo(a, 'rechazar', actual)),
        conLlave ? boton('Aprobar', () => ejecutar(a, 'aprobar', { actual })) : null));
      if (!conLlave) contenido.push(el('p', { class: 'nota bloqueo-nota' }, 'Se aprueba desde el celular o con la llave de esta computadora.'));
    }
  } else {
    const detalle = !firewall.conectado ? 'El firewall no responde'
      : actual.estado === 'aprobado' ? 'Esperando al firewall'
        : actual.aprobadoEn ? `Lo bloqueó ${actual.aprobadoPor} · ${fechaHora(Date.parse(actual.aprobadoEn))}` : null;
    contenido.push(fila('candado', 'alta', tituloBloqueado(actual), detalle));
    if (conLlave) {
      contenido.push(el('div', { class: 'fila-bloqueo-botones' },
        boton('Cambiar duración', () => hojaBloqueo(a, 'duracion', actual)),
        boton('Desbloquear', () => hojaBloqueo(a, 'desbloquear', actual))));
    } else if (bloquea) {
      contenido.push(el('p', { class: 'nota bloqueo-nota' }, 'Se cambia o se desbloquea desde el celular o con la llave de esta computadora.'));
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
    if (accion === 'desbloquear') return el('p', { class: 'bloqueo-corte' }, `${equipo} (${a.origenIp}) vuelve a llegar a otras áreas y a internet.`);
    if (!conDuracion) return null;
    const elegida = DURACIONES.find((d) => d.segundos === duracion) || DURACIONES[0];
    // Hasta desbloquear se puede elegir siempre; la línea pasa a ámbar para que se vea que no vence sola.
    return el('p', { class: `bloqueo-corte${duracion == null ? ' sin-vencimiento' : ''}` }, `${equipo} (${a.origenIp}) no va a llegar a otras áreas ni a internet ${elegida.durante}.`);
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
        el('label', { class: 'campo' }, el('span', {}, 'Motivo', el('span', { class: 'asterisco' }, ' *')), texto, falta,
          el('small', {}, 'Queda en el registro de cambios con tu nombre y la hora.')),
        el('p', { class: 'leyenda-obligatorio', style: 'margin:0' }, el('span', { class: 'asterisco' }, '*'), ' Obligatorio'),
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
    avisoInferior(AVISOS[accion], { duracion: 5000 });
  } catch (falla) {
    avisoInferior(falla.cancelada ? 'No se confirmó con la llave: no se hizo nada.' : `No se pudo: ${falla.message}`, { duracion: 7000 });
  }
  await actualizarBloqueos().catch(() => {});
  seguirAlFirewall();
}

/** Como la app: el firewall pregunta cada 10 s, así que se vuelve a mirar a los 4, 8 y 12 s para ver «aplicado». */
function seguirAlFirewall() {
  for (const reloj of siguiendo) clearTimeout(reloj);
  siguiendo = [4000, 8000, 12000].map((ms) => setTimeout(() => actualizarBloqueos().catch(() => {}), ms));
}

/** La lista de la pieza «Firewall» de Estado del sistema: los equipos bloqueados o pedidos; cada uno abre su alerta. */
export function pintarBloqueos() {
  const lista = $('lista-bloqueos');
  if (!lista) return;
  const activos = e.bloqueos ? e.bloqueos.bloqueos.filter((b) => ACTIVOS.includes(b.estado)) : [];
  if (!activos.length) {
    vaciar(lista, el('li', { class: 'nota' }, 'Sin bloqueos'));
    return;
  }
  vaciar(lista, activos.map((b) => el('li', {},
    el('button', { type: 'button', class: 'bloqueo-item', onclick: () => abrirAlerta(b.alertaId) },
      el('span', { class: `fila-bloqueo-icono ${b.estado === 'pedido' ? 'media' : 'alta'}` }, icono(b.estado === 'pedido' ? 'espera' : 'candado')),
      el('span', { class: 'bloqueo-item-texto' },
        el('strong', {}, b.nombre || b.ip),
        el('span', { class: 'nota' }, b.estado === 'pedido' ? `Pedido de ${b.pedidoPor} · espera aprobación` : tituloBloqueado(b)))))));
}
