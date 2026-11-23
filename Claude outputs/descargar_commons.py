#!/usr/bin/env python3
"""
descargar_commons.py — Aqualert

Descarga imagenes de Wikimedia Commons para las categorias que no
tienen un dataset publico dedicado: aguas residuales, espumas y
escombros.

Por que Commons y no una busqueda de imagenes cualquiera:
todo su contenido tiene licencia libre documentada (Creative Commons
o dominio publico). El script guarda ademas un CSV con el autor, la
licencia y el enlace de cada archivo, que es lo que necesitas para
el anexo de fuentes de tu trabajo de grado.

USO
---
    python descargar_commons.py crudo --por-clase 200

Deja las imagenes en:
    crudo/aguas_residuales/
    crudo/espumas/
    crudo/escombros/

y la procedencia en:
    crudo/atribuciones.csv

Luego pasa esa carpeta "crudo" por preparar_dataset.py.

Solo usa la biblioteca estandar de Python.
"""

import argparse
import csv
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://commons.wikimedia.org/w/api.php"

# Wikimedia exige un User-Agent que identifique a quien consulta.
# Cambia el correo por el tuyo.
USER_AGENT = (
    "AqualertDatasetBuilder/1.0 "
    "(proyecto academico Universidad Manuela Beltran; contacto: cambia@tu-correo.com)"
)

# Ancho al que se pide cada imagen. Suficiente para entrenar
# (el modelo usa 224 px) y mucho mas rapido de descargar.
ANCHO = 800

# Pausa entre peticiones, por cortesia con el servidor
PAUSA = 0.4

# Terminos de busqueda por clase. En ingles porque Commons
# tiene mucho mas material etiquetado en ese idioma.
BUSQUEDAS = {
    "aguas_residuales": [
        "sewage discharge river",
        "wastewater outfall",
        "sewage outfall pipe",
        "polluted river water sewage",
        "open sewer canal",
        "sewage water pollution",
        "aguas residuales rio",
        "contaminated river water brown",
    ],
    "espumas": [
        "river foam pollution",
        "detergent foam water",
        "foam river surface",
        "water pollution foam white",
        "foaming river",
        "espuma rio contaminacion",
        "sewage foam water",
    ],
    "escombros": [
        "construction debris river",
        "rubble dumped river",
        "debris water channel",
        "construction waste water",
        "escombros rio",
        "building rubble canal",
        "demolition waste dumped",
    ],
}


def consultar(parametros: dict) -> dict:
    """Llama a la API de Commons y devuelve la respuesta como diccionario."""
    url = f"{API}?{urllib.parse.urlencode(parametros)}"
    peticion = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})

    with urllib.request.urlopen(peticion, timeout=30) as respuesta:
        return json.loads(respuesta.read().decode("utf-8"))


def buscar(termino: str, limite: int, desplazamiento: int = 0) -> list:
    """
    Busca archivos de imagen en Commons.

    gsrnamespace=6 restringe la busqueda al espacio de nombres
    "Archivo"; filetype:bitmap descarta SVG, PDF y audio.
    """
    parametros = {
        "action": "query",
        "format": "json",
        "generator": "search",
        "gsrsearch": f"filetype:bitmap {termino}",
        "gsrnamespace": 6,
        "gsrlimit": min(limite, 50),
        "gsroffset": desplazamiento,
        "prop": "imageinfo",
        "iiprop": "url|extmetadata|size",
        "iiurlwidth": ANCHO,
    }

    try:
        datos = consultar(parametros)
    except Exception as e:
        print(f"    error consultando '{termino}': {e}")
        return []

    paginas = datos.get("query", {}).get("pages", {})
    resultados = []

    for pagina in paginas.values():
        info = (pagina.get("imageinfo") or [{}])[0]
        url = info.get("thumburl") or info.get("url")
        if not url:
            continue

        meta = info.get("extmetadata", {})
        resultados.append({
            "titulo": pagina.get("title", ""),
            "url": url,
            "pagina": info.get("descriptionurl", ""),
            "licencia": limpiar(meta.get("LicenseShortName", {}).get("value", "")),
            "autor": limpiar(meta.get("Artist", {}).get("value", "")),
            "ancho": info.get("width", 0),
            "alto": info.get("height", 0),
        })

    return resultados


