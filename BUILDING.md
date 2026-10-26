# Compilación Android ARM64

Los scripts actuales se han preparado para **Windows x64, PowerShell 7 y Python 3**. No instalan automáticamente el SDK Android ni la herramienta de compilación del host.

## Herramientas

El parámetro `-ToolsRoot` permite usar una instalación existente. La estructura esperada es:

```text
.tools/
  android-sdk/
    platforms/android-35/android.jar
    build-tools/35.0.0/
    ndk/28.2.13676358/
  jdk/jdk-17.0.20.1+1/
  w64devkit/bin/          # gcc, g++, cmake y ninja
```

También se necesita Git con acceso a los repositorios de ReXGlue y sus submódulos. Se fija el SDK a `c94f5ebdcb3c9d1a460ca48e04f9758448f8d518`. Los parches conservan sus avisos de autoría.

## Generar y compilar

Ejecuta desde la raíz del repositorio:

```powershell
# Extraer una copia propia compatible. La carpeta de destino debe ser nueva.
python android/InspectGame.py 'D:/Mis juegos/FIFA Street (2012).iso' --extract .android-work/GameData

# Construir runtime, backend Vulkan y generador del host fijados.
./android/BuildHostGenerator.ps1 -ToolsRoot 'D:/Herramientas' -Jobs 6

# Generar C++ desde los tres XEX y compilar las bibliotecas ARM64.
./android/BuildGame.ps1 -ToolsRoot 'D:/Herramientas' -Jobs 8

# Empaquetar, firmar y verificar la APK.
./android/Build.ps1 -ToolsRoot 'D:/Herramientas'
```

El resultado es `dist/FIFA-STREET-0.1.apk` y `dist/FIFA-STREET-0.1.apk.sha256`. Se comprueban firma y alineación APK de 16 KiB. La generación de los módulos y la primera compilación pueden ser largas; las siguientes compilaciones reutilizan los objetos.

Los tres ejecutables esperados son:

```text
GameData/default.xex
GameData/fifadllzf.xex.dll
GameData/dlc/dlc_FootballCompEng/dlc/FootballCompEng/FootballCompEngzf.xex.dll
```

`BuildGame.ps1` admite `-GameData` y `-Generator` para ubicaciones alternativas. `.android-work/`, la ISO, los ejecutables, las bibliotecas, los datos extraídos y las claves quedan fuera de Git.

## Firma

Sin parámetros adicionales se crea una clave de desarrollo local en `android/.local/debug.keystore`. No publiques ese archivo. Para usar una clave propia:

```powershell
$env:FIFA_STORE_PASSWORD = '<contraseña del almacén>'
$env:FIFA_KEY_PASSWORD = '<contraseña de la clave>'
./android/Build.ps1 -ToolsRoot 'D:/Herramientas' -KeyStore 'D:/Claves/fifa.jks' -KeyAlias 'fifa'
```

La clave usada para una APK debe conservarse para poder actualizarla con la misma identidad. `-Diagnostic` permite generar una APK con `run-as` para pruebas; no activa esa propiedad en la APK normal.

## Pruebas del extractor

```powershell
$env:JAVA_HOME = 'D:/Herramientas/jdk/jdk-17.0.20.1+1'
python -m unittest discover -s android -p 'test_*.py' -v

# Opcional: comprobar también una ISO real sin extraer sus datos.
$env:FIFA_TEST_ISO = 'D:/Mis juegos/FIFA Street (2012).iso'
python -m unittest discover -s android -p test_public_iso.py -v
```

Las pruebas comprueban el lector real, extracción, archivos truncados, rutas inseguras, ciclos, duplicados, cancelación y rechazo de sobrescritura. Las pruebas del host no sustituyen la comprobación del selector de archivos, controles, mando físico y gráficos en Android.
