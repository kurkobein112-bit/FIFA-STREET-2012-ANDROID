# FIFA Street (2012) — Android

Recompilación estática experimental para **Android ARM64**. La lógica PowerPC del juego se convierte a C++ y se compila a ARM64 con ReXGlue. Los comandos gráficos de Xbox 360 se traducen a **Vulkan** mediante el backend Xenos.

**Versión 0.1.** El rendimiento es limitado y hay errores gráficos conocidos. No se anuncia compatibilidad completa ni 30/60 FPS.

## Descargar e iniciar

La APK y su SHA-256 se distribuyen en [Releases](https://github.com/kurkobein112-bit/FIFA-STREET-2012-ANDROID/releases).

1. Instala `FIFA-STREET-0.1.apk` en un teléfono Android ARM64 con Vulkan y Android 8.0 o posterior.
2. Selecciona tu propia ISO de **FIFA Street (2012), Xbox 360, región libre**. Edición comprobada: **Title ID `4541097B`, Media ID `78AA9072`**. La app verifica los ejecutables; una ISO de otra revisión o modificada puede ser rechazada.
3. Deja espacio para la ISO y aproximadamente **4,7 GB adicionales** para extraer los datos. La ISO original no se modifica.
4. Elige **Mali** o **Snapdragon / Adreno** y pulsa **JUGAR**.

No se distribuye la ISO ni los archivos de datos del juego. Los archivos de PS3 y los juegos FIFA Street 1/2 no son compatibles.

## Controles

- **XBOX CONTROLLER**, predeterminado: sticks proporcionales, A/B/X/Y y gatillos.
- **MINIMALIST CONTROLS**: una disposición reducida para las acciones básicas.
- **FIFA ANDROID**: un stick y botones compactos con iconos de pase, tiro, globo, pase al hueco, regate y sprint.

El botón de ajustes dentro del juego permite cambiar el estilo y ajustar **tamaño**, **transparencia**, **cruceta**, **stick derecho** y **botones avanzados** sin reiniciar el juego. Los valores se guardan. La cruceta empieza oculta.

Los mandos Bluetooth y USB se reciben mediante SDL. Al detectar un mando físico se ocultan los controles táctiles. La compatibilidad depende del mando, su conexión y el controlador Android; la comprobación visual de los controles táctiles no certifica todos los mandos físicos.

## Compatibilidad y límites

Los dos perfiles GPU comparten las optimizaciones de memoria y Vulkan. El perfil Mali permite arrancar sin exigir `vertexPipelineStoresAndAtomics`; los dibujos que necesiten esa función siguen sin tener soporte en dispositivos que no la ofrecen. El perfil Adreno la exige.

- **World Tour / Underpass 1:** puede mostrar la cancha casi negra y colores incorrectos en el público. Pendiente de corrección.
- Algunas escenas y transiciones pueden oscurecerse o presentar fallos de texturas.
- Las pruebas históricas en Galaxy A25/Mali y Redmi/Adreno usaron versiones y escenas distintas; sus FPS no son una comparación equivalente.
- No se ha validado una campaña completa ni todos los dispositivos Android.

## Compilar desde fuentes

Consulta [BUILDING.md](BUILDING.md). El repositorio incluye la interfaz Android, el extractor XDVDFS, el puente de controles SDL, la configuración de recompilación, los parches y los scripts. El código generado a partir de los ejecutables se produce **localmente desde tu propia ISO**; no está incluido en Git.

SDK fijado: ReXGlue `c94f5ebdcb3c9d1a460ca48e04f9758448f8d518`. Las versiones de las dependencias están en [licenses/dependencies.json](licenses/dependencies.json).

## Licencias y créditos

El código original del proyecto conserva la [licencia MIT](LICENSE). ReXGlue, Xenia, SDL, FFmpeg y las demás dependencias conservan sus propias licencias; consulta [licenses/README.md](licenses/README.md) y [CREDITS.md](CREDITS.md).

FIFA Street, EA SPORTS, Xbox, sus marcas y la portada pertenecen a sus titulares. La portada no está cubierta por MIT. Este proyecto es independiente y no está afiliado a EA o Microsoft. Una APK de recompilación contiene código derivado de los ejecutables originales; la licencia MIT del proyecto no convierte ese contenido en código original del proyecto.
