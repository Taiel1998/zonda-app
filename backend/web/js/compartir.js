// Lo que se baja del tablero: los CSV de los paneles y de la lista de alertas, y lo que se comparte del
// Resumen, como en la app: una sola imagen, siempre en claro aunque el tablero esté en oscuro, y el PDF es
// esa misma imagen en una hoja A4. El PDF se arma acá, sin el diálogo de imprimir del navegador (que
// agregaba márgenes y tomaba los colores de la pantalla): una página con la imagen en JPEG, que todos los
// lectores de PDF abren.

/** Baja un archivo con su nombre. */
export function bajarArchivo(nombre, contenido) {
  const url = URL.createObjectURL(contenido);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.hidden = true;
  document.body.append(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

/** «zonda-volumen-2026-10-31_0615», con la hora de ahora. */
export function nombreConFecha(nombre) {
  const f = new Date();
  const dos = (n) => String(n).padStart(2, '0');
  return `zonda-${nombre}-${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}_${dos(f.getHours())}${dos(f.getMinutes())}`;
}

/** CSV con punto y coma, como lo abre Excel en castellano. */
export function csvDe(columnas, filas) {
  const celda = (valor) => {
    const texto = String(valor ?? '');
    return /[";\r\n]/.test(texto) ? `"${texto.replace(/"/g, '""')}"` : texto;
  };
  const lineas = [columnas, ...filas].map((fila) => fila.map(celda).join(';'));
  return new Blob(['\ufeff', lineas.join('\r\n')], { type: 'text/csv;charset=utf-8' });
}

/** Los bytes de un data URL («data:image/jpeg;base64,…»). */
function bytesDe(dataUrl) {
  const binario = atob(dataUrl.split(',')[1]);
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i += 1) bytes[i] = binario.charCodeAt(i);
  return bytes;
}

/** La imagen del lienzo como PNG, lista para bajar. */
export function pngDe(lienzo) {
  return new Blob([bytesDe(lienzo.toDataURL('image/png'))], { type: 'image/png' });
}

/**
 * Un PDF de una hoja A4 (595 × 842 puntos) con la imagen adentro, como el de la app: entra entre márgenes de
 * 40, centrada a lo ancho y pegada arriba (`ancho` y `alto` son los de la imagen sin escalar). Es el formato
 * más simple de PDF: el catálogo, la lista de páginas, la página, la imagen en JPEG y el dibujo que la pone
 * en la hoja, con su tabla de posiciones.
 */
export function pdfDe(lienzo, ancho, alto) {
  const jpeg = bytesDe(lienzo.toDataURL('image/jpeg', 0.92));
  const escala = Math.min(515 / ancho, 762 / alto);
  const w = (ancho * escala).toFixed(2);
  const h = (alto * escala).toFixed(2);
  const x = ((595 - ancho * escala) / 2).toFixed(2);
  const y = (842 - 40 - alto * escala).toFixed(2);
  const texto = new TextEncoder();
  const partes = [];
  const posiciones = [];
  let largo = 0;
  const sumar = (dato) => {
    const bytes = typeof dato === 'string' ? texto.encode(dato) : dato;
    partes.push(bytes);
    largo += bytes.length;
  };
  const objeto = (numero, ...contenido) => {
    posiciones[numero] = largo;
    sumar(`${numero} 0 obj\n`);
    for (const parte of contenido) sumar(parte);
    sumar('\nendobj\n');
  };
  const dibujo = `q ${w} 0 0 ${h} ${x} ${y} cm /Im0 Do Q`;
  sumar('%PDF-1.4\n');
  objeto(1, '<< /Type /Catalog /Pages 2 0 R >>');
  objeto(2, '<< /Type /Pages /Kids [3 0 R] /Count 1 >>');
  objeto(3, `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>`);
  objeto(4, `<< /Type /XObject /Subtype /Image /Width ${lienzo.width} /Height ${lienzo.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.length} >>\nstream\n`, jpeg, '\nendstream');
  objeto(5, `<< /Length ${dibujo.length} >>\nstream\n${dibujo}\nendstream`);
  const tabla = largo;
  sumar(`xref\n0 6\n0000000000 65535 f \n${posiciones.slice(1).map((p) => `${String(p).padStart(10, '0')} 00000 n \n`).join('')}`);
  sumar(`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${tabla}\n%%EOF\n`);
  return new Blob(partes, { type: 'application/pdf' });
}
