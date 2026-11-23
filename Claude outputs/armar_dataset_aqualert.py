#!/usr/bin/env python3
"""
armar_dataset_aqualert.py

Hace todo de una pasada:
  1. Descarga imagenes con licencia libre desde Wikimedia Commons
  2. Las normaliza a 224x224 JPG
  3. Las divide en entrenamiento (80 %) y prueba (20 %)
  4. Guarda el CSV de atribuciones para el anexo del documento

Cubre seis de las siete clases del modelo. La septima, "no_es_agua",
la sacas de la galeria de tu celular en cinco minutos: son fotos
cotidianas (calles, comida, personas, capturas) que representan lo
que un usuario subiria por error.

USO
---
    pip install Pillow
    python armar_dataset_aqualert.py --por-clase 200

Deja listo:
    dataset/entrenamiento/   -> subir a Teachable Machine
    dataset/prueba/          -> NO tocar, es para medir el modelo
    dataset/atribuciones.csv -> fuentes y licencias

Para anadir "no_es_agua" despues:
    1. Copia tus fotos a  crudo/no_es_agua/
    2. python armar_dataset_aqualert.py --solo-preparar --clases no_es_agua
"""

import argparse
import csv
import hashlib
import json
import re
import shutil
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path
from random import Random

try:
    from PIL import Image, ImageOps
except ImportError:
    sys.exit("Falta Pillow. Instalalo con:  pip install Pillow")


API = "https://commons.wikimedia.org/w/api.php"

# Wikimedia pide identificar a quien consulta su API.
# CAMBIA EL CORREO por el tuyo antes de ejecutar.
USER_AGENT = (
    "AqualertDatasetBuilder/1.0 "
    "(proyecto academico Universidad Manuela Beltran; contacto: cambia@tu-correo.com)"
)

LADO = 224              # entrada que espera el modelo
LADO_MINIMO = 100       # por debajo de esto la imagen no aporta detalle
ANCHO_DESCARGA = 800    # suficiente para entrenar y rapido de bajar
PAUSA = 0.35            # cortesia con el servidor
PROPORCION_PRUEBA = 0.20

EXTENSIONES = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".gif", ".tif", ".tiff"}

# Terminos en ingles: Commons tiene mucho mas material etiquetado asi
BUSQUEDAS = {
    "basuras": [
        "plastic waste river",
        "garbage floating water",
        "litter river bank",
        "plastic pollution river",
        "trash water canal",
        "waste dumped river",
        "basura rio contaminacion",
        "rubbish water surface",
    ],
    "aguas_residuales": [
        "sewage discharge river",
        "wastewater outfall",
        "sewage outfall pipe",
        "open sewer canal",
        "polluted river water sewage",
        "contaminated river brown water",
        "aguas residuales rio",
        "sewage water pollution",
    ],
    "espumas": [
        "river foam pollution",
        "detergent foam water",
        "foam river surface",
        "foaming river pollution",
        "water pollution foam white",
        "espuma rio contaminacion",
        "sewage foam water",
    ],
    "escombros": [
        "construction debris river",
        "rubble dumped river",
        "debris water channel",
        "construction waste water",
        "building rubble canal",
        "escombros rio",
        "demolition waste dumped",
    ],
    "vertimiento": [
        "industrial discharge river",
        "factory effluent water",
        "industrial water pollution discharge",
        "chemical discharge river",
        "mine drainage water",
        "colored water pollution industrial",
        "vertimiento industrial agua",
    ],
    "canal_limpio": [
        "urban canal water",
        "city drainage canal",
        "storm water channel",
        "concrete canal water",
        "urban river channel",
        "canal urbano agua",
        "waterway city clean",
    ],
}


# ==========================================================
#  Descarga desde Commons
# ==========================================================

def consultar(parametros: dict) -> dict:
    url = f"{API}?{urllib.parse.urlencode(parametros)}"
    peticion = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(peticion, timeout=30) as respuesta:
        return json.loads(respuesta.read().decode("utf-8"))


def limpiar(texto: str) -> str:
    """Commons devuelve los metadatos con etiquetas HTML dentro."""
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", texto)).strip()


