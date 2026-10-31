// Un menú chico que se abre debajo de un botón, como los de Grafana: con las flechas se recorre, con Enter
// se elige y con Esc o un clic afuera se cierra. Lo usan el menú de cada panel y la actualización automática.

import { el, icono, vaciar } from './util.js';

let abierto = null;

/** Cierra el menú abierto; devuelve si había uno, para que Esc no cierre además otra cosa. */
export function cerrarMenu(enfocar = false) {
  if (!abierto) return false;
  const { menu, boton } = abierto;
  abierto = null;
  menu.hidden = true;
  boton.setAttribute('aria-expanded', 'false');
  if (enfocar) boton.focus();
  return true;
}

/**
 * items: [{ texto, icono, atajo, accion, marcado }] o { separador: true }. Con `marcado`, el ítem es una
 * opción de una lista (como cada cuánto se actualiza) y la elegida lleva el tilde.
 */
export function abrirMenu(boton, items) {
  const menu = document.getElementById('menu-flotante');
  if (abierto && abierto.boton === boton) { cerrarMenu(); return; }
  cerrarMenu();
  vaciar(menu, items.map((item) => (item.separador ? el('div', { class: 'menu-separador', role: 'separator' })
    : el('button', {
      type: 'button',
      class: 'menu-item',
      role: item.marcado === undefined ? 'menuitem' : 'menuitemradio',
      'aria-checked': item.marcado === undefined ? null : String(item.marcado),
      onclick: () => { cerrarMenu(true); item.accion(); },
    },
    item.icono || item.marcado ? icono(item.marcado ? 'check' : item.icono) : el('span', { class: 'menu-hueco' }),
    el('span', { class: 'menu-texto' }, item.texto),
    item.atajo ? el('kbd', {}, item.atajo) : null))));
  menu.hidden = false;
  // Debajo del botón, alineado a su derecha, sin salirse de la pantalla.
  const caja = boton.getBoundingClientRect();
  const ancho = menu.offsetWidth;
  const maximo = document.documentElement.clientWidth - ancho - 8;
  menu.style.top = `${caja.bottom + window.scrollY + 4}px`;
  menu.style.left = `${window.scrollX + Math.max(8, Math.min(caja.right - ancho, maximo))}px`;
  boton.setAttribute('aria-expanded', 'true');
  abierto = { menu, boton };
  const elegido = menu.querySelector('[aria-checked="true"]') || menu.querySelector('.menu-item');
  if (elegido) elegido.focus();
}

export function configurarMenu() {
  const menu = document.getElementById('menu-flotante');
  menu.addEventListener('keydown', (ev) => {
    const items = [...menu.querySelectorAll('.menu-item')];
    const i = items.indexOf(document.activeElement);
    if (ev.key === 'ArrowDown' || ev.key === 'ArrowUp') {
      ev.preventDefault();
      const siguiente = ev.key === 'ArrowDown' ? (i + 1) % items.length : (i - 1 + items.length) % items.length;
      items[siguiente].focus();
    } else if (ev.key === 'Tab') {
      cerrarMenu();
    }
  });
  document.addEventListener('pointerdown', (ev) => {
    if (abierto && !abierto.menu.contains(ev.target) && !abierto.boton.contains(ev.target)) cerrarMenu();
  });
  window.addEventListener('resize', () => cerrarMenu());
}
