// Las tablas largas del tablero van de a páginas, como las tablas de Grafana (decisión 58): 15 filas y, abajo,
// «16–30 de 57», las flechas y los números de página. Cada tabla guarda su página con su nombre; buscar, filtrar
// u ordenar vuelve a la primera.

import { $ } from './comun.js';
import { el, icono, numero } from './util.js';

const POR_PAGINA = 15;
const paginas = {};

export function primeraPagina(clave) {
  paginas[clave] = 1;
}

/** Las filas de la página actual; si la lista se achicó (una búsqueda, un cambio), vuelve a la última que existe. */
export function enPagina(lista, clave) {
  const ultima = Math.max(1, Math.ceil(lista.length / POR_PAGINA));
  paginas[clave] = Math.min(paginas[clave] || 1, ultima);
  const desde = (paginas[clave] - 1) * POR_PAGINA;
  return lista.slice(desde, desde + POR_PAGINA);
}

/** Los números que se ven: la primera, la última y las vecinas de la actual, con «…» en los saltos. */
function numerosDePagina(actual, ultima) {
  const vistos = [...new Set([1, actual - 1, actual, actual + 1, ultima])].filter((n) => n >= 1 && n <= ultima).sort((a, b) => a - b);
  const salida = [];
  vistos.forEach((n, i) => {
    if (i && n - vistos[i - 1] > 1) salida.push(null);
    salida.push(n);
  });
  return salida;
}

/** El pie de una tabla larga, que va en el elemento `${clave}-paginas`. Con una sola página no hay pie. Cambiar de
    página rehace la tabla con `repintar` y el foco sigue en el botón que se tocó. */
export function piePaginas(lista, clave, repintar) {
  const ultima = Math.ceil(lista.length / POR_PAGINA);
  if (ultima <= 1) return null;
  const actual = paginas[clave] || 1;
  const desde = (actual - 1) * POR_PAGINA + 1;
  const hasta = Math.min(lista.length, actual * POR_PAGINA);
  const ir = (n, foco) => {
    paginas[clave] = n;
    repintar();
    const pie = $(`${clave}-paginas`);
    const boton = pie.querySelector(`[data-foco="${foco}"]:not(:disabled)`) || pie.querySelector('[aria-current="page"]');
    if (boton) boton.focus();
  };
  const flecha = (nombre, n, dibujo, foco, apagada) => el('button', {
    type: 'button', class: 'pagina-flecha', 'aria-label': nombre, title: nombre, disabled: apagada, dataset: { foco }, onclick: () => ir(n, foco),
  }, icono(dibujo));
  return el('nav', { class: 'paginas', 'aria-label': 'Páginas de la tabla' },
    el('span', { class: 'paginas-cuenta' }, `${numero(desde)}–${numero(hasta)} de ${numero(lista.length)}`),
    flecha('Página anterior', actual - 1, 'izquierda', `${clave}-anterior`, actual === 1),
    numerosDePagina(actual, ultima).map((n) => (n === null
      ? el('span', { class: 'paginas-salto', 'aria-hidden': 'true' }, '…')
      : el('button', {
        type: 'button', 'aria-label': `Página ${n}`, 'aria-current': n === actual ? 'page' : null, dataset: { foco: `${clave}-${n}` },
        onclick: () => ir(n, `${clave}-${n}`),
      }, String(n)))),
    flecha('Página siguiente', actual + 1, 'derecha', `${clave}-siguiente`, actual === ultima));
}
