// La llave de acceso de esta computadora (decisión 51): la huella, la cara o el PIN de Windows Hello
// (WebAuthn) para lo que da poder o corta tráfico desde el tablero. Se crea al parear el navegador, en los
// 10 minutos siguientes; la privada nunca sale de la computadora y el servidor revisa cada firma.

import { el } from './util.js';
import { e, pedir } from './comun.js';
import { abrirHoja, avisoInferior } from './hojas.js';

/** base64url, como lo manda el servidor, a bytes, y al revés. */
function aBytes(texto) {
  return Uint8Array.from(atob(texto.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
}

function aTexto(bytes) {
  let binario = '';
  for (const byte of new Uint8Array(bytes)) binario += String.fromCharCode(byte);
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

const hayLlaves = () => Boolean(window.PublicKeyCredential && navigator.credentials);

export const tieneLlave = () => Boolean(e.llave && e.llave.tiene && hayLlaves());

/** Si este navegador tiene su llave y si todavía la puede crear. Un servidor sin llaves: ninguna. */
export async function actualizarLlave() {
  try {
    e.llave = await pedir('/llaves');
  } catch (_) {
    e.llave = { tiene: false, puedeCrear: false };
  }
  return e.llave;
}

/** Crea la llave: Windows pide la huella, la cara o el PIN, y el servidor guarda la parte pública. */
async function crear() {
  const { desafioId, opciones } = await pedir('/llaves/opciones', { metodo: 'POST' });
  const credencial = await navigator.credentials.create({
    publicKey: {
      ...opciones,
      challenge: aBytes(opciones.challenge),
      user: { ...opciones.user, id: aBytes(opciones.user.id) },
      excludeCredentials: (opciones.excludeCredentials || []).map((c) => ({ ...c, id: aBytes(c.id) })),
    },
  });
  const respuesta = credencial.response;
  e.llave = await pedir('/llaves', {
    metodo: 'POST',
    cuerpo: {
      desafioId,
      credencial: {
        id: credencial.id,
        rawId: aTexto(credencial.rawId),
        type: credencial.type,
        response: {
          clientDataJSON: aTexto(respuesta.clientDataJSON),
          attestationObject: aTexto(respuesta.attestationObject),
          transports: typeof respuesta.getTransports === 'function' ? respuesta.getTransports() : [],
        },
        clientExtensionResults: credencial.getClientExtensionResults(),
      },
    },
  });
}

/** Recién pareado (los 10 minutos siguientes): ofrece crear la llave. Después, solo volviendo a parear. */
export function ofrecerLlave() {
  if (!e.llave || e.llave.tiene || !e.llave.puedeCrear || !hayLlaves()) return;
  abrirHoja({
    titulo: 'Llave de esta computadora',
    cuerpo: [
      el('p', { style: 'margin:0' }, 'Para bloquear y para sumar o cambiar gente desde la compu, Windows te va a pedir la huella, la cara o el PIN.'),
      el('p', { class: 'nota', style: 'margin:0' }, 'Se crea ahora. Si no, volviendo a parear el tablero.'),
    ],
    acciones: [
      { texto: 'Ahora no', tipo: 'secundario', alHacer: (h) => h.cerrar() },
      {
        texto: 'Crear la llave',
        tipo: 'principal',
        alHacer: async (h) => {
          h.cerrar();
          try {
            await crear();
            avisoInferior('Listo: esta computadora tiene su llave.', { duracion: 4000 });
          } catch (falla) {
            const cancelada = falla && falla.name === 'NotAllowedError';
            avisoInferior(cancelada ? 'No se creó la llave: se canceló.' : `No se creó la llave: ${falla.message || falla}`, { duracion: 7000 });
          }
        },
      },
    ],
  });
}

/**
 * Confirma [accion] con la llave: Windows pide la huella, la cara o el PIN, y la firma va en X-Llave con el
 * pedido. Sin llave devuelve {}: el servidor decide qué se puede sin ella (pedir un bloqueo, por ejemplo).
 * Si la persona cancela, tira un error con `cancelada`.
 */
export async function cabecerasConLlave(accion) {
  if (!tieneLlave()) return {};
  const { desafioId, opciones } = await pedir('/llaves/desafio', { metodo: 'POST', cuerpo: { accion } });
  let firma;
  try {
    firma = await navigator.credentials.get({
      publicKey: {
        ...opciones,
        challenge: aBytes(opciones.challenge),
        allowCredentials: (opciones.allowCredentials || []).map((c) => ({ ...c, id: aBytes(c.id) })),
      },
    });
  } catch (_) {
    throw Object.assign(new Error('No se confirmó con la llave.'), { cancelada: true });
  }
  const r = firma.response;
  const respuesta = { clientDataJSON: aTexto(r.clientDataJSON), authenticatorData: aTexto(r.authenticatorData), signature: aTexto(r.signature) };
  if (r.userHandle) respuesta.userHandle = aTexto(r.userHandle);
  const credencial = { id: firma.id, rawId: aTexto(firma.rawId), type: firma.type, response: respuesta, clientExtensionResults: firma.getClientExtensionResults() };
  return { 'X-Llave': aTexto(new TextEncoder().encode(JSON.stringify({ desafio: desafioId, credencial }))) };
}
