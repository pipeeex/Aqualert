#!/usr/bin/env python3
"""
preparar_dataset.py — Aqualert

Toma las carpetas de imagenes que hayas descargado de los datasets
publicos y arma el dataset listo para Teachable Machine:

  - Balancea: toma como maximo N imagenes por clase
  - Normaliza: convierte todo a JPG de 224x224 (lo que espera el modelo)
  - Divide: separa 80 % para entrenar y 20 % para medir
  - Descarta: imagenes corruptas, muy pequenas o duplicadas

USO
---
1. Crea una carpeta "crudo" y mete dentro una subcarpeta por clase,
   con las imagenes que hayas reunido de cualquier fuente:

   crudo/
     basuras/            (de Kaggle, Roboflow, Commons...)
     vertimiento/
     aguas_residuales/
     espumas/
     escombros/
     canal_limpio/
     no_es_agua/         (fotos de tu celular)

   No importa el formato, el tamano ni como se llamen los archivos.

2. Instala la unica dependencia:
     pip install Pillow

3. Ejecuta:
     python preparar_dataset.py crudo dataset --por-clase 200

4. Sube a Teachable Machine las carpetas de dataset/entrenamiento/
   GUARDA dataset/prueba/ sin tocar: es con lo que mides el modelo.
"""

import argparse
import hashlib
import shutil
import sys
from pathlib import Path
from random import Random

try:
    from PIL import Image, ImageOps
except ImportError:
    sys.exit("Falta Pillow. Instalalo con:  pip install Pillow")


# El modelo de Teachable Machine espera entradas de 224x224
LADO = 224

# Por debajo de esto la imagen no aporta detalle util
LADO_MINIMO = 100

# Proporcion que se aparta para medir el modelo
PROPORCION_PRUEBA = 0.20

EXTENSIONES = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".gif", ".tif", ".tiff"}


def firma(ruta: Path) -> str:
    """Huella del contenido, para detectar duplicados exactos."""
    return hashlib.md5(ruta.read_bytes()).hexdigest()


def normalizar(origen: Path, destino: Path) -> bool:
    """
    Convierte la imagen a JPG cuadrado de 224x224.

    Recorta al centro en vez de deformar: un rio estirado deja de
    parecerse a un rio y el modelo aprende la deformacion.
    Devuelve False si el archivo no es una imagen utilizable.
    """
    try:
        with Image.open(origen) as img:
            img = ImageOps.exif_transpose(img)   # respeta la rotacion del celular

            if min(img.size) < LADO_MINIMO:
                return False

            img = img.convert("RGB")
            img = ImageOps.fit(img, (LADO, LADO), Image.Resampling.LANCZOS)
            img.save(destino, "JPEG", quality=90)
            return True
    except Exception:
        return False


def procesar_clase(
    carpeta: Path,
    salida: Path,
    por_clase: int,
    azar: Random,
) -> dict:
    """Normaliza, baraja y reparte las imagenes de una clase."""
    nombre = carpeta.name

    # Recorre tambien subcarpetas: los datasets suelen venir anidados
    archivos = [
        p for p in carpeta.rglob("*")
        if p.is_file() and p.suffix.lower() in EXTENSIONES
    ]

    if not archivos:
        return {"clase": nombre, "encontradas": 0, "usadas": 0,
                "entrenamiento": 0, "prueba": 0, "descartadas": 0,
                "duplicadas": 0}

    azar.shuffle(archivos)

    carpeta_entrenamiento = salida / "entrenamiento" / nombre
    carpeta_prueba = salida / "prueba" / nombre
    carpeta_entrenamiento.mkdir(parents=True, exist_ok=True)
    carpeta_prueba.mkdir(parents=True, exist_ok=True)

    vistas = set()
    validas = []
    descartadas = 0
    duplicadas = 0

    temporal = salida / ".tmp" / nombre
    temporal.mkdir(parents=True, exist_ok=True)

    for archivo in archivos:
        if len(validas) >= por_clase:
            break

        try:
            huella = firma(archivo)
        except Exception:
            descartadas += 1
            continue

        if huella in vistas:
            duplicadas += 1
            continue
        vistas.add(huella)

        destino = temporal / f"{nombre}_{len(validas):04d}.jpg"
        if normalizar(archivo, destino):
            validas.append(destino)
        else:
            descartadas += 1

    # Reparte: el 20 % final va a prueba
    corte = int(len(validas) * (1 - PROPORCION_PRUEBA))
    for i, imagen in enumerate(validas):
        objetivo = carpeta_entrenamiento if i < corte else carpeta_prueba
        shutil.move(str(imagen), str(objetivo / imagen.name))

    shutil.rmtree(temporal, ignore_errors=True)

    return {
        "clase": nombre,
        "encontradas": len(archivos),
        "usadas": len(validas),
        "entrenamiento": corte,
        "prueba": len(validas) - corte,
        "descartadas": descartadas,
        "duplicadas": duplicadas,
    }


