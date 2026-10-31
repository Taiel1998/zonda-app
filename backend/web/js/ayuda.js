// La ayuda de cada «?»: un globito que flota al lado del botón, en lugar de un texto que se despliega abajo y
// corre lo demás. Uno solo a la vez; se cierra tocando afuera, con Esc, tocando el «?» de nuevo o al correr la
// página o el detalle, porque está fijo en la pantalla y quedaría lejos de su botón.

import { el, vaciar } from './util.js';

let abierta = null;

const globo = () => document.getElementById('ayuda-flotante');

/** Cierra la ayuda abierta; devuelve si había una, para que Esc no cierre además otra cosa. */
export function cerrarAyuda(enfocar = false) {
  if (!abierta) return false;
  const { boton } = abierta;
  abierta = null;
  globo().hidden = true;
  boton.setAttribute('aria-expanded', 'false');
  if (enfocar && boton.isConnected) boton.focus();
  return true;
}

/** Debajo del «?», alineada con él y sin salirse de la pantalla; arriba si abajo no entra. */
function colocar(caja) {
  const g = globo();
  const ancho = g.offsetWidth;
  const alto = g.offsetHeight;
  const izquierda = Math.max(8, Math.min(caja.left - 12, document.documentElement.clientWidth - ancho - 8));
  let arriba = caja.bottom + 8;
  if (arriba + alto > window.innerHeight - 8 && caja.top - alto - 8 >= 8) arriba = caja.top - alto - 8;
  g.style.left = `${izquierda}px`;
  g.style.top = `${arriba}px`;
}

/** Abre la ayuda de un «?» con su título y sus párrafos; si ya estaba abierta la de ese botón, la cierra. */
export function alternarAyuda(boton, titulo, parrafos) {
  if (abierta && abierta.boton === boton) { cerrarAyuda(); return; }
  cerrarAyuda();
  const g = globo();
  vaciar(g, el('p', { class: 'ayuda-titulo' }, titulo), parrafos);
  g.setAttribute('aria-label', titulo);
  g.hidden = false;
  colocar(boton.getBoundingClientRect());
  boton.setAttribute('aria-expanded', 'true');
  abierta = { boton };
  // El lector de pantalla lee el globito al recibir el foco; Esc o Tab lo cierran y el foco vuelve al «?».
  g.focus({ preventScroll: true });
}

export function configurarAyuda() {
  document.addEventListener('pointerdown', (ev) => {
    if (abierta && !globo().contains(ev.target) && !abierta.boton.contains(ev.target)) cerrarAyuda();
  });
  window.addEventListener('resize', () => cerrarAyuda());
  window.addEventListener('scroll', (ev) => { if (!(ev.target instanceof Node && globo().contains(ev.target))) cerrarAyuda(); }, { passive: true, capture: true });
  globo().addEventListener('keydown', (ev) => { if (ev.key === 'Tab') { ev.preventDefault(); cerrarAyuda(true); } });
}
