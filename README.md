# POS Abarrotes

Sistema de Punto de Venta (POS) para tiendas de abarrotes, desarrollado en Java con Swing.

## Requisitos

- Java 17 o superior
- Maven 3.6+

## Cómo ejecutar

### Opción 1: Desde Maven
```bash
mvn package -q
java -jar target/pos-sistema-1.0-SNAPSHOT.jar
```

### Opción 2: Desde IntelliJ IDEA
1. Abrir el proyecto en IntelliJ IDEA
2. Ejecutar la clase `AbarrotesPos`

## Base de Datos

La aplicación usa **SQLite** como base de datos local. El archivo se crea automáticamente en:
```
data/posabarrotes.db
```

La estructura de tablas se inicializa automáticamente al primer arranque.

### Usuarios por defecto
| Usuario | Contraseña | Rol |
|---------|-----------|-----|
| admin   | 1234      | ADMIN |
| cajero  | 1234      | CAJERO |

### Cómo resetear la base de datos
Para empezar desde cero, simplemente elimine el archivo:
```
data/posabarrotes.db
```
La siguiente vez que ejecute la app, se recreará con los datos iniciales.

## Configuración

La ruta de la base de datos se puede cambiar en:
```
src/main/resources/app.properties
```

Clave disponible:
- `db.url` – URL JDBC de SQLite (por defecto: `jdbc:sqlite:data/posabarrotes.db`)

## Módulo de Caja

- Al iniciar sesión como **CAJERO**, si no hay caja abierta para hoy, se solicita el monto inicial.
- El botón **"Corte Caja"** cierra la caja activa, solicita el efectivo contado y genera un PDF de corte.
- Los PDFs de cortes se guardan en la carpeta `Cortes/`.

## Carpetas generadas en tiempo de ejecución

| Carpeta | Contenido |
|---------|-----------|
| `data/` | Base de datos SQLite |
| `Tickets/` | PDFs de tickets de venta |
| `Cortes/` | PDFs de cortes de caja |
