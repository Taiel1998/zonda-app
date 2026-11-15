// Las filas de la vista En vivo, como las de Grafana: cada una con su título y su flecha; un clic la pliega o
// la despliega, y plegada dice cuántos paneles tiene. Cada navegador recuerda cuáles dejó plegadas.

import { icono } from './util.js';

const CLAVE = 'zonda.tablero.filas';
let alCambiar = () => {};

function leerPlegadas() {
  try {
    const lista = JSON.parse(localStorage.getItem(CLAVE) || '[]');
    return new Set(Array.isArray(lista) ? lista : []);
  } catch (_) {
    return new Set();
  }
}

function guardarPlegadas() {
  const plegadas = [...document.querySelectorAll('.fila.plegada')].map((fila) => fila.dataset.fila);
  try { localStorage.setItem(CLAVE, JSON.stringify(plegadas)); } catch (_) { /* solo por esta vez */ }
}

function ponerFila(fila, abierta) {
  fila.classList.toggle('plegada', !abierta);
  fila.querySelector('.fila-boton').setAttribute('aria-expanded', String(abierta));
  fila.querySelector('.grilla').hidden = !abierta;
}

function cambiarFila(fila, abierta) {
  ponerFila(fila, abierta);
  guardarPlegadas();
  alCambiar(fila.dataset.fila, abierta);
}

/** Si la fila está desplegada (o no existe). */
export function filaAbierta(nombre) {
  const fila = document.querySelector(`.fila[data-fila="${nombre}"]`);
  return !fila || !fila.classList.contains('plegada');
}

/** Despliega la fila de un panel, para verlo en grande. */
export function abrirFilaDe(tarjeta) {
  const fila = tarjeta.closest('.fila');
  if (fila && fila.classList.contains('plegada')) cambiarFila(fila, true);
}

/** «(5 paneles)»: lo que dice cada fila plegada. La tarjeta «Bloqueos» cuenta solo si se ve (en una red con
    firewall); bloqueo.js vuelve a contar cuando aparece o se va. */
export function contarPaneles() {
  for (const fila of document.querySelectorAll('.fila')) {
    const paneles = fila.querySelectorAll('.grilla > .tarjeta:not(#tarjeta-bloqueos[hidden])').length;
    fila.querySelector('.fila-cuenta').textContent = paneles === 1 ? ' (1 panel)' : ` (${paneles} paneles)`;
  }
}

/** alCambiar(fila, abierta): al desplegar una fila, sus gráficos se dibujan con el tamaño que tienen. */
export function configurarFilas(opciones) {
  alCambiar = opciones.alCambiar;
  const plegadas = leerPlegadas();
  for (const fila of document.querySelectorAll('.fila')) {
    const boton = fila.querySelector('.fila-boton');
    boton.prepend(icono('abajo'));
    ponerFila(fila, !plegadas.has(fila.dataset.fila));
    boton.addEventListener('click', () => cambiarFila(fila, fila.classList.contains('plegada')));
  }
  contarPaneles();
}