def buscar(termino: str, desplazamiento: int = 0) -> list:
    parametros = {
        "action": "query",
        "format": "json",
        "generator": "search",
        "gsrsearch": f"filetype:bitmap {termino}",
        "gsrnamespace": 6,          # espacio de nombres "Archivo"
        "gsrlimit": 50,
        "gsroffset": desplazamiento,
        "prop": "imageinfo",
        "iiprop": "url|extmetadata|size",
        "iiurlwidth": ANCHO_DESCARGA,
    }

    try:
        datos = consultar(parametros)
    except Exception as e:
        print(f"      error: {e}")
        return []

    salida = []
    for pagina in datos.get("query", {}).get("pages", {}).values():
        info = (pagina.get("imageinfo") or [{}])[0]
        url = info.get("thumburl") or info.get("url")
        if not url:
            continue

        meta = info.get("extmetadata", {})
        salida.append({
            "titulo": pagina.get("title", ""),
            "url": url,
            "pagina": info.get("descriptionurl", ""),
            "licencia": limpiar(meta.get("LicenseShortName", {}).get("value", "")),
            "autor": limpiar(meta.get("Artist", {}).get("value", "")),
            "ancho": info.get("width", 0),
        })
    return salida


def bajar(url: str, destino: Path) -> bool:
    try:
        peticion = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(peticion, timeout=60) as respuesta:
            contenido = respuesta.read()
        if len(contenido) < 5000:
            return False
        destino.write_bytes(contenido)
        return True
    except Exception:
        return False


