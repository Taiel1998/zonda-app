// Las hojas del tablero: un diálogo en la compu y una hoja que sube desde abajo en el teléfono, como las
// de la app. También la confirmación con el botón rojo y el aviso inferior con su acción (Deshacer).

import { el, vaciar } from './util.js';

const abiertas = [];

document.addEventListener('keydown', (evento) => {
  if (evento.key === 'Escape' && abiertas.length) {
    evento.stopPropagation();
    abiertas[abiertas.length - 1].cerrar('escape');
  }
}, true);

/**
 * Abre una hoja. `acciones`: [{ texto, tipo: 'principal' | 'secundario' | 'peligro' | 'texto', alHacer, deshabilitado }].
 * Devuelve { cerrar, pintar }: `pintar` cambia el contenido (para las hojas de dos pasos).
 */
/** Detrás de una hoja o del detalle no se puede llegar con Tab: el resto queda inerte (aria-modal no alcanza). */
export function actualizarFondo() {
  const velos = [...document.querySelectorAll('#hojas > .hoja-velo')];
  velos.forEach((v, i) => { v.inert = i < velos.length - 1; });
  const detalle = document.getElementById('detalle');
  detalle.inert = velos.length > 0;
  document.getElementById('tablero').inert = velos.length > 0 || !detalle.hidden;
}

export function abrirHoja(contenido) {
  const abridor = document.activeElement;
  const velo = el('div', { class: 'hoja-velo' });
  const caja = el('div', { class: 'hoja', role: 'dialog', 'aria-modal': 'true', tabindex: '-1' });
  velo.append(caja);
  let alCerrar = contenido.alCerrar;
  const hoja = {
    cerrado: false,
    cerrar(motivo = 'cerrar') {
      if (hoja.cerrado) return;
      hoja.cerrado = true;
      velo.remove();
      abiertas.splice(abiertas.indexOf(hoja), 1);
      actualizarFondo();
      if (abridor && abridor.isConnected && !abridor.closest('[inert]')) abridor.focus({ preventScroll: true });
      if (alCerrar) alCerrar(motivo);
    },
    pintar(nuevo) {
      if (nuevo.alCerrar) alCerrar = nuevo.alCerrar;
      const titulo = el('h2', { class: 'hoja-titulo', id: `hoja-${abiertas.length}` }, nuevo.titulo);
      caja.setAttribute('aria-labelledby', titulo.id);
      const botones = (nuevo.acciones || []).map((accion) => el('button', {
        type: 'button',
        class: { principal: 'boton-principal', peligro: 'boton-peligro', texto: 'boton-texto' }[accion.tipo] || 'boton-secundario',
        disabled: accion.deshabilitado || null,
        onclick: () => accion.alHacer(hoja),
      }, accion.texto));
      vaciar(caja,
        nuevo.paso ? el('p', { class: 'hoja-paso' }, nuevo.paso) : null,
        titulo,
        nuevo.subtitulo ? el('p', { class: 'hoja-subtitulo' }, nuevo.subtitulo) : null,
        el('div', { class: 'hoja-cuerpo' }, nuevo.cuerpo || []),
        botones.length ? el('div', { class: 'hoja-acciones' }, botones) : null);
      // El foco va a un campo si hay; si no, a la hoja: así ninguna opción parece elegida sin tocarla.
      const campo = caja.querySelector('input, textarea, select');
      if (campo) campo.focus({ preventScroll: true });
      else caja.focus({ preventScroll: true });
    },
  };
  velo.addEventListener('mousedown', (evento) => { if (evento.target === velo) hoja.cerrar('afuera'); });
  document.getElementById('hojas').append(velo);
  abiertas.push(hoja);
  actualizarFondo();
  hoja.pintar(contenido);
  return hoja;
}

/** "¿Seguro?" con el botón de aceptar en rojo si `peligro`. Devuelve true si aceptó. */
export function confirmar({ titulo, texto, aceptar = 'Aceptar', peligro = false }) {
  return new Promise((resolver) => {
    let respondio = false;
    abrirHoja({
      titulo,
      cuerpo: [el('p', { style: 'margin:0;color:var(--texto-2)' }, texto)],
      acciones: [
        { texto: 'Cancelar', tipo: 'secundario', alHacer: (h) => h.cerrar() },
        { texto: aceptar, tipo: peligro ? 'peligro' : 'principal', alHacer: (h) => { respondio = true; h.cerrar('aceptar'); resolver(true); } },
      ],
      alCerrar: () => { if (!respondio) resolver(false); },
    });
  });
}

let avisoActual = null;

/** El aviso de abajo, como el de la app: un texto y, si hace falta, una acción ("Deshacer", "Reintentar"). */
export function avisoInferior(texto, { accion, alAccion, duracion = 5000, alTerminar } = {}) {
  if (avisoActual) avisoActual.cerrar(true);
  const caja = el('div', { class: 'aviso-inferior', role: 'status' }, el('span', {}, texto));
  let cerrado = false;
  const aviso = {
    cerrar(porOtro = false) {
      if (cerrado) return;
      cerrado = true;
      clearTimeout(reloj);
      quitarAtajo();
      caja.remove();
      if (avisoActual === aviso) avisoActual = null;
      if (alTerminar) alTerminar(porOtro ? 'reemplazado' : 'vencido');
    },
  };
  let reloj = null;
  let quitarAtajo = () => {};
  if (accion) {
    const hacer = () => { cerrado = true; clearTimeout(reloj); quitarAtajo(); caja.remove(); avisoActual = null; alAccion(); };
    caja.append(el('button', { type: 'button', onclick: hacer }, accion));
    // Ctrl+Z (⌘Z) mientras el aviso está a la vista, salvo que se esté escribiendo.
    const atajo = (ev) => {
      const escribiendo = ev.target instanceof Element && ev.target.closest('input, textarea, select, [contenteditable="true"]');
      if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === 'z' && !escribiendo) { ev.preventDefault(); hacer(); }
    };
    document.addEventListener('keydown', atajo);
    quitarAtajo = () => document.removeEventListener('keydown', atajo);
  }
  // El reloj se frena mientras el mouse o el foco están sobre el aviso (WCAG 2.2.1) y sigue con lo que le quedaba.
  let restante = duracion;
  let desde = Date.now();
  let encima = 0;
  const correr = () => { desde = Date.now(); clearTimeout(reloj); reloj = setTimeout(() => aviso.cerrar(), Math.max(1000, restante)); };
  const frenar = () => { if (encima++ === 0) { clearTimeout(reloj); restante -= Date.now() - desde; } };
  const soltar = () => { if (encima > 0 && --encima === 0) correr(); };
  caja.addEventListener('mouseenter', frenar);
  caja.addEventListener('mouseleave', soltar);
  caja.addEventListener('focusin', frenar);
  caja.addEventListener('focusout', soltar);
  correr();
  document.body.append(caja);
  avisoActual = aviso;
  return aviso;
}