def limpiar(texto: str) -> str:
    """Quita etiquetas HTML que Commons incluye en los metadatos."""
    sin_html = re.sub(r"<[^>]+>", " ", texto)
    return re.sub(r"\s+", " ", sin_html).strip()


def descargar(url: str, destino: Path) -> bool:
    """Guarda un archivo. Devuelve False si falla o viene vacio."""
    try:
        peticion = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(peticion, timeout=60) as respuesta:
            contenido = respuesta.read()

        if len(contenido) < 5000:        # menos de 5 KB no es una foto util
            return False

        destino.write_bytes(contenido)
        return True
    except Exception:
        return False


def procesar_clase(clase: str, terminos: list, carpeta: Path, objetivo: int) -> list:
    """Descarga hasta 'objetivo' imagenes para una clase."""
    destino = carpeta / clase
    destino.mkdir(parents=True, exist_ok=True)

    print(f"\n[{clase}]  objetivo: {objetivo} imagenes")

    vistos = set()
    atribuciones = []
    contador = 0

    # Recorre los terminos en rondas, repartiendo el cupo entre todos
    # en lugar de agotarlo con el primero
    por_termino = max(objetivo // len(terminos) + 5, 10)

    for termino in terminos:
        if contador >= objetivo:
            break

        print(f"  buscando: {termino}")
        desplazamiento = 0
        obtenidos_termino = 0

        while obtenidos_termino < por_termino and contador < objetivo:
            resultados = buscar(termino, 50, desplazamiento)
            if not resultados:
                break

            for item in resultados:
                if contador >= objetivo or obtenidos_termino >= por_termino:
                    break

                if item["titulo"] in vistos:
                    continue
                vistos.add(item["titulo"])

                # Descarta imagenes demasiado pequenas en origen
                if item["ancho"] and item["ancho"] < 300:
                    continue

                nombre = f"{clase}_{contador:04d}.jpg"
                if descargar(item["url"], destino / nombre):
                    contador += 1
                    obtenidos_termino += 1
                    atribuciones.append({
                        "clase": clase,
                        "archivo": nombre,
                        "titulo_commons": item["titulo"],
                        "autor": item["autor"],
                        "licencia": item["licencia"],
                        "pagina": item["pagina"],
                    })

                time.sleep(PAUSA)

            desplazamiento += 50
            if desplazamiento > 300:      # no vale la pena ir mas profundo
                break

        print(f"    acumulado: {contador}")

    print(f"  -> {clase}: {contador} imagenes")
    return atribuciones


def main() -> int:
    analizador = argparse.ArgumentParser(
        description="Descarga imagenes con licencia libre de Wikimedia Commons."
    )
    analizador.add_argument("destino", type=Path,
                            help="Carpeta donde crear las subcarpetas por clase")
    analizador.add_argument("--por-clase", type=int, default=200,
                            help="Cuantas imagenes descargar por clase")
    analizador.add_argument("--clases", nargs="*", default=list(BUSQUEDAS.keys()),
                            help="Clases a descargar (por defecto, todas)")

    args = analizador.parse_args()

    if "cambia@tu-correo.com" in USER_AGENT:
        print("AVISO: edita la constante USER_AGENT y pon tu correo real.")
        print("       Wikimedia pide identificar a quien consulta su API.\n")

    args.destino.mkdir(parents=True, exist_ok=True)

    todas = []
    for clase in args.clases:
        if clase not in BUSQUEDAS:
            print(f"Clase desconocida: {clase}")
            continue
        todas.extend(
            procesar_clase(clase, BUSQUEDAS[clase], args.destino, args.por_clase)
        )

    # Registro de procedencia: va directo al anexo del documento
    if todas:
        csv_salida = args.destino / "atribuciones.csv"
        with csv_salida.open("w", newline="", encoding="utf-8") as f:
            escritor = csv.DictWriter(f, fieldnames=list(todas[0].keys()))
            escritor.writeheader()
            escritor.writerows(todas)

        print(f"\nTotal descargado: {len(todas)} imagenes")
        print(f"Procedencia y licencias: {csv_salida}")

    print("\nRevisa las imagenes a mano antes de entrenar:")
    print("  las busquedas por texto siempre traen algun resultado que no")
    print("  corresponde, y una sola imagen mal etiquetada confunde al modelo.\n")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