def descargar_clase(clase: str, carpeta: Path, objetivo: int) -> list:
    terminos = BUSQUEDAS[clase]
    destino = carpeta / clase
    destino.mkdir(parents=True, exist_ok=True)

    print(f"\n[{clase}]  objetivo {objetivo}")

    vistos, atribuciones, contador = set(), [], 0
    # Reparte el cupo entre los terminos en vez de agotarlo con el primero
    por_termino = max(objetivo // len(terminos) + 5, 10)

    for termino in terminos:
        if contador >= objetivo:
            break
        print(f"   {termino}")

        desplazamiento, del_termino = 0, 0
        while del_termino < por_termino and contador < objetivo:
            resultados = buscar(termino, desplazamiento)
            if not resultados:
                break

            for item in resultados:
                if contador >= objetivo or del_termino >= por_termino:
                    break
                if item["titulo"] in vistos:
                    continue
                vistos.add(item["titulo"])
                if item["ancho"] and item["ancho"] < 300:
                    continue

                nombre = f"{clase}_{contador:04d}.jpg"
                if bajar(item["url"], destino / nombre):
                    contador += 1
                    del_termino += 1
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
            if desplazamiento > 300:
                break

    print(f"   -> {contador} descargadas")
    return atribuciones


# ==========================================================
#  Normalizacion y division
# ==========================================================

def normalizar(origen: Path, destino: Path) -> bool:
    """
    Convierte a JPG de 224x224 recortando al centro.
    No deforma: un rio estirado deja de parecerse a un rio y el
    modelo acabaria aprendiendo la deformacion.
    """
    try:
        with Image.open(origen) as img:
            img = ImageOps.exif_transpose(img)
            if min(img.size) < LADO_MINIMO:
                return False
            img = img.convert("RGB")
            img = ImageOps.fit(img, (LADO, LADO), Image.Resampling.LANCZOS)
            img.save(destino, "JPEG", quality=90)
            return True
    except Exception:
        return False


def preparar_clase(carpeta: Path, salida: Path, por_clase: int, azar: Random) -> dict:
    clase = carpeta.name
    archivos = [p for p in carpeta.rglob("*")
                if p.is_file() and p.suffix.lower() in EXTENSIONES]

    base = {"clase": clase, "halladas": len(archivos), "usadas": 0,
            "entrenamiento": 0, "prueba": 0, "descartadas": 0, "duplicadas": 0}
    if not archivos:
        return base

    azar.shuffle(archivos)

    dir_entrenamiento = salida / "entrenamiento" / clase
    dir_prueba = salida / "prueba" / clase
    dir_entrenamiento.mkdir(parents=True, exist_ok=True)
    dir_prueba.mkdir(parents=True, exist_ok=True)

    temporal = salida / ".tmp" / clase
    temporal.mkdir(parents=True, exist_ok=True)

    vistas, validas = set(), []
    for archivo in archivos:
        if len(validas) >= por_clase:
            break
        try:
            huella = hashlib.md5(archivo.read_bytes()).hexdigest()
        except Exception:
            base["descartadas"] += 1
            continue
        if huella in vistas:
            base["duplicadas"] += 1
            continue
        vistas.add(huella)

        destino = temporal / f"{clase}_{len(validas):04d}.jpg"
        if normalizar(archivo, destino):
            validas.append(destino)
        else:
            base["descartadas"] += 1

    corte = int(len(validas) * (1 - PROPORCION_PRUEBA))
    for i, imagen in enumerate(validas):
        objetivo = dir_entrenamiento if i < corte else dir_prueba
        shutil.move(str(imagen), str(objetivo / imagen.name))

    shutil.rmtree(temporal, ignore_errors=True)

    base.update(usadas=len(validas), entrenamiento=corte,
                prueba=len(validas) - corte)
    return base


# ==========================================================

def main() -> int:
    ap = argparse.ArgumentParser(
        description="Arma el dataset de Aqualert desde Wikimedia Commons.")
    ap.add_argument("--por-clase", type=int, default=200)
    ap.add_argument("--crudo", type=Path, default=Path("crudo"))
    ap.add_argument("--dataset", type=Path, default=Path("dataset"))
    ap.add_argument("--clases", nargs="*", default=list(BUSQUEDAS.keys()))
    ap.add_argument("--solo-preparar", action="store_true",
                    help="Omite la descarga y solo procesa lo que ya hay en crudo/")
    args = ap.parse_args()

    if not args.solo_preparar and "cambia@tu-correo.com" in USER_AGENT:
        print("AVISO: edita USER_AGENT en el script y pon tu correo real.")
        print("       Wikimedia pide identificar a quien consulta su API.\n")

    # --- 1. Descarga ---
    atribuciones = []
    if not args.solo_preparar:
        args.crudo.mkdir(parents=True, exist_ok=True)
        print("=" * 60)
        print("  DESCARGA DESDE WIKIMEDIA COMMONS")
        print("=" * 60)
        for clase in args.clases:
            if clase in BUSQUEDAS:
                atribuciones.extend(
                    descargar_clase(clase, args.crudo, args.por_clase))

    # --- 2. Preparacion ---
    print("\n" + "=" * 60)
    print("  NORMALIZACION Y DIVISION")
    print("=" * 60)

    if args.dataset.exists():
        shutil.rmtree(args.dataset)

    carpetas = sorted(p for p in args.crudo.iterdir() if p.is_dir())
    if not carpetas:
        print(f"No hay subcarpetas en {args.crudo}")
        return 1

    azar = Random(42)
    resultados = [preparar_clase(c, args.dataset, args.por_clase, azar)
                  for c in carpetas]
    shutil.rmtree(args.dataset / ".tmp", ignore_errors=True)

    print(f"\n{'CLASE':<20}{'HALLADAS':>10}{'USADAS':>9}"
          f"{'ENTREN.':>9}{'PRUEBA':>8}{'DESCART.':>10}")
    print("-" * 66)
    for r in resultados:
        print(f"{r['clase']:<20}{r['halladas']:>10}{r['usadas']:>9}"
              f"{r['entrenamiento']:>9}{r['prueba']:>8}{r['descartadas']:>10}")
    print("-" * 66)
    print(f"{'TOTAL':<20}{'':>10}{sum(r['usadas'] for r in resultados):>9}\n")

    # --- 3. Atribuciones ---
    if atribuciones:
        csv_salida = args.dataset / "atribuciones.csv"
        csv_salida.parent.mkdir(parents=True, exist_ok=True)
        with csv_salida.open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=list(atribuciones[0].keys()))
            w.writeheader()
            w.writerows(atribuciones)
        print(f"Fuentes y licencias: {csv_salida}")

    # --- 4. Avisos ---
    usadas = [r["usadas"] for r in resultados if r["usadas"] > 0]
    if usadas and max(usadas) > min(usadas) * 2:
        print(f"\nDESBALANCE: la clase mayor tiene {max(usadas)} imagenes "
              f"y la menor {min(usadas)}.")
        print(f"Un modelo asi responde la clase mayoritaria cuando duda.")
        print(f"Sugerencia: repite con --solo-preparar --por-clase {min(usadas)}")

    print("\nANTES DE ENTRENAR: revisa las carpetas y borra lo que no")
    print("corresponda. Una imagen mal etiquetada hace mas dano que")
    print("veinte faltantes.\n")
    print(f"Sube a Teachable Machine: {args.dataset / 'entrenamiento'}")
    print(f"Guarda sin tocar:          {args.dataset / 'prueba'}\n")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