def main() -> int:
    analizador = argparse.ArgumentParser(
        description="Prepara el dataset de Aqualert para Teachable Machine."
    )
    analizador.add_argument("origen", type=Path,
                            help="Carpeta con una subcarpeta por clase")
    analizador.add_argument("destino", type=Path,
                            help="Carpeta de salida del dataset")
    analizador.add_argument("--por-clase", type=int, default=200,
                            help="Maximo de imagenes por clase (por defecto 200)")
    analizador.add_argument("--semilla", type=int, default=42,
                            help="Semilla aleatoria, para resultados repetibles")

    args = analizador.parse_args()

    if not args.origen.is_dir():
        print(f"No existe la carpeta de origen: {args.origen}")
        return 1

    clases = sorted(p for p in args.origen.iterdir() if p.is_dir())
    if not clases:
        print(f"No hay subcarpetas de clase dentro de {args.origen}")
        return 1

    if args.destino.exists():
        print(f"La carpeta {args.destino} ya existe. Borrala o usa otro nombre.")
        return 1

    print(f"\nPreparando dataset: {len(clases)} clases, "
          f"maximo {args.por_clase} imagenes por clase\n")

    azar = Random(args.semilla)
    resultados = [procesar_clase(c, args.destino, args.por_clase, azar)
                  for c in clases]

    shutil.rmtree(args.destino / ".tmp", ignore_errors=True)

    # ---- Informe ----
    print(f"{'CLASE':<22}{'HALLADAS':>10}{'USADAS':>9}"
          f"{'ENTREN.':>9}{'PRUEBA':>8}{'DESCART.':>10}{'DUPL.':>8}")
    print("-" * 76)

    for r in resultados:
        print(f"{r['clase']:<22}{r['encontradas']:>10}{r['usadas']:>9}"
              f"{r['entrenamiento']:>9}{r['prueba']:>8}"
              f"{r['descartadas']:>10}{r['duplicadas']:>8}")

    total = sum(r["usadas"] for r in resultados)
    print("-" * 76)
    print(f"{'TOTAL':<22}{'':>10}{total:>9}\n")

    # ---- Avisos sobre el balance ----
    usadas = [r["usadas"] for r in resultados if r["usadas"] > 0]
    if usadas:
        menor, mayor = min(usadas), max(usadas)

        for r in resultados:
            if r["usadas"] == 0:
                print(f"  AVISO  La clase '{r['clase']}' quedo vacia.")
            elif r["usadas"] < 100:
                print(f"  AVISO  '{r['clase']}' tiene solo {r['usadas']} imagenes. "
                      f"Por debajo de 100 el modelo aprende poco.")

        if mayor > menor * 2:
            print(f"\n  DESBALANCE  La clase mas grande tiene {mayor} imagenes "
                  f"y la mas pequena {menor}.")
            print("  Un modelo entrenado asi tiende a responder la clase "
                  "mayoritaria cuando duda.")
            print(f"  Sugerencia: vuelve a ejecutar con --por-clase {menor}")

    print(f"\nListo. Sube a Teachable Machine las carpetas de:")
    print(f"   {args.destino / 'entrenamiento'}")
    print(f"\nNO uses para entrenar (son para medir el modelo despues):")
    print(f"   {args.destino / 'prueba'}\n")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
